package com.memexagent.app.audio

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WaveFileEncoder {
    
    private const val TAG = "WaveFileEncoder"
    
    fun encodeWaveFile(file: File, data: ShortArray) {
        Log.d(TAG, "Encoding WAV file: ${file.absolutePath} with ${data.size} samples")
        
        if (data.isEmpty()) {
            Log.w(TAG, "No audio data to encode")
            throw IllegalArgumentException("Audio data is empty")
        }
        try {
            file.outputStream().use {
                val headerBytes = headerBytes(data.size * 2)
                it.write(headerBytes)
                
                val buffer = ByteBuffer.allocate(data.size * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.asShortBuffer().put(data)
                val bytes = ByteArray(buffer.limit())
                buffer.get(bytes)
                it.write(bytes)
            }
            Log.d(TAG, "WAV file encoded successfully: ${file.length()} bytes")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to encode WAV file", e)
            throw e
        }
    }
    
    fun decodeWaveFile(file: File): FloatArray {
        Log.d(TAG, "Decoding WAV file: ${file.absolutePath}")
        
        if (!file.exists()) {
            Log.e(TAG, "WAV file does not exist: ${file.absolutePath}")
            throw IllegalArgumentException("WAV file does not exist")
        }
        
        if (file.length() < 44) {
            Log.e(TAG, "WAV file too small: ${file.length()} bytes")
            throw IllegalArgumentException("Invalid WAV file - too small")
        }
        
        try {
            val buffer = ByteBuffer.wrap(file.readBytes())
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            
            // Validate WAV header
            val riffHeader = ByteArray(4)
            buffer.get(riffHeader)
            if (String(riffHeader) != "RIFF") {
                throw IllegalArgumentException("Not a valid WAV file - missing RIFF header")
            }
            
            // Skip file size
            buffer.getInt()
            
            val waveHeader = ByteArray(4)
            buffer.get(waveHeader)
            if (String(waveHeader) != "WAVE") {
                throw IllegalArgumentException("Not a valid WAV file - missing WAVE header")
            }
            
            // Read channel count
            buffer.position(22)
            val channel = buffer.getShort().toInt()
            Log.d(TAG, "WAV file channels: $channel")
            
            // Read sample rate
            val sampleRate = buffer.getInt()
            Log.d(TAG, "WAV file sample rate: $sampleRate")
            
            // Position at data
            buffer.position(44)
            val shortBuffer = buffer.asShortBuffer()
            val shortArray = ShortArray(shortBuffer.limit())
            shortBuffer.get(shortArray)
            
            Log.d(TAG, "Read ${shortArray.size} samples from WAV file")
            
            return FloatArray(shortArray.size / channel) { index ->
                when (channel) {
                    1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
                    else -> ((shortArray[2*index] + shortArray[2*index + 1]) / 32767.0f / 2.0f).coerceIn(-1f..1f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode WAV file", e)
            throw e
        }
    }

    private fun headerBytes(totalLength: Int): ByteArray {
        require(totalLength >= 44) { "Total length must be at least 44 bytes, got $totalLength" }
        Log.d(TAG, "Creating WAV header for $totalLength bytes")
        ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())

            putInt(totalLength - 8)

            // WAVE header
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())

            // fmt subchunk
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())

            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat (1 for PCM)
            putShort(1.toShort()) // NumChannels (1 for mono)
            putInt(16000) // SampleRate (16 kHz)
            putInt(32000) // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
            putShort(2.toShort()) // BlockAlign (NumChannels * BitsPerSample/8)
            putShort(16.toShort()) // BitsPerSample

            // data subchunk
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())

            putInt(totalLength - 44)
            position(0)
        }.also {
            val bytes = ByteArray(it.limit())
            it.get(bytes)
            Log.d(TAG, "WAV header created: ${bytes.size} bytes")
            return bytes
        }
    }
}
