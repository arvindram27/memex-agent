package com.memexagent.app.context

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * Manages screen capture capabilities for the voice-controlled browser agent.
 * Provides screen context awareness by capturing and processing screen content.
 */
class ScreenContextManager(private val context: Context) {
    
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    companion object {
        private const val TAG = "ScreenContextManager"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1000
        private const val VIRTUAL_DISPLAY_NAME = "MemexAgent_ScreenCapture"
    }
    
    init {
        initializeDisplayMetrics()
    }
    
    private fun initializeDisplayMetrics() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi
        
        Log.d(TAG, "Screen metrics initialized: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }
    
    /**
     * Request screen capture permission from the user.
     * This must be called from an Activity context.
     */
    fun requestScreenCapturePermission(activity: Activity) {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }
    
    /**
     * Initialize screen capture with the permission result.
     * Call this from onActivityResult in your Activity.
     */
    fun initializeScreenCapture(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Screen capture permission denied")
            return false
        }
        
        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            setupImageReader()
            createVirtualDisplay()
            
            Log.d(TAG, "Screen capture initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize screen capture", e)
            return false
        }
    }
    
    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
    }
    
    private fun createVirtualDisplay() {
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }
    
    /**
     * Capture the current screen content as a Bitmap.
     * Returns null if screen capture is not initialized or fails.
     */
    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "No image available from ImageReader")
                return@withContext null
            }
            
            val bitmap = imageToBitmap(image)
            image.close()
            
            Log.d(TAG, "Screen captured successfully: ${bitmap?.width}x${bitmap?.height}")
            return@withContext bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screen", e)
            return@withContext null
        }
    }
    
    /**
     * Capture screen with a callback for immediate processing.
     */
    fun captureScreenAsync(callback: (Bitmap?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = captureScreen()
            callback(bitmap)
        }
    }
    
    private fun imageToBitmap(image: Image): Bitmap? {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            
            bitmap.copyPixelsFromBuffer(buffer)
            
            return if (rowPadding == 0) {
                bitmap
            } else {
                // Crop the bitmap if there's padding
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap", e)
            return null
        }
    }
    
    /**
     * Check if screen capture is currently available and initialized.
     */
    fun isScreenCaptureAvailable(): Boolean {
        return mediaProjection != null && virtualDisplay != null && imageReader != null
    }
    
    /**
     * Get current screen dimensions.
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        return Pair(screenWidth, screenHeight)
    }
    
    /**
     * Stop screen capture and release resources.
     */
    fun stopScreenCapture() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
            
            virtualDisplay = null
            imageReader = null
            mediaProjection = null
            
            Log.d(TAG, "Screen capture stopped and resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen capture", e)
        }
    }
    
    /**
     * Register a callback to be notified when the MediaProjection stops.
     */
    fun setOnMediaProjectionStoppedCallback(callback: () -> Unit) {
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                callback()
            }
        }, Handler(Looper.getMainLooper()))
    }
}
