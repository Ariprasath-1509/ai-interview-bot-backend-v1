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

# Install CPU-only PyTorch first — avoids pulling CUDA wheels and shrinks the image ~2GB
RUN pip install --no-cache-dir --user \
    torch==2.6.0+cpu \
    --index-url https://download.pytorch.org/whl/cpu

# Install torchaudio without CPU suffix (it will use the CPU torch we just installed)
RUN pip install --no-cache-dir --user \
    torchaudio==2.6.0 \
    --extra-index-url https://download.pytorch.org/whl/cpu

# Pin TTS; extra-index-url keeps pip from replacing CPU torch with CUDA wheels
RUN pip install --no-cache-dir --user TTS==0.22.0 \
    --extra-index-url https://download.pytorch.org/whl/cpu

# Bake VITS weights into the image so deploy does not wait on runtime download (volume mount would hide this).
RUN /home/ttsuser/.local/bin/tts \
    --model_name tts_models/en/ljspeech/vits \
    --text "Model warmup." \
    --out_path /tmp/warmup.wav \
    && rm -f /tmp/warmup.wav

EXPOSE 5002

# tts-server 0.22.0 has no --host flag; it listens on 0.0.0.0 by default
CMD ["/home/ttsuser/.local/bin/tts-server", "--model_name", "tts_models/en/ljspeech/vits", "--port", "5002"]
