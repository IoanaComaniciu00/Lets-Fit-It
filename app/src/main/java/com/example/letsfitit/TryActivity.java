package com.example.letsfitit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.util.List;

public class TryActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 1;
    private NetworkService networkService;
    private ImageView originalImageView;
    private ImageView resultImageView;
    private TextView itemsListTextView;
    private Button selectButton;
    private Button btnStartAR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_try);

        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Initialize views
        originalImageView = findViewById(R.id.original_image);
        resultImageView = findViewById(R.id.result_image);
        itemsListTextView = findViewById(R.id.detected_items);
        selectButton = findViewById(R.id.select_button);
        btnStartAR = findViewById(R.id.btnStartAR);

        // Initialize network service
        networkService = new NetworkService();

        // Check server health on startup
        checkServerHealth();

        selectButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        });

        btnStartAR.setOnClickListener(v -> {
            if (ARDataHolder.getSegmentedBitmap() != null) {
                startActivity(new Intent(TryActivity.this, ARActivity.class));
            } else {
                Toast.makeText(this, "Please segment an image first", Toast.LENGTH_SHORT).show();
            }
        });

        // Initially disable AR button until we have a segmented image
        btnStartAR.setEnabled(false);
    }

    private void checkServerHealth() {
        networkService.checkServerHealth(new NetworkService.HealthCallback() {
            @Override
            public void onHealthChecked(boolean isHealthy) {
                runOnUiThread(() -> {
                    if (isHealthy) {
                        Toast.makeText(TryActivity.this, "Server connected!", Toast.LENGTH_SHORT).show();
                        itemsListTextView.setText("Server ready - select an image");
                    } else {
                        Toast.makeText(TryActivity.this, "Server not available", Toast.LENGTH_LONG).show();
                        itemsListTextView.setText("Server offline - check connection");
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                // Load the selected image
                Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                // Check if bitmap is valid
                if (originalBitmap == null) {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Process the image with the server
                processImageWithServer(originalBitmap);

            } catch (IOException e) {
                Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void processImageWithServer(Bitmap originalBitmap) {
        originalImageView.setImageBitmap(originalBitmap);
        itemsListTextView.setText("Sending to server...");
        selectButton.setEnabled(false);

        networkService.segmentImage(originalBitmap, new NetworkService.SegmentationCallback() {
            @Override
            public void onSuccess(Bitmap segmentedImage, List<NetworkService.ClothingItem> detectedItems, String primaryItem) {
                runOnUiThread(() -> {
                    selectButton.setEnabled(true);
                    resultImageView.setImageBitmap(segmentedImage);


                    if (detectedItems.isEmpty() || primaryItem.equals("none")) {
                        itemsListTextView.setText("Trying to segment anyway...");

                        btnStartAR.setEnabled(true);
                        ARDataHolder.setSegmentedBitmap(segmentedImage);
                        ARDataHolder.setDetectedItems(detectedItems);
                        Toast.makeText(TryActivity.this, "Proceeding with segmentation for POC", Toast.LENGTH_SHORT).show();
                    } else {

                        StringBuilder itemsText = new StringBuilder("Detected");

                        itemsListTextView.setText(itemsText.toString());
                        btnStartAR.setEnabled(true);
                        ARDataHolder.setSegmentedBitmap(segmentedImage);
                        ARDataHolder.setDetectedItems(detectedItems);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    selectButton.setEnabled(true);
                    Toast.makeText(TryActivity.this, "Server error: " + error, Toast.LENGTH_LONG).show();
                    itemsListTextView.setText("Error: " + error);
                    btnStartAR.setEnabled(false);
                });
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }
}