package com.memexagent.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Audio recording constants accessible to all classes in this file
private const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val BUFFER_SIZE_MULTIPLIER = 4

class AudioRecorder {
    
    companion object {
        private const val TAG = "AudioRecorder"
    }
    
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var recorder: AudioRecordThread? = null

    suspend fun startRecording(outputFile: File, onError: (Exception) -> Unit) = withContext(scope.coroutineContext) {
        Log.d(TAG, "Starting audio recording to: ${outputFile.absolutePath}")
        
        if (recorder != null) {
            Log.w(TAG, "Recording already in progress, stopping previous recording")
            stopRecording()
        }
        
        recorder = AudioRecordThread(outputFile, onError)
        recorder?.start()
    }

    suspend fun stopRecording() = withContext(scope.coroutineContext) {
        Log.d(TAG, "Stopping audio recording")
        recorder?.stopRecording()
        @Suppress("BlockingMethodInNonBlockingContext")
        recorder?.join()
        recorder = null
        Log.d(TAG, "Audio recording stopped and cleaned up")
    }
    
    fun isRecording(): Boolean = recorder != null
}

private class AudioRecordThread(
    private val outputFile: File,
    private val onError: (Exception) -> Unit
) : Thread("AudioRecorder") {
    
    private var quit = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    override fun run() {
        Log.d("AudioRecordThread", "Starting audio recording thread")
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw RuntimeException("Invalid buffer size: $bufferSize")
            }
            
            Log.d("AudioRecordThread", "Buffer size: $bufferSize")
            val buffer = ShortArray(bufferSize / 2)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw RuntimeException("AudioRecord not initialized properly")
            }
            
            Log.d("AudioRecordThread", "AudioRecord initialized successfully")

            try {
                audioRecord.startRecording()
                Log.d("AudioRecordThread", "Recording started")

                val allData = mutableListOf<Short>()
                var totalSamples = 0

                while (!quit.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            allData.add(buffer[i])
                        }
                        totalSamples += read
                        
                        // Log progress every 10000 samples
                        if (totalSamples % 10000 == 0) {
                            Log.d("AudioRecordThread", "Recorded $totalSamples samples")
                        }
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e("AudioRecordThread", "Invalid operation during recording")
                        break
                    } else if (read == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e("AudioRecordThread", "Bad value during recording")
                        break
                    } else {
                        Log.w("AudioRecordThread", "audioRecord.read returned $read")
                    }
                }

                audioRecord.stop()
                Log.d("AudioRecordThread", "Recording stopped, encoding WAV file with $totalSamples samples")
                WaveFileEncoder.encodeWaveFile(outputFile, allData.toShortArray())
                Log.d("AudioRecordThread", "WAV file encoded successfully: ${outputFile.absolutePath}")
            } finally {
                audioRecord.release()
                Log.d("AudioRecordThread", "AudioRecord resources released")
            }
        } catch (e: Exception) {
            Log.e("AudioRecordThread", "Recording error", e)
            onError(e)
        }
    }

    fun stopRecording() {
        Log.d("AudioRecordThread", "Stop recording requested")
        quit.set(true)
    }
}
