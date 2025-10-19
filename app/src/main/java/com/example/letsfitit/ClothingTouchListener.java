package com.example.letsfitit;

import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

public class ClothingTouchListener implements View.OnTouchListener {
    private int lastX, lastY;
    private float startDist;
    private int startWidth, startHeight;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastX = (int) event.getRawX();
                lastY = (int) event.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1) {

                    int dx = (int) event.getRawX() - lastX;
                    int dy = (int) event.getRawY() - lastY;

                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) v.getLayoutParams();
                    params.leftMargin += dx;
                    params.topMargin += dy;
                    v.setLayoutParams(params);

                    lastX = (int) event.getRawX();
                    lastY = (int) event.getRawY();
                } else if (event.getPointerCount() == 2) {

                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        float scale = newDist / startDist;

                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) v.getLayoutParams();
                        params.width = (int) (startWidth * scale);
                        params.height = (int) (startHeight * scale);
                        v.setLayoutParams(params);
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                startDist = spacing(event);
                startWidth = v.getWidth();
                startHeight = v.getHeight();
                break;
        }
        return true;
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
}