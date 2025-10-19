
package com.example.letsfitit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PoseOverlayView extends View {
    private final Paint keypointPaint;
    private final Paint linePaint;
    private final Paint textPaint;
    private final List<float[]> keypoints = new ArrayList<>();

    // Define pose connections (MediaPipe Pose landmarks)
    private static final int[][] POSE_CONNECTIONS = {
            // Face oval (simplified)
            {0, 1}, {1, 2}, {2, 3}, {3, 7},
            // Left arm
            {11, 13}, {13, 15},
            // Right arm
            {12, 14}, {14, 16},
            // Torso
            {11, 12}, {11, 23}, {12, 24}, {23, 24},
            // Left leg
            {23, 25}, {25, 27}, {27, 29}, {29, 31},
            // Right leg
            {24, 26}, {26, 28}, {28, 30}, {30, 32}
    };

    // Important landmark indices for labeling
    private static final int[] IMPORTANT_LANDMARKS = {11, 12, 23, 24};
    private static final String[] LANDMARK_NAMES = {
            "L Shoulder", "R Shoulder", "L Hip", "R Hip"
    };

    public PoseOverlayView(Context context) {
        super(context);
        keypointPaint = initKeypointPaint();
        linePaint = initLinePaint();
        textPaint = initTextPaint();
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        keypointPaint = initKeypointPaint();
        linePaint = initLinePaint();
        textPaint = initTextPaint();
    }

    public PoseOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        keypointPaint = initKeypointPaint();
        linePaint = initLinePaint();
        textPaint = initTextPaint();
    }

    private Paint initKeypointPaint() {
        Paint p = new Paint();
        p.setColor(Color.RED);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(15f);
        p.setAntiAlias(true);
        return p;
    }

    private Paint initLinePaint() {
        Paint p = new Paint();
        p.setColor(Color.GREEN);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8f);
        p.setAntiAlias(true);
        return p;
    }

    private Paint initTextPaint() {
        Paint p = new Paint();
        p.setColor(Color.YELLOW);
        p.setTextSize(28f);
        p.setAntiAlias(true);
        p.setFakeBoldText(true);
        return p;
    }


    public void setKeypoints(List<float[]> newKeypoints) {
        keypoints.clear();
        if (newKeypoints != null) keypoints.addAll(newKeypoints);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        for (int[] connection : POSE_CONNECTIONS) {
            if (connection.length >= 2 &&
                    connection[0] < keypoints.size() &&
                    connection[1] < keypoints.size()) {

                float[] point1 = keypoints.get(connection[0]);
                float[] point2 = keypoints.get(connection[1]);

                if (point1.length >= 2 && point2.length >= 2) {
                    canvas.drawLine(point1[0], point1[1], point2[0], point2[1], linePaint);
                }
            }
        }


        for (int i = 0; i < keypoints.size(); i++) {
            float[] point = keypoints.get(i);
            if (point.length >= 2) {

                canvas.drawCircle(point[0], point[1], 12f, keypointPaint);

                for (int j = 0; j < IMPORTANT_LANDMARKS.length; j++) {
                    if (i == IMPORTANT_LANDMARKS[j]) {
                        String label = IMPORTANT_LANDMARKS[j] + ": " + LANDMARK_NAMES[j];
                        canvas.drawText(label, point[0] + 20, point[1] - 20, textPaint);
                        break;
                    }
                }
            }
        }
    }
}