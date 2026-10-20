# Sensor Simulator

This script simulates an IoT scale sending weight data to the WastePilot backend.

## Usage

1. Create a running batch in the WastePilot app.
2. Set `SENSOR_INGEST_API_KEY` to the shared sensor-ingestion key configured for the backend. Do not commit or put this key in `config.json`.
3. Update `config.json` with the batch ID and sensor details.
4. Run the script:

```bash
pip install requests
export SENSOR_INGEST_API_KEY="your-configured-sensor-key"
python simulator.py
```

In PowerShell, set the key for the current terminal before running the script:

```powershell
$env:SENSOR_INGEST_API_KEY = "your-configured-sensor-key"
python simulator.py
```

The simulator authenticates using the `X-Sensor-API-Key` header, not a user JWT. This prototype uses one deployment-level shared key, so it authenticates a trusted sender but does not identify or revoke individual sensors.
