# PowerShell script to download Whisper model for Android app
# This downloads the tiny model which is suitable for mobile testing

$modelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
$outputPath = "app\src\main\assets\models\ggml-tiny.bin"

# Create models directory if it doesn't exist
$modelsDir = "app\src\main\assets\models"
if (!(Test-Path $modelsDir)) {
    Write-Host "Creating models directory..."
    New-Item -ItemType Directory -Path $modelsDir -Force
}

Write-Host "Downloading Whisper tiny model (39 MB)..."
Write-Host "From: $modelUrl"
Write-Host "To: $outputPath"

try {
    # Download with progress
    $ProgressPreference = 'Continue'
    Invoke-WebRequest -Uri $modelUrl -OutFile $outputPath -UseBasicParsing
    
    # Verify download
    if (Test-Path $outputPath) {
        $fileSize = (Get-Item $outputPath).Length / 1MB
        Write-Host "Download complete! File size: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Green
        Write-Host "Model saved to: $outputPath" -ForegroundColor Green
    } else {
        Write-Host "Download failed - file not found" -ForegroundColor Red
    }
} catch {
    Write-Host "Error downloading model: $_" -ForegroundColor Red
    Write-Host "Please download manually from: $modelUrl" -ForegroundColor Yellow
    Write-Host "And save to: $outputPath" -ForegroundColor Yellow
}

Write-Host "`nNote: For better accuracy, you can also download:"
Write-Host "  - Base model (142 MB): https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
Write-Host "  - Small model (466 MB): https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
