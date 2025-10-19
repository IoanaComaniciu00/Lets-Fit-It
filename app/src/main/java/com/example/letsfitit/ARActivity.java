package com.example.letsfitit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ARActivity extends AppCompatActivity implements MediaPipePoseDetector.PoseDetectionListener {
    private static final String TAG = "ARActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private MediaPipePoseDetector poseDetector;

    private ImageView clothingOverlay;
    private Button btnBack;
    private Button btnTogglePoseView;
    private TextView tvPoseStatus;
    private PoseOverlayView poseOverlay;

    private boolean isPoseDetected = false;
    private float initialClothingWidth = 400f;
    private float initialClothingHeight = 500f;

    private long lastProcessTime = 0;
    private static final long MIN_FRAME_INTERVAL = 300L;

    private boolean isFrontCamera = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_ar);
            initializeViews();
            setupPoseDetector();
            setupClickListeners();

            if (allPermissionsGranted()) {
                startCamera();
            } else {
                requestPermissions();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.preview_view);
        clothingOverlay = findViewById(R.id.clothing_overlay);
        btnBack = findViewById(R.id.btn_back);
        btnTogglePoseView = findViewById(R.id.btn_toggle_pose_view);
        tvPoseStatus = findViewById(R.id.tv_pose_status);
        poseOverlay = findViewById(R.id.pose_overlay);

        if (ARDataHolder.getSegmentedBitmap() != null) {
            clothingOverlay.setImageBitmap(ARDataHolder.getSegmentedBitmap());
            clothingOverlay.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) clothingOverlay.getLayoutParams();
            params.width = (int) initialClothingWidth;
            params.height = (int) initialClothingHeight;
            params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            clothingOverlay.setLayoutParams(params);
        } else {
            Toast.makeText(this, "No clothing image available", Toast.LENGTH_SHORT).show();
            clothingOverlay.setVisibility(View.GONE);
        }
    }

    private void setupPoseDetector() {
        poseDetector = new MediaPipePoseDetector(this, this);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnTogglePoseView.setOnClickListener(v -> {
            boolean isVisible = poseOverlay.getVisibility() == View.VISIBLE;
            poseOverlay.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            btnTogglePoseView.setText(isVisible ? "Show Pose" : "Hide Pose");
        });
    }

    private void updatePoseStatus() {
        if (tvPoseStatus != null) {
            String status = isPoseDetected ? "Pose: Tracking" : "Pose: Detecting...";
            tvPoseStatus.setText(status);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera setup error", e);
                Toast.makeText(this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        try {
            cameraProvider.unbindAll();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();
            isFrontCamera = true;

            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(1280, 720))
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed: " + e.getMessage(), e);
            Toast.makeText(this, "Camera binding failed", Toast.LENGTH_SHORT).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < MIN_FRAME_INTERVAL) {
            imageProxy.close();
            return;
        }
        lastProcessTime = currentTime;

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        try {
            Bitmap bitmap = ImageUtils.imageToBitmap(imageProxy);
            if (bitmap == null) {
                imageProxy.close();
                return;
            }

            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();


            poseDetector.processFrame(bitmap, rotationDegrees);

            Log.d(TAG, "Processing frame - Size: " + bitmap.getWidth() + "x" + bitmap.getHeight() +
                    ", Rotation: " + rotationDegrees + ", FrontCamera: " + isFrontCamera);

        } catch (Exception e) {
            Log.e(TAG, "Failed to process image: " + e.getMessage(), e);
        } finally {
            imageProxy.close();
        }
    }

    @Override
    public void onPoseDetected(Map<Integer, float[]> landmarks, int rotation) {
        runOnUiThread(() -> {
            positionClothingOnBody(landmarks, rotation);
            updatePoseOverlay(landmarks, rotation);
            isPoseDetected = true;
            updatePoseStatus();
        });
    }

    @Override
    public void onPoseUpdate(Map<Integer, float[]> landmarks, int rotation) {
        runOnUiThread(() -> updatePoseOverlay(landmarks, rotation));
    }

    @Override
    public void onPoseError(String error) {
        runOnUiThread(() -> tvPoseStatus.setText("Pose: Error - " + error));
    }

    private void updatePoseOverlay(Map<Integer, float[]> landmarks, int rotation) {
        if (poseOverlay == null || poseOverlay.getVisibility() != View.VISIBLE) return;

        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        List<float[]> keypoints = new ArrayList<>();

        for (float[] landmark : landmarks.values()) {
            if (landmark.length < 2) continue;
            float normX = landmark[0];
            float normY = landmark[1];

            float screenX, screenY;

            if (rotation == 270 && isFrontCamera) {

                screenX = normY * viewWidth;
                screenY = (1 - normX) * viewHeight;

                screenX = viewWidth - screenX;
            } else {

                screenX = isFrontCamera ? (1 - normX) * viewWidth : normX * viewWidth;
                screenY = normY * viewHeight;
            }

            keypoints.add(new float[]{screenX, screenY});
        }

        poseOverlay.setKeypoints(keypoints);

        if (landmarks.containsKey(11) && landmarks.containsKey(12)) {
            float[] leftShoulder = landmarks.get(11);
            float[] rightShoulder = landmarks.get(12);
            Log.d(TAG, String.format("Rotation %d - Shoulders - L(%.3f,%.3f) R(%.3f,%.3f)",
                    rotation, leftShoulder[0], leftShoulder[1], rightShoulder[0], rightShoulder[1]));
        }
    }

    private void positionClothingOnBody(Map<Integer, float[]> landmarks, int rotation) {
        float[] leftShoulder = landmarks.get(11);
        float[] rightShoulder = landmarks.get(12);
        float[] leftHip = landmarks.get(23);
        float[] rightHip = landmarks.get(24);

        if (leftShoulder == null || rightShoulder == null) {
            Log.w(TAG, "Shoulder landmarks not found");
            return;
        }

        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        float leftShoulderX, leftShoulderY, rightShoulderX, rightShoulderY;

        if (rotation == 270 && isFrontCamera) {
            // For 270° rotation with front camera
            leftShoulderX = leftShoulder[1] * viewWidth;
            leftShoulderY = (1 - leftShoulder[0]) * viewHeight;
            rightShoulderX = rightShoulder[1] * viewWidth;
            rightShoulderY = (1 - rightShoulder[0]) * viewHeight;

            // Mirror for front camera
            leftShoulderX = viewWidth - leftShoulderX;
            rightShoulderX = viewWidth - rightShoulderX;
        } else {
            // Default mapping
            leftShoulderX = isFrontCamera ? (1 - leftShoulder[0]) * viewWidth : leftShoulder[0] * viewWidth;
            leftShoulderY = leftShoulder[1] * viewHeight;
            rightShoulderX = isFrontCamera ? (1 - rightShoulder[0]) * viewWidth : rightShoulder[0] * viewWidth;
            rightShoulderY = rightShoulder[1] * viewHeight;
        }

        float centerX = (leftShoulderX + rightShoulderX) / 2f;
        float centerY = (leftShoulderY + rightShoulderY) / 2f;
        float shoulderWidth = Math.abs(rightShoulderX - leftShoulderX);

        float hipCenterY = centerY;
        if (leftHip != null && rightHip != null) {
            float leftHipY, rightHipY;

            if (rotation == 270 && isFrontCamera) {
                leftHipY = (1 - leftHip[0]) * viewHeight;
                rightHipY = (1 - rightHip[0]) * viewHeight;
            } else {
                leftHipY = leftHip[1] * viewHeight;
                rightHipY = rightHip[1] * viewHeight;
            }

            hipCenterY = (leftHipY + rightHipY) / 2f;
        }

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) clothingOverlay.getLayoutParams();
        params.removeRule(RelativeLayout.CENTER_IN_PARENT);

        String clothingType = ARDataHolder.getClothingType();
        Log.d(TAG, "Clothing type: " + clothingType);

        float scaleFactor;
        int verticalOffset;

        switch (clothingType != null ? clothingType : "unknown") {
            case "pants":
            case "shorts":
                scaleFactor = Math.max(0.7f, Math.min(shoulderWidth * 1.8f / initialClothingWidth, 2.0f));
                verticalOffset = (int) (hipCenterY - (params.height / 3));
                break;
            case "dress":
                scaleFactor = Math.max(0.8f, Math.min(shoulderWidth * 2.0f / initialClothingWidth, 2.2f));
                verticalOffset = (int) (centerY - (params.height / 6));
                break;
            case "shirt":
            case "jacket":
            default:
                scaleFactor = Math.max(0.6f, Math.min(shoulderWidth * 2.2f / initialClothingWidth, 2.0f));
                verticalOffset = (int) (centerY - (params.height / 4));
                break;
        }

        params.width = (int) (initialClothingWidth * scaleFactor);
        params.height = (int) (initialClothingHeight * scaleFactor);

        params.leftMargin = (int) (centerX - (params.width / 2));
        params.topMargin = (int) Math.max(0, Math.min(verticalOffset, viewHeight - params.height));

        clothingOverlay.setLayoutParams(params);
        clothingOverlay.setVisibility(View.VISIBLE);

        Log.d(TAG, String.format("Clothing - Type: %s, Scale: %.2f, Pos: (%d, %d), Size: %dx%d, Rotation: %d",
                clothingType, scaleFactor, params.leftMargin, params.topMargin, params.width, params.height, rotation));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (poseDetector != null) poseDetector.cleanup();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}