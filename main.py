import os
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Depends
from pydantic import BaseModel, Field
import asyncpg
import h3
import numpy as np
from sklearn.gaussian_process import GaussianProcessRegressor
from sklearn.gaussian_process.kernels import RBF, ConstantKernel as C

# --- PostgreSQL Database Configuration ---
DB_CONFIG = {
    "user": os.getenv("DB_USER"),
    "password": os.getenv("DB_PASSWORD"),
    "database": "network_intelligence",
    "host": "127.0.0.1",
    "port": 5432
}

pool: asyncpg.Pool = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global pool
    if not DB_CONFIG["user"] or not DB_CONFIG["password"]:
        raise RuntimeError("DB_USER and DB_PASSWORD environment variables must be set")
    pool = await asyncpg.create_pool(**DB_CONFIG)
    yield
    await pool.close()

app = FastAPI(
    title="Network Intelligence Engine",
    version="1.0.0",
    lifespan=lifespan
)

async def get_db():
    async with pool.acquire() as connection:
        yield connection

# --- Data Schemas ---

class TelemetryPayload(BaseModel):
    device_id: str
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    network_type: str
    pci: int
    rsrp: int
    rsrq: int
    sinr: int
    channel_freq: int

class OptimizationQuery(BaseModel):
    latitude: float
    longitude: float
    radius_meters: float = 100.0

# --- Helper for H3 Compatibility ---

def get_h3_res10(lat: float, lon: float) -> str:
    """Extracts H3 Resolution 10 index supporting all h3-py library versions."""
    if hasattr(h3, 'latlng_to_cell'):  # h3-py v4.x
        return h3.latlng_to_cell(lat, lon, 10)
    elif hasattr(h3, 'geo_to_h3'):     # h3-py v3.x
        return h3.geo_to_h3(lat, lon, 10)
    elif hasattr(h3, 'latlng_to_h3'):
        return h3.latlng_to_h3(lat, lon, 10)
    else:
        raise RuntimeError("Incompatible H3 library version.")

# --- API Endpoints ---

@app.get("/")
async def root():
    return {"status": "online", "engine": "Network Intelligence Spatio-Temporal API"}

@app.post("/api/v1/telemetry/ingest", status_code=201)
async def ingest_telemetry(payload: TelemetryPayload, db=Depends(get_db)):
    """Receives live cellular telemetry, generates H3 Res 10 index, and stores PostGIS geometry."""
    h3_index = get_h3_res10(payload.latitude, payload.longitude)

    query = """
        INSERT INTO cell_telemetry (
            device_id, network_type, pci, rsrp, rsrq, sinr, channel_freq, h3_res10, location
        ) VALUES (
            $1, $2, $3, $4, $5, $6, $7, $8,
            ST_SetSRID(ST_MakePoint($9, $10), 4326)
        );
    """
    await db.execute(
        query,
        payload.device_id, payload.network_type, payload.pci,
        payload.rsrp, payload.rsrq, payload.sinr, payload.channel_freq,
        h3_index, payload.longitude, payload.latitude
    )
    return {"status": "success", "h3_index": h3_index}

@app.post("/api/v1/network/predict-optimal-spot")
async def predict_optimal_spot(query: OptimizationQuery, db=Depends(get_db)):
    """
    Queries spatial data within radius_meters using ST_DWithin and performs
    Spatial Kriging (Gaussian Process Regression) to locate optimal peak signal coordinates.
    """
    sql = """
        SELECT
            ST_Y(location::geometry) as lat,
            ST_X(location::geometry) as lon,
            rsrp, sinr
        FROM cell_telemetry
        WHERE ST_DWithin(
            location,
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
            $3
        )
        ORDER BY recorded_at DESC
        LIMIT 200;
    """
    rows = await db.fetch(sql, query.longitude, query.latitude, query.radius_meters)

    if len(rows) < 5:
        raise HTTPException(
            status_code=400,
            detail=f"Insufficient spatial telemetry points in radius (need >= 5, got {len(rows)})."
        )

    coords = np.array([[r["lat"], r["lon"]] for r in rows])
    rsrp_vals = np.array([r["rsrp"]] for r in rows)

    # Spatial Kriging Model (Gaussian Process Regression)
    kernel = C(1.0, (1e-3, 1e3)) * RBF(length_scale=0.001, length_scale_bounds=(1e-5, 1e-1))
    gp = GaussianProcessRegressor(kernel=kernel, n_restarts_optimizer=5, random_state=42)
    gp.fit(coords, rsrp_vals)

    # Grid search for peak predicted signal within radius
    lat_delta = query.radius_meters / 111111.0
    lon_delta = query.radius_meters / (111111.0 * np.cos(np.radians(query.latitude)))

    grid_lats = np.linspace(query.latitude - lat_delta, query.latitude + lat_delta, 15)
    grid_lons = np.linspace(query.longitude - lon_delta, query.longitude + lon_delta, 15)
    grid_points = np.array([[lat, lon] for lat in grid_lats for lon in grid_lons])

    predicted_rsrp, sigma = gp.predict(grid_points, return_std=True)

    best_idx = int(np.argmax(predicted_rsrp))
    best_spot = grid_points[best_idx]

    return {
        "user_location": {"latitude": query.latitude, "longitude": query.longitude},
        "optimal_spot": {
            "latitude": float(best_spot[0]),
            "longitude": float(best_spot[1]),
            "predicted_rsrp_dbm": float(predicted_rsrp[best_idx]),
            "confidence_uncertainty": float(sigma[best_idx])
        },
        "sampled_points_count": len(rows)
    }