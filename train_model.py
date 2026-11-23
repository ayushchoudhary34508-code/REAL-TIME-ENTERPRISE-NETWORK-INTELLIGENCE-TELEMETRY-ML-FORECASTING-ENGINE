import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error
from database import SessionLocal
from predict import get_network_dataframe

def train_speed_prediction_model():
    """Trains and evaluates a Random Forest Regressor on historical network readings."""
    db = SessionLocal()
    try:
        df = get_network_dataframe(db)

        if df.empty or len(df) < 20:
            print("Not enough data to train machine learning model (minimum 20 records required).")
            return

        # Prepare features (X) and target variable (y)
        # Convert categorical 'indoor_spot' into numeric binary columns
        X = pd.get_dummies(df[["hour", "signal_strength", "indoor_spot"]], drop_first=True)
        y = df["download_speed"]

        # Split data into 80% Training and 20% Testing sets
        X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

        # 1. Baseline Model (Simple Mean Prediction)
        baseline_prediction = np.full_like(y_test, fill_value=y_train.mean())
        baseline_mae = mean_absolute_error(y_test, baseline_prediction)

        # 2. Random Forest Regressor Model
        model = RandomForestRegressor(n_estimators=100, random_state=42)
        model.fit(X_train, y_train)

        model_predictions = model.predict(X_test)
        model_mae = mean_absolute_error(y_test, model_predictions)

        print("--- Machine Learning Model Evaluation ---")
        print(f"Total Dataset Size: {len(df)} samples")
        print(f"Baseline (Mean) MAE: {baseline_mae:.2f} Mbps error")
        print(f"Random Forest MAE:  {model_mae:.2f} Mbps error")

        if model_mae < baseline_mae:
            improvement = ((baseline_mae - model_mae) / baseline_mae) * 100
            print(f"Result: Machine Learning model improved speed prediction accuracy by {improvement:.1f}%!")
        else:
            print("Result: ML model did not outperform baseline. Keep collecting more real-world measurements.")

    finally:
        db.close()

if __name__ == "__main__":
    train_speed_prediction_model()