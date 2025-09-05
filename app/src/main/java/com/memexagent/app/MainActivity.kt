package com.memexagent.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.memexagent.app.audio.AudioRecorder
import com.memexagent.app.commands.VoiceCommandProcessor
import com.memexagent.app.whisper.WhisperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
        private const val MAX_RECORDING_DURATION_MS = 60000L // 60 seconds
        private const val AUDIO_SAMPLE_RATE = 16000
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val MIN_AUDIO_BUFFER_MULTIPLIER = 4
        private const val WHISPER_THREAD_COUNT = 4
        private const val RECORDING_WARNING_THRESHOLD_MS = 10000L
        private const val SCROLL_DISTANCE_PX = 500
        private const val DEFAULT_URL = "https://www.google.com"
        private const val GOOGLE_SEARCH_BASE_URL = "https://www.google.com/search?q="
        private const val MODEL_PATH = "models/ggml-tiny.bin"
    }
    
    private lateinit var webView: WebView
    private lateinit var urlEditText: TextInputEditText
    private lateinit var toolbar: Toolbar
    private lateinit var fabRecord: FloatingActionButton
    private lateinit var fabSettings: FloatingActionButton
    private lateinit var progressOverlay: FrameLayout
    private lateinit var progressText: TextView
    
    // Audio recording components
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisperService: WhisperService
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private var recordingDialog: AlertDialog? = null
    private var recordingTimer: CountDownTimer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var recordingStartTime: Long = 0
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        initializeViews()
        
        // Initialize audio components
        initializeAudioComponents()
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Setup WebView
        setupWebView()
        
        // Setup URL input
        setupUrlInput()
        
        // Setup FABs
        setupFABs()
        
        // Initialize voice command processor
        voiceCommandProcessor = VoiceCommandProcessor(this, webView)
        
        // Load initial URL
        webView.loadUrl(DEFAULT_URL)
    }
    
    private fun initializeAudioComponents() {
        audioRecorder = AudioRecorder()
        whisperService = WhisperService(this)
        
        // Initialize Whisper model from assets
        lifecycleScope.launch {
            try {
                val initialized = whisperService.initializeFromAsset(MODEL_PATH)
                if (initialized) {
                    Log.d(TAG, "Whisper service initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize Whisper service - model not found or invalid")
                    showModelMissingDialog()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper", e)
                showModelMissingDialog()
            }
        }
    }
    
    private fun initializeViews() {
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        toolbar = findViewById(R.id.toolbar)
        fabRecord = findViewById(R.id.fabRecord)
        fabSettings = findViewById(R.id.fabSettings)
        progressOverlay = findViewById(R.id.progressOverlay)
        progressText = findViewById(R.id.progressText)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Update URL in the address bar
                    urlEditText.setText(url)
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    // You can add a progress bar here if needed
                }
            }
        }
    }
    
    private fun setupUrlInput() {
        urlEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                loadUrl(urlEditText.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }
    }
    
    private fun setupFABs() {
        fabRecord.setOnClickListener {
            // Start recording
            startRecording()
        }
        
        fabSettings.setOnClickListener {
            // Open settings
            // TODO: Implement settings activity
        }
    }
    
    private fun loadUrl(url: String) {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }
        webView.loadUrl(formattedUrl)
    }
    
    private fun startRecording() {
        // Check and request permission
        if (checkRecordingPermission()) {
            showRecordingDialog()
        } else {
            requestRecordingPermission()
        }
    }
    
    private fun checkRecordingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestRecordingPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Microphone Permission Required")
                .setMessage("This app needs microphone access to record voice commands for speech-to-text conversion.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        PERMISSION_REQUEST_RECORD_AUDIO
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start recording
                showRecordingDialog()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required for voice commands",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showRecordingDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recording, null)
        
        val micPulseCircle = dialogView.findViewById<View>(R.id.micPulseCircle)
        val timerText = dialogView.findViewById<TextView>(R.id.timerText)
        val recordingStatusText = dialogView.findViewById<TextView>(R.id.recordingStatusText)
        val stopButton = dialogView.findViewById<MaterialButton>(R.id.stopButton)
        
        // Create and show dialog
        recordingDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        recordingDialog?.show()
        
        // Start pulse animation
        startPulseAnimation(micPulseCircle)
        
        // Start recording
        startAudioRecording(timerText, recordingStatusText)
        
        // Setup stop button
        stopButton.setOnClickListener {
            stopAudioRecording()
        }
    }
    
    private fun startPulseAnimation(view: View) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 0.6f, 0.3f)
        
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
    
    private fun startAudioRecording(timerText: TextView, statusText: TextView) {
        recordingStartTime = System.currentTimeMillis()
        
        // Create output file in cache directory with validation
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val audioFile = File(cacheDir, "recording_$timestamp.wav")
        
        // Ensure cache directory exists
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                Log.e(TAG, "Failed to create cache directory")
                Toast.makeText(this, "Failed to create recording directory", Toast.LENGTH_LONG).show()
                return
            }
        }
        
        // Start countdown timer
        recordingTimer = object : CountDownTimer(MAX_RECORDING_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / 1000) / 60
                timerText.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                
                // Show warning when approaching limit
                if (millisUntilFinished <= RECORDING_WARNING_THRESHOLD_MS) {
                    statusText.text = "Recording ending in ${millisUntilFinished / 1000}s..."
                    statusText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                }
            }
            
            override fun onFinish() {
                stopAudioRecording()
            }
        }.start()
        
        // Start actual recording
        lifecycleScope.launch {
            try {
                audioRecorder.startRecording(audioFile) { error ->
                    runOnUiThread {
                        Log.e(TAG, "Recording error", error)
                        Toast.makeText(this@MainActivity, "Recording failed: ${error.message}", Toast.LENGTH_LONG).show()
                        stopAudioRecording()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                Toast.makeText(this@MainActivity, "Failed to start recording", Toast.LENGTH_LONG).show()
                stopAudioRecording()
            }
        }
    }
    
    private fun stopAudioRecording() {
        // Stop timer
        recordingTimer?.cancel()
        recordingTimer = null
        
        // Stop animation
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        // Dismiss recording dialog
        recordingDialog?.dismiss()
        recordingDialog = null
        
        // Show processing overlay
        showProgressOverlay("Processing speech...")
        
        lifecycleScope.launch {
            try {
                // Stop recording
                audioRecorder.stopRecording()
                
                // Get the recorded file
                val recordings = cacheDir.listFiles { file ->
                    file.name.startsWith("recording_") && file.name.endsWith(".wav")
                }
                
                val latestRecording = recordings?.maxByOrNull { it.lastModified() }
                
                if (latestRecording != null && latestRecording.exists()) {
                    // Process with Whisper
                    processWithWhisper(latestRecording)
                } else {
                    hideProgressOverlay()
                    Toast.makeText(this@MainActivity, "No recording found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                hideProgressOverlay()
                Toast.makeText(this@MainActivity, "Failed to process recording", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun processWithWhisper(audioFile: File) {
        lifecycleScope.launch {
            try {
                // Transcribe the audio file
                val transcription = withContext(Dispatchers.IO) {
                    whisperService.transcribeFile(audioFile)
                }
                
                hideProgressOverlay()
                
                if (!transcription.isNullOrBlank()) {
                    // Process the transcribed command
                    processVoiceCommand(transcription)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not transcribe audio. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // Clean up the audio file
                audioFile.delete()
                
            } catch (e: Exception) {
                Log.e(TAG, "Whisper processing failed", e)
                hideProgressOverlay()
                Toast.makeText(
                    this@MainActivity,
                    "Speech processing failed. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun processVoiceCommand(command: String) {
        Log.d(TAG, "Voice command: $command")
        
        // Show the recognized text to user
        MaterialAlertDialogBuilder(this)
            .setTitle("Voice Command Recognized")
            .setMessage(command)
            .setPositiveButton("Execute") { _, _ ->
                executeCommand(command)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun executeCommand(command: String) {
        Log.d(TAG, "Executing voice command: $command")
        voiceCommandProcessor.processCommand(command)
    }
    
    private fun showProgressOverlay(message: String) {
        progressText.text = message
        progressOverlay.visibility = View.VISIBLE
    }
    
    private fun hideProgressOverlay() {
        progressOverlay.visibility = View.GONE
    }
    
    private fun showModelMissingDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Whisper Model Missing")
            .setMessage("The Whisper AI model is missing. Please ensure you have downloaded the model file and placed it in the assets/models folder. Run the download script: .\\download-whisper-model.ps1")
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        recordingTimer?.cancel()
        pulseAnimator?.cancel()
        recordingDialog?.dismiss()
        
        // Release Whisper resources
        whisperService.release()
        
        // Stop any ongoing recording
        if (audioRecorder.isRecording()) {
            lifecycleScope.launch {
                audioRecorder.stopRecording()
            }
        }
    }
}
