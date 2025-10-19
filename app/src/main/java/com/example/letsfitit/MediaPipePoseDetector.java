package com.example.letsfitit;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

public class MediaPipePoseDetector {
    private static final String TAG = "MediaPipePoseDetector";
    private static final String POSE_LANDMARKER_TASK = "pose_landmarker_full.task";

    private PoseLandmarker poseLandmarker;
    private PoseDetectionListener listener;
    private int currentRotation = 0;

    public interface PoseDetectionListener {
        void onPoseDetected(Map<Integer, float[]> landmarks, int rotation);
        void onPoseUpdate(Map<Integer, float[]> landmarks, int rotation);
        void onPoseError(String error);
    }

    public MediaPipePoseDetector(Context context, PoseDetectionListener listener) {
        this.listener = listener;
        initializePoseDetector(context);
    }

    private void initializePoseDetector(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(POSE_LANDMARKER_TASK)
                    .build();


            PoseLandmarkerOptions options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build();

            poseLandmarker = PoseLandmarker.createFromOptions(context, options);
            Log.d(TAG, "MediaPipe PoseLandmarker initialized successfully with IMAGE mode");

        } catch (Exception e) {
            String errorMessage = "Failed to initialize MediaPipe Pose: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
            if (listener != null) {
                listener.onPoseError(errorMessage);
            }
        }
    }

    public void processFrame(Bitmap bitmap, int rotation) {
        if (poseLandmarker == null) {
            Log.w(TAG, "Pose detector not initialized");
            return;
        }

        if (bitmap == null) {
            Log.w(TAG, "Bitmap is null");
            return;
        }

        try {
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();


            this.currentRotation = rotation;


            PoseLandmarkerResult result = poseLandmarker.detect(mpImage);
            processPoseResult(result, rotation);

            Log.d(TAG, "Processing frame with rotation: " + rotation);

        } catch (Exception e) {
            String errorMessage = "Error processing frame: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
            if (listener != null) {
                listener.onPoseError(errorMessage);
            }
        }
    }

    private void processPoseResult(PoseLandmarkerResult result, int rotation) {
        if (result == null || result.landmarks().isEmpty()) {
            Log.d(TAG, "No pose landmarks detected");
            return;
        }

        Map<Integer, float[]> landmarks = new HashMap<>();
        boolean hasRequiredLandmarks = false;

        try {
            List<NormalizedLandmark> personLandmarks = result.landmarks().get(0);

            for (int i = 0; i < personLandmarks.size(); i++) {
                NormalizedLandmark landmark = personLandmarks.get(i);
                float x = landmark.x();
                float y = landmark.y();
                float z = landmark.z();
                landmarks.put(i, new float[]{x, y, z});

                if (i == 11 || i == 12) {
                    hasRequiredLandmarks = true;
                }
            }

            if (listener != null) {
                if (hasRequiredLandmarks) {
                    listener.onPoseDetected(landmarks, rotation);
                }
                listener.onPoseUpdate(landmarks, rotation);
            }

            Log.d(TAG, "MediaPipe pose detected with " + landmarks.size() + " landmarks, rotation: " + rotation);
        } catch (Exception e) {
            Log.e(TAG, "Error processing pose result: " + e.getMessage(), e);
        }
    }

    public void cleanup() {
        if (poseLandmarker != null) {
            try {
                poseLandmarker.close();
                Log.d(TAG, "MediaPipe PoseLandmarker cleaned up");
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up MediaPipe: " + e.getMessage(), e);
            }
        }
    }
}