package com.example.letsfitit;

import android.content.Context;
import android.graphics.*;
import android.util.Log;
import android.util.Pair;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class Model {
    private Interpreter interpreter;
    private static final String TAG = "ClothingModel";
    private static final String MODEL_NAME = "clothing_segmenter_fixed.tflite";

    private static final String[] CLASS_NAMES = {
            "Background", "Hat", "Hair", "Sunglasses",
            "Upper-clothes", "Skirt", "Pants", "Dress",
            "Belt", "Left-shoe", "Right-shoe", "Face",
            "Left-leg", "Right-leg", "Left-arm", "Right-arm",
            "Bag", "Scarf"
    };


    private static final int[] CLOTHING_CLASSES = {4, 5, 6, 7};

    public Model(Context context) throws IOException {
        interpreter = loadModelFile(context, MODEL_NAME);
        Log.d(TAG, "TensorFlow Lite model loaded successfully!");
    }

    private Interpreter loadModelFile(Context context, String modelName) throws IOException {
        android.content.res.AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer modelFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        Interpreter.Options options = new Interpreter.Options();
        options.setUseXNNPACK(true);

        return new Interpreter(modelFile, options);
    }

    public Pair<Bitmap, List<String>> processImage(Bitmap image) {
        try {
            Log.d(TAG, "Starting image processing...");

            // 1. Preprocess the image
            float[][][][] input = preprocessBitmap(image);
            Log.d(TAG, "Preprocessing completed");

            // 2. Run inference
            float[][][][] output = new float[1][18][128][128];
            interpreter.run(input, output);
            Log.d(TAG, "Inference completed");

            // 3. Debug: Print output statistics
            debugOutput(output[0]);

            // 4. Create segmented image and get detected items
            Bitmap segmentedImage = createSegmentedBitmap(image, output[0]);
            List<String> detectedItems = getDetectedItems(output[0]);

            Log.d(TAG, "Detected " + detectedItems.size() + " clothing items: " + detectedItems);

            return new Pair<>(segmentedImage, detectedItems);

        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            return new Pair<>(image, new ArrayList<>());
        }
    }

    private float[][][][] preprocessBitmap(Bitmap bitmap) {
        // Resize to 512x512 (model input size)
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true);
        float[][][][] input = new float[1][512][512][3];

        for (int x = 0; x < 512; x++) {
            for (int y = 0; y < 512; y++) {
                int pixel = resizedBitmap.getPixel(x, y);

                // Normalize using ImageNet stats
                input[0][y][x][0] = (Color.red(pixel) / 255.0f - 0.485f) / 0.229f;   // R
                input[0][y][x][1] = (Color.green(pixel) / 255.0f - 0.456f) / 0.224f; // G
                input[0][y][x][2] = (Color.blue(pixel) / 255.0f - 0.406f) / 0.225f;  // B
            }
        }
        return input;
    }

    private Bitmap createSegmentedBitmap(Bitmap original, float[][][] output) {
        int width = original.getWidth();
        int height = original.getHeight();

        // Create output bitmap with transparency
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        // Create mask for clothing items
        Paint maskPaint = new Paint();
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        Bitmap maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int clothingPixels = 0;
        int totalPixels = 0;

        // For each pixel, check if it belongs to clothing classes
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                totalPixels++;

                // Map coordinates to output space (128x128)
                int outputX = Math.min((x * 127) / width, 127);
                int outputY = Math.min((y * 127) / height, 127);

                // Find class with highest probability
                float maxProb = 0;
                int maxClass = 0;
                for (int cls = 0; cls < 18; cls++) {
                    if (output[cls][outputY][outputX] > maxProb) {
                        maxProb = output[cls][outputY][outputX];
                        maxClass = cls;
                    }
                }

                // If it's a clothing class, make it opaque in the mask
                boolean isClothing = false;
                for (int clothClass : CLOTHING_CLASSES) {
                    if (maxClass == clothClass) {
                        isClothing = true;
                        clothingPixels++;
                        break;
                    }
                }

                if (isClothing) {
                    maskBitmap.setPixel(x, y, Color.WHITE);
                } else {
                    maskBitmap.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }

        Log.d(TAG, "Clothing pixels: " + clothingPixels + "/" + totalPixels +
                " (" + (clothingPixels * 100 / totalPixels) + "%)");

        // Apply Gaussian blur for smooth edges
        Bitmap blurredMask = applyBlurToMask(maskBitmap);

        // Apply the mask to create transparency
        canvas.drawBitmap(blurredMask, 0, 0, maskPaint);

        return result;
    }

    private Bitmap applyBlurToMask(Bitmap bitmap) {
        // Simple blur implementation
        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setMaskFilter(new BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return result;
    }

    private List<String> getDetectedItems(float[][][] output) {
        List<String> detectedItems = new ArrayList<>();

        // Count pixels for each class to see which ones are present
        int[] pixelCounts = new int[18];
        int totalPixels = 128 * 128;

        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                // Find class with highest probability for this pixel
                float maxProb = 0;
                int maxClass = 0;
                for (int cls = 0; cls < 18; cls++) {
                    if (output[cls][y][x] > maxProb) {
                        maxProb = output[cls][y][x];
                        maxClass = cls;
                    }
                }
                pixelCounts[maxClass]++;
            }
        }

        // Debug: Print all class percentages
        for (int cls = 0; cls < 18; cls++) {
            double percentage = (pixelCounts[cls] * 100.0) / totalPixels;
            if (percentage > 0.1) { // Only log significant classes
                Log.d(TAG, String.format("Class %2d (%s): %.2f%%",
                        cls, CLASS_NAMES[cls], percentage));
            }
        }

        // Consider a class "detected" if it has more than 0.5% of pixels (lowered threshold)
        for (int cls : CLOTHING_CLASSES) {
            double percentage = (pixelCounts[cls] * 100.0) / totalPixels;
            if (percentage > 0.5) { // Lowered threshold from 1.0% to 0.5%
                detectedItems.add(CLASS_NAMES[cls]);
                Log.d(TAG, "Detected: " + CLASS_NAMES[cls] + " (" + percentage + "%)");
            }
        }

        return detectedItems;
    }

    private void debugOutput(float[][][] output) {
        // Print some output values to understand the range
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        for (int cls = 0; cls < 18; cls++) {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    float val = output[cls][y][x];
                    if (val < min) min = val;
                    if (val > max) max = val;
                }
            }
        }

        Log.d(TAG, "Output range: min=" + min + ", max=" + max);

        // Check if we have any non-zero values in clothing classes
        for (int cls : CLOTHING_CLASSES) {
            int nonZeroCount = 0;
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    if (output[cls][y][x] > 0.1f) {
                        nonZeroCount++;
                    }
                }
            }
            Log.d(TAG, "Class " + cls + " non-zero values: " + nonZeroCount);
        }
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }
}