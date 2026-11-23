import random
from datetime import datetime, timedelta
import requests

# URL where your FastAPI server is currently running
API_URL = "http://127.0.0.1:8000/readings/"

# Defined indoor spots and baseline performance characteristics
SPOTS = [
    {"name": "Desk", "lat": 22.5726, "lon": 88.3639, "base_speed": 30.0},
    {"name": "Window", "lat": 22.5728, "lon": 88.3641, "base_speed": 85.0},
    {"name": "Bed", "lat": 22.5725, "lon": 88.3638, "base_speed": 45.0},
]


def generate_mock_readings():
    """Generates 48 hours of simulated network speed test readings."""
    print("Sending mock network data to FastAPI backend...")
    now = datetime.utcnow()
    total_inserted = 0

    # Generate test readings for the past 2 days (48 hours)
    for hour_offset in range(48, 0, -1):
        timestamp = now - timedelta(hours=hour_offset)
        hour_of_day = timestamp.hour

        # Nighttime speed boost (network congestion decreases late at night)
        time_multiplier = 1.4 if (hour_of_day >= 22 or hour_of_day <= 5) else 0.95

        for spot in SPOTS:
            # Add random variation (+/- 10 Mbps) to simulate natural fluctuations
            speed_variation = random.uniform(-10.0, 10.0)
            download_speed = round(
                max(5.0, (spot["base_speed"] * time_multiplier) + speed_variation), 1
            )
            upload_speed = round(download_speed * 0.25, 1)
            signal_strength = -70 if spot["name"] == "Window" else -88
            latency = round(random.uniform(18.0, 35.0), 1)

            payload = {
                "network_type": "5G",
                "download_speed": download_speed,
                "upload_speed": upload_speed,
                "signal_strength": signal_strength,
                "latency": latency,
                "latitude": spot["lat"],
                "longitude": spot["lon"],
                "indoor_spot": spot["name"],
            }

            try:
                response = requests.post(API_URL, json=payload)
                if response.status_code == 200:
                    total_inserted += 1
            except Exception as e:
                print(
                    f"Error connecting to server: {e}. Is 'uvicorn main:app --reload' running?"
                )
                return

    print(
        f"Success! Inserted {total_inserted} mock network readings into SQLite database."
    )


if __name__ == "__main__":
    generate_mock_readings()