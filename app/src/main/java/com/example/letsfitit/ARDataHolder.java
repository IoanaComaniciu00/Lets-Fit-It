package com.example.letsfitit;

import android.graphics.Bitmap;
import java.util.List;

public class ARDataHolder {
    private static Bitmap segmentedBitmap;
    private static List<NetworkService.ClothingItem> detectedItems;
    private static String primaryClothingType;

    public static void setSegmentedBitmap(Bitmap bitmap) {
        segmentedBitmap = bitmap;
    }

    public static Bitmap getSegmentedBitmap() {
        return segmentedBitmap;
    }

    public static void setDetectedItems(List<NetworkService.ClothingItem> items) {
        detectedItems = items;

        primaryClothingType = determinePrimaryClothingType();
    }

    public static List<NetworkService.ClothingItem> getDetectedItems() {
        return detectedItems;
    }

    public static String getClothingType() {
        return primaryClothingType;
    }

    public static void setPrimaryClothingType(String type) {
        primaryClothingType = type;
    }

    private static String determinePrimaryClothingType() {
        if (detectedItems == null || detectedItems.isEmpty()) {
            return "unknown";
        }


        NetworkService.ClothingItem primaryItem = null;
        double highestConfidence = 0.0;

        for (NetworkService.ClothingItem item : detectedItems) {
            if (item.getScore() > highestConfidence) {
                highestConfidence = item.getScore();
                primaryItem = item;
            }
        }

        if (primaryItem == null) {
            return "unknown";
        }

        String label = primaryItem.getLabel().toLowerCase();

        if (label.contains("pants") || label.contains("trousers") ||
                label.contains("jeans") || label.contains("leggings")) {
            return "pants";
        } else if (label.contains("shirt") || label.contains("t-shirt") ||
                label.contains("blouse") || label.contains("top") ||
                label.contains("upper-clothes") || label.contains("hoodie")) {
            return "shirt";
        } else if (label.contains("dress") || label.contains("gown")) {
            return "dress";
        } else if (label.contains("skirt")) {
            return "skirt";
        } else if (label.contains("coat") || label.contains("jacket")) {
            return "jacket";
        } else if (label.contains("shorts")) {
            return "shorts";
        } else {
            return "unknown";
        }
    }

    public static void clear() {
        segmentedBitmap = null;
        detectedItems = null;
        primaryClothingType = null;
    }
}