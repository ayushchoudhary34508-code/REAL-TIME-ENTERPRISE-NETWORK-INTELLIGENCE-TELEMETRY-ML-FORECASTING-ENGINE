from datetime import datetime
from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime
from sqlalchemy.orm import declarative_base, sessionmaker

DATABASE_URL = "sqlite:///./network_intelligence.db"

engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class NetworkReading(Base):
    __tablename__ = "network_readings"

    id = Column(Integer, primary_key=True, index=True)
    timestamp = Column(DateTime, default=datetime.utcnow)
    network_type = Column(String)
    download_speed = Column(Float)
    upload_speed = Column(Float, nullable=True)
    signal_strength = Column(Integer, nullable=True)
    latency = Column(Float, nullable=True)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    indoor_spot = Column(String, nullable=True)

def init_db():
    Base.metadata.create_all(bind=engine)