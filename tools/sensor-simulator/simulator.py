import requests
import time
import json
import random
from datetime import datetime

def run_simulator():
    with open('config.json', 'r') as f:
        config = json.load(f)

    print(f"Starting sensor simulator for batch {config['batchId']}")
    print(f"Endpoint: {config['endpointUrl']}")
    print(f"Interval: {config['intervalSeconds']}s")

    headers = {
        'Content-Type': 'application/json',
        'Authorization': f"Bearer {config['token']}"
    }

    while True:
        value = round(random.uniform(config['minValue'], config['maxValue']), 2)
        
        payload = {
            "batchId": config['batchId'],
            "materialName": config['materialName'],
            "quantity": value,
            "unit": config['unit'],
            "sensorType": config['sensorType'],
            "timestamp": datetime.utcnow().isoformat() + "Z"
        }

        try:
            response = requests.post(config['endpointUrl'], json=payload, headers=headers)
            print(f"[{datetime.now()}] Sent {value} {config['unit']} -> Status: {response.status_code}")
            if response.status_code != 201:
                print(f"Error: {response.text}")
        except Exception as e:
            print(f"Failed to connect: {e}")

        time.sleep(config['intervalSeconds'])

if __name__ == "__main__":
    run_simulator()
