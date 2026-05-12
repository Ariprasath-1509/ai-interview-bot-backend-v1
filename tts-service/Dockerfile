FROM python:3.10-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libsndfile1 \
    ffmpeg \
    espeak-ng \
    && rm -rf /var/lib/apt/lists/*

# Install Coqui TTS
RUN pip install --no-cache-dir TTS

EXPOSE 5002

# Run TTS server with Tacotron2 model (good quality, reasonable speed)
CMD ["tts-server", "--model_name", "tts_models/en/ljspeech/tacotron2-DDC", "--port", "5002", "--host", "0.0.0.0"]
