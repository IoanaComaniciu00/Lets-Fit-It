package com.example.letsfitit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;

public class ARActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private SurfaceView cameraPreview;
    private ImageView clothingOverlay;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        initializeViews();
        setupTouchControls();

        // Check camera permission
        if (checkCameraPermission()) {
            setupCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void initializeViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        clothingOverlay = findViewById(R.id.clothing_overlay);
        btnBack = findViewById(R.id.btn_back);

        // Set the segmented clothing image
        if (ARDataHolder.getSegmentedBitmap() != null) {
            clothingOverlay.setImageBitmap(ARDataHolder.getSegmentedBitmap());
            clothingOverlay.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, "No clothing image available", Toast.LENGTH_SHORT).show();
            clothingOverlay.setVisibility(View.GONE);
        }

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupTouchControls() {
        clothingOverlay.setOnTouchListener(new View.OnTouchListener() {
            private int lastX, lastY;
            private int startWidth, startHeight;
            private float startDist;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction() & android.view.MotionEvent.ACTION_MASK) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                        break;

                    case android.view.MotionEvent.ACTION_MOVE:
                        if (event.getPointerCount() == 1) {
                            // Dragging
                            int dx = (int) event.getRawX() - lastX;
                            int dy = (int) event.getRawY() - lastY;

                            int left = v.getLeft() + dx;
                            int top = v.getTop() + dy;
                            int right = v.getRight() + dx;
                            int bottom = v.getBottom() + dy;

                            v.layout(left, top, right, bottom);

                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                        } else if (event.getPointerCount() == 2) {
                            // Pinch to zoom
                            float newDist = spacing(event);
                            if (newDist > 10f) {
                                float scale = newDist / startDist;

                                RelativeLayout.LayoutParams params =
                                        (RelativeLayout.LayoutParams) v.getLayoutParams();
                                params.width = (int) (startWidth * scale);
                                params.height = (int) (startHeight * scale);
                                v.setLayoutParams(params);
                            }
                        }
                        break;

                    case android.view.MotionEvent.ACTION_POINTER_DOWN:
                        startDist = spacing(event);
                        startWidth = v.getWidth();
                        startHeight = v.getHeight();
                        break;
                }
                return true;
            }

            private float spacing(android.view.MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return (float) Math.sqrt(x * x + y * y);
            }
        });
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    private void setupCamera() {
        surfaceHolder = cameraPreview.getHolder();
        surfaceHolder.addCallback(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            } else {
                Toast.makeText(this, "Camera permission required for AR try-on",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {

            camera = getFrontCamera();

            if (camera == null) {
                Toast.makeText(this, "Front camera not available", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            camera.setPreviewDisplay(holder);

            // Set camera parameters for better preview
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

            // Set preview size if available
            List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
            if (supportedSizes != null && !supportedSizes.isEmpty()) {
                Camera.Size optimalSize = getOptimalPreviewSize(supportedSizes,
                        cameraPreview.getWidth(), cameraPreview.getHeight());
                if (optimalSize != null) {
                    parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                }
            }

            camera.setParameters(parameters);
            camera.setDisplayOrientation(90); // Portrait orientation
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
        }
    }


    private Camera getFrontCamera() {
        try {
            int cameraCount = Camera.getNumberOfCameras();
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

            for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    return Camera.open(camIdx);
                }
            }

            // If no front camera found, return the first available camera
            if (cameraCount > 0) {
                return Camera.open(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        if (surfaceHolder.getSurface() == null || camera == null) return;

        try {
            camera.stopPreview();
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }
}