package com.example.letsfitit;

import android.graphics.Bitmap;
import java.util.List;

public class ARDataHolder {
    private static Bitmap segmentedBitmap;
    private static List<NetworkService.ClothingItem> detectedItems;

    public static void setSegmentedBitmap(Bitmap bitmap) {
        segmentedBitmap = bitmap;
    }

    public static Bitmap getSegmentedBitmap() {
        return segmentedBitmap;
    }

    public static void setDetectedItems(List<NetworkService.ClothingItem> items) {
        detectedItems = items;
    }

    public static List<NetworkService.ClothingItem> getDetectedItems() {
        return detectedItems;
    }

    public static void clear() {
        segmentedBitmap = null;
        detectedItems = null;
    }
}