package com.example.memexos;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhisperTestActivity extends AppCompatActivity {
    private static final String TAG = "WhisperTest";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String MODEL_ASSET_PATH = "models/ggml-tiny.bin";
    
    private Button btnRecord;
    private Button btnTranscribe;
    private TextView tvResult;
    private TextView tvStatus;
    private ProgressBar progressBar;
    
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private String modelPath;
    private boolean isRecording = false;
    
    private ExecutorService executorService;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create simple layout programmatically
        setContentView(createLayout());
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Setup audio file path
        File audioDir = new File(getFilesDir(), "audio");
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        audioFilePath = new File(audioDir, "recording.wav").getAbsolutePath();
        
        // Check permissions
        checkPermissions();
        
        // Initialize model
        initializeModel();
    }
    
    private View createLayout() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        tvStatus = new TextView(this);
        tvStatus.setText("Status: Initializing...");
        tvStatus.setPadding(0, 16, 0, 16);
        layout.addView(tvStatus);
        
        btnRecord = new Button(this);
        btnRecord.setText("Start Recording");
        btnRecord.setEnabled(false);
        btnRecord.setOnClickListener(v -> toggleRecording());
        layout.addView(btnRecord);
        
        btnTranscribe = new Button(this);
        btnTranscribe.setText("Transcribe");
        btnTranscribe.setEnabled(false);
        btnTranscribe.setOnClickListener(v -> transcribeAudio());
        layout.addView(btnTranscribe);
        
        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        layout.addView(progressBar);
        
        tvResult = new TextView(this);
        tvResult.setText("Transcription will appear here...");
        tvResult.setPadding(0, 32, 0, 0);
        layout.addView(tvResult);
        
        return layout;
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            onPermissionsGranted();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onPermissionsGranted();
            } else {
                Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_LONG).show();
                tvStatus.setText("Status: Permission denied");
            }
        }
    }
    
    private void onPermissionsGranted() {
        btnRecord.setEnabled(true);
        tvStatus.setText("Status: Ready to record");
    }
    
    private void initializeModel() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Status: Loading model...");
        
        executorService.execute(() -> {
            // Copy model from assets to internal storage
            modelPath = ModelUtils.copyModelFromAssets(this, MODEL_ASSET_PATH);
            
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (modelPath != null) {
                    tvStatus.setText("Status: Model loaded");
                    Log.i(TAG, "Model loaded: " + modelPath);
                } else {
                    tvStatus.setText("Status: Failed to load model");
                    Toast.makeText(this, "Failed to load Whisper model", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    
    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private void startRecording() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            isRecording = true;
            btnRecord.setText("Stop Recording");
            btnTranscribe.setEnabled(false);
            tvStatus.setText("Status: Recording...");
            tvResult.setText("Recording in progress...");
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                
                isRecording = false;
                btnRecord.setText("Start Recording");
                btnTranscribe.setEnabled(true);
                tvStatus.setText("Status: Recording saved");
                tvResult.setText("Recording saved. Press 'Transcribe' to convert to text.");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop recording", e);
            }
        }
    }
    
    private void transcribeAudio() {
        if (modelPath == null) {
            Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        btnTranscribe.setEnabled(false);
        btnRecord.setEnabled(false);
        tvStatus.setText("Status: Transcribing...");
        tvResult.setText("Processing audio...");
        
        executorService.execute(() -> {
            try {
                // Perform transcription
                WhisperWrapper whisper = WhisperWrapper.getInstance();
                String transcription = whisper.transcribe(audioFilePath, modelPath);
                
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnTranscribe.setEnabled(true);
                    btnRecord.setEnabled(true);
                    tvStatus.setText("Status: Transcription complete");
                    tvResult.setText("Transcription:\n\n" + transcription);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnTranscribe.setEnabled(true);
                    btnRecord.setEnabled(true);
                    tvStatus.setText("Status: Transcription failed");
                    tvResult.setText("Error: " + e.getMessage());
                    Toast.makeText(WhisperTestActivity.this, 
                            "Transcription failed: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
