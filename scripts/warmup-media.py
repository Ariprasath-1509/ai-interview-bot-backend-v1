"""
Pre-warms Whisper STT and Kokoro TTS after container startup.
Runs once as media-warmup service so the first real interview request has no cold-start delay.
"""
import os
import io
import wave
import struct
import urllib.request
import urllib.error
import json
import time

WHISPER_URL = os.environ.get("WHISPER_URL", "http://whisper-stt:8000")
KOKORO_URL = os.environ.get("KOKORO_URL", "http://kokoro-tts:8880")
KOKORO_VOICE = os.environ.get("KOKORO_VOICE", "af_heart")


def make_silent_wav(duration_s: float = 1.0, sample_rate: int = 16000) -> bytes:
    num_samples = int(sample_rate * duration_s)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(struct.pack(f"<{num_samples}h", *([0] * num_samples)))
    return buf.getvalue()


def warmup_whisper() -> None:
    print("[warmup] Warming up Whisper STT...")
    audio = make_silent_wav()
    boundary = "warmboundary"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="warmup.wav"\r\n'
        f"Content-Type: audio/wav\r\n\r\n"
    ).encode() + audio + f"\r\n--{boundary}--\r\n".encode()

    req = urllib.request.Request(
        f"{WHISPER_URL}/v1/audio/transcriptions",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            resp.read()
        print("[warmup] Whisper STT warm-up done.")
    except Exception as e:
        print(f"[warmup] Whisper warm-up error (non-fatal): {e}")


def warmup_kokoro() -> None:
    print("[warmup] Warming up Kokoro TTS...")
    payload = json.dumps({"model": "kokoro", "input": "Hello.", "voice": KOKORO_VOICE}).encode()
    req = urllib.request.Request(
        f"{KOKORO_URL}/v1/audio/speech",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            resp.read()
        print("[warmup] Kokoro TTS warm-up done.")
    except Exception as e:
        print(f"[warmup] Kokoro warm-up error (non-fatal): {e}")


if __name__ == "__main__":
    time.sleep(2)
    warmup_whisper()
    warmup_kokoro()
    print("[warmup] All media services warmed up.")
