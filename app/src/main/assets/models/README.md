# Whisper Models

This directory should contain the Whisper model files in GGML format.

## Download Models

You can download pre-converted models from the Whisper.cpp repository:

### Tiny Model (39 MB) - Recommended for testing
```bash
curl -L -o ggml-tiny.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
```

### Base Model (142 MB) - Better accuracy
```bash
curl -L -o ggml-base.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
```

### Small Model (466 MB) - Good balance
```bash
curl -L -o ggml-small.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin
```

## PowerShell Commands (for Windows)

### Tiny Model
```powershell
Invoke-WebRequest -Uri "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" -OutFile "ggml-tiny.bin"
```

### Base Model
```powershell
Invoke-WebRequest -Uri "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin" -OutFile "ggml-base.bin"
```

## Usage in Code

After downloading, you can access the model from your Android code:

```java
// Copy model from assets to internal storage
String modelPath = copyAssetToInternalStorage("models/ggml-tiny.bin");

// Use with WhisperWrapper
WhisperWrapper whisper = WhisperWrapper.getInstance();
String transcription = whisper.transcribe(audioPath, modelPath);
```

## Model Sizes and Performance

| Model | Size | Speed | Accuracy |
|-------|------|-------|----------|
| tiny  | 39 MB | Fastest | Basic |
| base  | 142 MB | Fast | Good |
| small | 466 MB | Medium | Better |
| medium | 1.5 GB | Slow | Best for mobile |

For mobile PoC, recommend starting with `tiny` or `base` model.
