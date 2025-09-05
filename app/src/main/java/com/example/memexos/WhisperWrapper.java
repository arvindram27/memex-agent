package com.example.memexos;

public class WhisperWrapper {
    
    // Load the native library
    static {
        System.loadLibrary("memexos_native");
    }
    
    // Native methods
    public native String nativeTranscribe(String audioPath, String modelPath);
    public native void nativeInit();
    public native void nativeCleanup();
    
    // Singleton instance
    private static WhisperWrapper instance;
    
    private WhisperWrapper() {
        nativeInit();
    }
    
    public static synchronized WhisperWrapper getInstance() {
        if (instance == null) {
            instance = new WhisperWrapper();
        }
        return instance;
    }
    
    /**
     * Transcribe audio file to text
     * @param audioPath Path to the audio file (WAV format)
     * @param modelPath Path to the Whisper model file
     * @return Transcribed text or error message
     */
    public String transcribe(String audioPath, String modelPath) {
        return nativeTranscribe(audioPath, modelPath);
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        nativeCleanup();
    }
}
