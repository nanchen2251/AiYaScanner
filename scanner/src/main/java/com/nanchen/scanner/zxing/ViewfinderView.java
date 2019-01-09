/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nanchen.scanner.zxing;

import com.google.zxing.ResultPoint;
import com.nanchen.scanner.R;
import com.nanchen.scanner.zxing.camera.CameraManager;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 6;

    private CameraManager cameraManager;
    private final Paint paint;
    private final int maskColor;
    private final int laserColor;
    private final int resultPointColor;
    private int scannerAlpha;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;
    private int mCornerBarW;
    private int mCornerBarH;
    private int mCornerBarColor;
    private int mLaserH;
    private int testColor;
    private int mLaserPos;
    private ValueAnimator mAnimator;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        laserColor = resources.getColor(R.color.viewfinder_laser);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        mCornerBarW = resources.getDimensionPixelSize(R.dimen.scan_frame_corner_w);
        mCornerBarH = resources.getDimensionPixelSize(R.dimen.scan_frame_corner_h);
        mCornerBarColor = resources.getColor(R.color.viewfinder_corner_bar);
        testColor = resources.getColor(R.color.viewfinder_corner_bar2);
        mLaserH = resources.getDimensionPixelSize(R.dimen.laser_bar_h);
        scannerAlpha = 0;
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;
        mAnimator = null;
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        Rect frame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
        /** FIXME:调试绘制代码
         paint.setColor(testColor);
         canvas.drawRect(previewFrame, paint);
         */

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);
        // 画扫描框的4个角
        paint.setColor(mCornerBarColor);
        // 左上角
        canvas.drawRect(
                frame.left - mCornerBarH,
                frame.top - mCornerBarH,
                frame.left - mCornerBarH + mCornerBarW,
                frame.top,
                paint);
        canvas.drawRect(
                frame.left - mCornerBarH,
                frame.top,
                frame.left,
                frame.top + mCornerBarW - mCornerBarH,
                paint);
        // 右上角
        canvas.drawRect(
                frame.right - mCornerBarW + mCornerBarH,
                frame.top - mCornerBarH,
                frame.right,
                frame.top,
                paint);
        canvas.drawRect(
                frame.right,
                frame.top - mCornerBarH,
                frame.right + mCornerBarH,
                frame.top + mCornerBarW - mCornerBarH,
                paint);
        // 左下角
        canvas.drawRect(
                frame.left - mCornerBarH,
                frame.bottom - mCornerBarW + mCornerBarH,
                frame.left,
                frame.bottom,
                paint);
        canvas.drawRect(
                frame.left - mCornerBarH,
                frame.bottom,
                frame.left - mCornerBarH + mCornerBarW,
                frame.bottom + mCornerBarH,
                paint);
        // 右下角
        canvas.drawRect(
                frame.right - mCornerBarW + mCornerBarH,
                frame.bottom,
                frame.right + mCornerBarH,
                frame.bottom + mCornerBarH,
                paint);
        canvas.drawRect(
                frame.right,
                frame.bottom - mCornerBarW + mCornerBarH,
                frame.right + mCornerBarH,
                frame.bottom,
                paint);

        // Draw a red "laser scanner" line through the middle to show decoding is active
        paint.setColor(laserColor);
//    paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
//    scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
        int middle = frame.height() / 2 + frame.top;
        if (mAnimator == null) {
            initAnimator(frame);
        }
//      int step = 4;
//    mLaserPos = mLaserPos == 0 ? frame.top : mLaserPos + step;
//      mLaserPos = mLaserPos > frame.bottom ? frame.top : mLaserPos + step;
//    canvas.drawRect(frame.left, middle - mLaserH / 2, frame.right, middle + mLaserH / 2, paint);
        canvas.drawRect(frame.left, mLaserPos - mLaserH / 2, frame.right, mLaserPos + mLaserH / 2, paint);

        float scaleX = frame.width() / (float) previewFrame.width();
        float scaleY = frame.height() / (float) previewFrame.height();

        List<ResultPoint> currentPossible = possibleResultPoints;
        List<ResultPoint> currentLast = lastPossibleResultPoints;
        int frameLeft = frame.left;
        int frameTop = frame.top;
        if (currentPossible.isEmpty()) {
            lastPossibleResultPoints = null;
        } else {
            possibleResultPoints = new ArrayList<>(5);
            lastPossibleResultPoints = currentPossible;
            paint.setAlpha(CURRENT_POINT_OPACITY);
            paint.setColor(resultPointColor);
            synchronized (currentPossible) {
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(
                            frameLeft + (int) (point.getX() * scaleX),
                            frameTop + (int) (point.getY() * scaleY),
                            POINT_SIZE,
                            paint);
                }
            }
        }
        if (currentLast != null) {
            paint.setAlpha(CURRENT_POINT_OPACITY / 2);
            paint.setColor(resultPointColor);
            synchronized (currentLast) {
                float radius = POINT_SIZE / 2.0f;
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(
                            frameLeft + (int) (point.getX() * scaleX),
                            frameTop + (int) (point.getY() * scaleY),
                            radius,
                            paint);
                }
            }
        }

        // Request another update at the animation interval, but only repaint the laser line,
        // not the entire viewfinder mask.
        postInvalidateDelayed(
                ANIMATION_DELAY,
                frame.left - POINT_SIZE,
                frame.top - POINT_SIZE,
                frame.right + POINT_SIZE,
                frame.bottom + POINT_SIZE);
    }

    private void initAnimator(Rect frame) {
        mAnimator = ValueAnimator.ofInt(frame.top, frame.bottom);
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimator.setDuration(2500);
        mAnimator.setRepeatMode(ValueAnimator.RESTART);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mLaserPos = (int) animation.getAnimatedValue();
                invalidate();
            }
        });
        mAnimator.start();
    }

    public void drawViewfinder() {
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

}
