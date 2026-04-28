"""
FCM Bridge - holt Firebase FCM-Tokens fuer VW Group MQTT-Auth.

VW's MQTT-Broker benoetigt ein TOTP das aus einem FCM-Token abgeleitet wird.
Die firebase-messaging Library implementiert das Android Checkin Protocol -
das koennen wir nicht sauber in Java nachbauen.

Endpoint: GET /fcm-token/{brand}
Response: { "token": "..." }

Token wird gecacht bis er explizit invalidiert wird.
"""

import hashlib
import hmac
import struct
import time

from firebase_messaging import FcmPushClient, FcmRegisterConfig
from fastapi import FastAPI, HTTPException
import asyncio

app = FastAPI()

# Firebase-Konfigurationen pro Marke.
# Credentials aus myskoda Open-Source-Projekt (nicht geheim - im App-Binary eingebettet).
BRAND_CONFIGS = {
    "skoda": FcmRegisterConfig(
        api_key="AIzaSyBlJdDfVR6ltRhKpA87F3SmCe2hHqhyEd8",
        project_id="678067506455",
        app_id="1:678067506455:android:4afca86c91d6d4c235bb52",
        sender_id="678067506455",
    ),
}

token_cache: dict[str, str] = {}


@app.get("/fcm-token/{brand}")
async def get_fcm_token(brand: str):
    if brand not in BRAND_CONFIGS:
        raise HTTPException(status_code=404, detail=f"Unknown brand: {brand}")

    if brand not in token_cache:
        token_cache[brand] = await _register(brand)

    return {"token": token_cache[brand]}


@app.delete("/fcm-token/{brand}")
async def invalidate_token(brand: str):
    token_cache.pop(brand, None)
    return {"ok": True}


@app.get("/health")
def health():
    return {"ok": True}


async def _register(brand: str) -> str:
    config = BRAND_CONFIGS[brand]

    async def noop(*args, **kwargs):
        pass

    client = FcmPushClient(
        callback=noop,
        fcm_config=config,
        credentials_updated_callback=noop,
    )
    return await client.checkin_or_register()
