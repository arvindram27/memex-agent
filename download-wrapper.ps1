# PowerShell script to download gradle wrapper
$wrapperUrl = "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
$wrapperPath = "gradle\wrapper\gradle-wrapper.jar"

try {
    Write-Host "Downloading gradle wrapper..."
    Invoke-WebRequest -Uri $wrapperUrl -OutFile $wrapperPath
    Write-Host "Gradle wrapper downloaded successfully!"
} catch {
    Write-Host "Failed to download gradle wrapper. Please download manually from:"
    Write-Host $wrapperUrl
    Write-Host "And place it in: $wrapperPath"
}
