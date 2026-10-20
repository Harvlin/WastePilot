import json
import os
import random
import time
from datetime import datetime, timezone

import requests


def run_simulator():
    with open("config.json", "r", encoding="utf-8") as config_file:
        config = json.load(config_file)

    sensor_api_key = os.environ.get("SENSOR_INGEST_API_KEY")
    if not sensor_api_key:
        raise RuntimeError("Set SENSOR_INGEST_API_KEY before running the simulator.")

    print(f"Starting sensor simulator for batch {config['batchId']}")
    print(f"Endpoint: {config['endpointUrl']}")
    print(f"Interval: {config['intervalSeconds']}s")

    headers = {
        "Content-Type": "application/json",
        "X-Sensor-API-Key": sensor_api_key,
    }

    while True:
        value = round(random.uniform(config["minValue"], config["maxValue"]), 2)

        payload = {
            "batchId": config["batchId"],
            "materialName": config["materialName"],
            "quantity": value,
            "unit": config["unit"],
            "sensorType": config["sensorType"],
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }

        try:
            response = requests.post(config["endpointUrl"], json=payload, headers=headers)
            print(f"[{datetime.now()}] Sent {value} {config['unit']} -> Status: {response.status_code}")
            if response.status_code != 201:
                print(f"Error: {response.text}")
        except requests.RequestException as error:
            print(f"Failed to connect: {error}")

        time.sleep(config["intervalSeconds"])


if __name__ == "__main__":
    run_simulator()
