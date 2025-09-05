package com.example.memexos;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ModelUtils {
    private static final String TAG = "ModelUtils";
    private static final String MODELS_DIR = "whisper_models";
    
    /**
     * Copy a model file from assets to internal storage
     * @param context Application context
     * @param assetPath Path to the model in assets (e.g., "models/ggml-tiny.bin")
     * @return Absolute path to the copied model file, or null if failed
     */
    public static String copyModelFromAssets(Context context, String assetPath) {
        String fileName = new File(assetPath).getName();
        File modelsDir = new File(context.getFilesDir(), MODELS_DIR);
        
        // Create models directory if it doesn't exist
        if (!modelsDir.exists()) {
            if (!modelsDir.mkdirs()) {
                Log.e(TAG, "Failed to create models directory");
                return null;
            }
        }
        
        File modelFile = new File(modelsDir, fileName);
        
        // Check if model already exists
        if (modelFile.exists()) {
            Log.i(TAG, "Model already exists: " + modelFile.getAbsolutePath());
            return modelFile.getAbsolutePath();
        }
        
        // Copy model from assets
        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(modelFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            Log.i(TAG, "Copying model from assets: " + assetPath);
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                // Log progress every 10MB
                if (totalBytes % (10 * 1024 * 1024) == 0) {
                    Log.i(TAG, "Copied " + (totalBytes / 1024 / 1024) + " MB...");
                }
            }
            
            Log.i(TAG, "Model copied successfully: " + modelFile.getAbsolutePath());
            Log.i(TAG, "Total size: " + (totalBytes / 1024 / 1024) + " MB");
            
            return modelFile.getAbsolutePath();
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy model from assets", e);
            // Clean up partial file
            if (modelFile.exists()) {
                modelFile.delete();
            }
            return null;
        }
    }
    
    /**
     * Check if a model exists in internal storage
     * @param context Application context
     * @param modelName Name of the model file (e.g., "ggml-tiny.bin")
     * @return true if model exists
     */
    public static boolean modelExists(Context context, String modelName) {
        File modelsDir = new File(context.getFilesDir(), MODELS_DIR);
        File modelFile = new File(modelsDir, modelName);
        return modelFile.exists();
    }
    
    /**
     * Get the path to a model in internal storage
     * @param context Application context
     * @param modelName Name of the model file
     * @return Absolute path to the model file
     */
    public static String getModelPath(Context context, String modelName) {
        File modelsDir = new File(context.getFilesDir(), MODELS_DIR);
        File modelFile = new File(modelsDir, modelName);
        return modelFile.getAbsolutePath();
    }
    
    /**
     * Delete a model from internal storage
     * @param context Application context
     * @param modelName Name of the model file
     * @return true if deletion was successful
     */
    public static boolean deleteModel(Context context, String modelName) {
        File modelsDir = new File(context.getFilesDir(), MODELS_DIR);
        File modelFile = new File(modelsDir, modelName);
        
        if (modelFile.exists()) {
            boolean deleted = modelFile.delete();
            Log.i(TAG, "Model " + modelName + " deletion: " + deleted);
            return deleted;
        }
        
        return false;
    }
    
    /**
     * Get the size of a model file in MB
     * @param context Application context
     * @param modelName Name of the model file
     * @return Size in MB, or -1 if file doesn't exist
     */
    public static float getModelSizeMB(Context context, String modelName) {
        File modelsDir = new File(context.getFilesDir(), MODELS_DIR);
        File modelFile = new File(modelsDir, modelName);
        
        if (modelFile.exists()) {
            return modelFile.length() / (1024.0f * 1024.0f);
        }
        
        return -1;
    }
}
