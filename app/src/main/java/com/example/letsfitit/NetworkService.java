
package com.example.letsfitit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class NetworkService {
    private static final String TAG = "NetworkService";
    private static final String BASE_URL = "http://192.168.100.24:5000"; //  IP 192.168.100.24
    private OkHttpClient client;

    public NetworkService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public interface SegmentationCallback {
        void onSuccess(Bitmap segmentedImage, List<ClothingItem> detectedItems, String primaryItem);
        void onError(String error);
    }

    public static class ClothingItem {
        private String label;
        private double score;

        public ClothingItem(String label, double score) {
            this.label = label;
            this.score = score;
        }

        public String getLabel() {
            return label;
        }

        public double getScore() {
            return score;
        }
    }

    public void segmentImage(Bitmap bitmap, SegmentationCallback callback) {
        new Thread(() -> {
            try {
                // Convert bitmap to base64
                String imageBase64 = bitmapToBase64(bitmap);
                Log.d(TAG, "Sending image to server, size: " + imageBase64.length());

                // Create JSON request
                JSONObject requestBody = new JSONObject();
                requestBody.put("image", "data:image/jpeg;base64," + imageBase64);

                Request request = new Request.Builder()
                        .url(BASE_URL + "/segment")
                        .post(RequestBody.create(
                                MediaType.parse("application/json"),
                                requestBody.toString()))
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                // DEBUG: Log the entire response
                Log.d(TAG, "Server response: " + responseData);

                JSONObject result = new JSONObject(responseData);

                if (result.getBoolean("success")) {
                    // Get segmented image
                    String imageData = result.getString("segmented_image").split(",")[1];
                    Bitmap segmentedBitmap = base64ToBitmap(imageData);

                    // Parse detected items
                    List<ClothingItem> detectedItems = new ArrayList<>();
                    JSONArray itemsArray = result.getJSONArray("detected_items");
                    Log.d(TAG, "Detected items count: " + itemsArray.length());

                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject item = itemsArray.getJSONObject(i);
                        ClothingItem clothingItem = new ClothingItem(
                                item.getString("label"),
                                item.getDouble("score")
                        );
                        detectedItems.add(clothingItem);
                        Log.d(TAG, "Item: " + clothingItem.getLabel() + " score: " + clothingItem.getScore());
                    }

                    String primaryItem = result.getString("primary_item");
                    Log.d(TAG, "Primary item: " + primaryItem);

                    // Set detected items in ARDataHolder for clothing type detection
                    ARDataHolder.setDetectedItems(detectedItems);

                    if (callback != null) {
                        callback.onSuccess(segmentedBitmap, detectedItems, primaryItem);
                    }

                } else {
                    Log.e(TAG, "Server returned error: " + result.getString("error"));
                    if (callback != null) {
                        callback.onError(result.getString("error"));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Network error", e);
                if (callback != null) {
                    callback.onError("Network error: " + e.getMessage());
                }
            }
        }).start();
    }

    public void checkServerHealth(HealthCallback callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/health")
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                JSONObject result = new JSONObject(responseData);
                String status = result.getString("status");

                if (callback != null) {
                    callback.onHealthChecked("ready".equals(status));
                }

            } catch (Exception e) {
                Log.e(TAG, "Health check failed", e);
                if (callback != null) {
                    callback.onHealthChecked(false);
                }
            }
        }).start();
    }

    public interface HealthCallback {
        void onHealthChecked(boolean isHealthy);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private Bitmap base64ToBitmap(String base64String) {
        byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }
}