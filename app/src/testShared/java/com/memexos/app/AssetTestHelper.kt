package com.memexos.app

import android.content.res.AssetManager
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Helper for creating mock asset managers and asset files for testing.
 */
object AssetTestHelper {
    
    /**
     * Creates a mock AssetManager with predefined assets.
     * 
     * @param assets Map of asset paths to their content as byte arrays
     * @return Mock AssetManager
     */
    fun createMockAssetManager(assets: Map<String, ByteArray>): AssetManager {
        val assetManager = mock<AssetManager>()
        
        assets.forEach { (path, content) ->
            val inputStream = ByteArrayInputStream(content)
            doReturn(inputStream).`when`(assetManager).open(path)
        }
        
        // Mock list() method for directory listing
        val directories = assets.keys.mapNotNull { path ->
            val segments = path.split("/")
            if (segments.size > 1) {
                segments.dropLast(1).joinToString("/")
            } else null
        }.distinct()
        
        directories.forEach { dir ->
            val filesInDir = assets.keys
                .filter { it.startsWith("$dir/") && it.count { c -> c == '/' } == dir.count { c -> c == '/' } + 1 }
                .map { it.substring(it.lastIndexOf('/') + 1) }
                .toTypedArray()
            
            doReturn(filesInDir).`when`(assetManager).list(dir)
        }
        
        return assetManager
    }
    
    /**
     * Creates a mock Whisper model file content for testing.
     * This is just dummy data, not a real model.
     */
    fun createMockWhisperModel(): ByteArray {
        return "MOCK_WHISPER_MODEL_DATA".toByteArray()
    }
    
    /**
     * Creates test audio data (PCM 16-bit mono at 16kHz).
     * This generates a simple sine wave for testing purposes.
     * 
     * @param durationSeconds Duration of the audio in seconds
     * @param frequency Frequency of the sine wave in Hz
     * @return FloatArray representing PCM audio data
     */
    fun createTestAudioData(
        durationSeconds: Float = 1.0f,
        frequency: Float = 440.0f
    ): FloatArray {
        val sampleRate = 16000
        val samples = (durationSeconds * sampleRate).toInt()
        val audioData = FloatArray(samples)
        
        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            audioData[i] = kotlin.math.sin(2.0 * kotlin.math.PI * frequency * time).toFloat()
        }
        
        return audioData
    }
    
    /**
     * Creates test WAV file content.
     * This creates a minimal WAV file header with the provided audio data.
     */
    fun createTestWavFile(audioData: FloatArray, sampleRate: Int = 16000): ByteArray {
        val audioBytes = audioData.flatMap { sample ->
            val intSample = (sample * 32767).toInt().coerceIn(-32768, 32767)
            listOf(
                (intSample and 0xFF).toByte(),
                ((intSample shr 8) and 0xFF).toByte()
            )
        }.toByteArray()
        
        val dataSize = audioBytes.size
        val fileSize = 36 + dataSize
        
        return buildList<Byte> {
            // RIFF header
            addAll("RIFF".toByteArray().toList())
            addAll(fileSize.toLittleEndianBytes())
            addAll("WAVE".toByteArray().toList())
            
            // fmt chunk
            addAll("fmt ".toByteArray().toList())
            addAll(16.toLittleEndianBytes()) // PCM format size
            addAll(1.toShort().toLittleEndianBytes()) // PCM format
            addAll(1.toShort().toLittleEndianBytes()) // Mono
            addAll(sampleRate.toLittleEndianBytes())
            addAll((sampleRate * 2).toLittleEndianBytes()) // Byte rate
            addAll(2.toShort().toLittleEndianBytes()) // Block align
            addAll(16.toShort().toLittleEndianBytes()) // Bits per sample
            
            // data chunk
            addAll("data".toByteArray().toList())
            addAll(dataSize.toLittleEndianBytes())
            addAll(audioBytes.toList())
        }.toByteArray()
    }
    
    private fun Int.toLittleEndianBytes(): List<Byte> = listOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )
    
    private fun Short.toLittleEndianBytes(): List<Byte> = listOf(
        (this.toInt() and 0xFF).toByte(),
        ((this.toInt() shr 8) and 0xFF).toByte()
    )
}
