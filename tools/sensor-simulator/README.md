# Sensor Simulator

This script simulates an IoT scale sending weight data to the WastePilot backend.

## Usage

1. Create a running batch in the WastePilot app.
2. Get your access token from the app's local storage (or via `/api/v1/auth/login`).
3. Update `config.json` with your token and the batch ID.
4. Run the script:

```bash
pip install requests
python simulator.py
```
