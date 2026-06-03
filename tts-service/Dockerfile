FROM python:3.10-slim

# Create a system user for running the application securely
RUN groupadd -r ttsuser && useradd -r -g ttsuser -d /home/ttsuser -m ttsuser

# Install system dependencies in a single layer to keep image slim
RUN apt-get update && apt-get install -y \
    libsndfile1 \
    ffmpeg \
    espeak-ng \
    && rm -rf /var/lib/apt/lists/*

# Pre-create model storage directory and hand permissions over to our non-root user
RUN mkdir -p /home/ttsuser/.local/share/tts && chown -R ttsuser:ttsuser /home/ttsuser/
USER ttsuser
WORKDIR /home/ttsuser

# Pin your packages to prevent random upstream pipeline breaks
RUN pip install --no-cache-dir --user TTS==0.22.0

EXPOSE 5002

# tts-server 0.22.0 has no --host flag; it listens on 0.0.0.0 by default
CMD ["/home/ttsuser/.local/bin/tts-server", "--model_name", "tts_models/en/ljspeech/tacotron2-DDC", "--port", "5002"]