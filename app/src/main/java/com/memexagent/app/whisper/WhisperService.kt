package com.memexagent.app.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperService(private val context: Context) {
    
    companion object {
        private const val TAG = "WhisperService"
        
        init {
            try {
                System.loadLibrary("memexagent_native")
                Log.d(TAG, "Whisper library loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Whisper library", e)
            }
        }
    }
    
    private var contextPtr: Long = 0L
    private var isInitialized = false
    
    /**
     * Initialize Whisper with a model file from assets
     */
    suspend fun initializeFromAsset(assetPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                release()
            }
            
            contextPtr = initContextFromAsset(context.assets, assetPath)
            isInitialized = contextPtr != 0L
            
            if (isInitialized) {
                Log.d(TAG, "Whisper initialized successfully from asset: $assetPath")
            } else {
                Log.e(TAG, "Failed to initialize Whisper from asset: $assetPath")
            }
            
            return@withContext isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper", e)
            return@withContext false
        }
    }
    
    /**
     * Initialize Whisper with a model file from file path
     */
    suspend fun initializeFromFile(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                release()
            }
            
            contextPtr = initContext(modelPath)
            isInitialized = contextPtr != 0L
            
            if (isInitialized) {
                Log.d(TAG, "Whisper initialized successfully from file: $modelPath")
            } else {
                Log.e(TAG, "Failed to initialize Whisper from file: $modelPath")
            }
            
            return@withContext isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper", e)
            return@withContext false
        }
    }
    
    /**
     * Transcribe audio data
     */
    suspend fun transcribe(audioData: FloatArray): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e(TAG, "Whisper not initialized")
            return@withContext null
        }
        
        try {
            // Run transcription with 4 threads by default
            fullTranscribe(contextPtr, 4, audioData)
            
            // Get the transcribed text
            val textCount = getTextSegmentCount(contextPtr)
            val result = StringBuilder()
            
            for (i in 0 until textCount) {
                val segment = getTextSegment(contextPtr, i)
                result.append(segment).append(" ")
            }
            
            return@withContext result.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            return@withContext null
        }
    }
    
    /**
     * Transcribe audio from WAV file
     */
    suspend fun transcribeFile(wavFile: File): String? = withContext(Dispatchers.IO) {
        if (!wavFile.exists()) {
            Log.e(TAG, "WAV file does not exist: ${wavFile.absolutePath}")
            return@withContext null
        }
        
        try {
            val audioData = com.memexagent.app.audio.WaveFileEncoder.decodeWaveFile(wavFile)
            return@withContext transcribe(audioData)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file", e)
            return@withContext null
        }
    }
    
    /**
     * Release Whisper resources
     */
    fun release() {
        if (isInitialized && contextPtr != 0L) {
            freeContext(contextPtr)
            contextPtr = 0L
            isInitialized = false
            Log.d(TAG, "Whisper resources released")
        }
    }
    
    // Native methods - these will be linked to the C++ implementation
    private external fun initContext(modelPath: String): Long
    private external fun initContextFromAsset(assetManager: android.content.res.AssetManager, assetPath: String): Long
    private external fun freeContext(contextPtr: Long)
    private external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)
    private external fun getTextSegmentCount(contextPtr: Long): Int
    private external fun getTextSegment(contextPtr: Long, index: Int): String
}
