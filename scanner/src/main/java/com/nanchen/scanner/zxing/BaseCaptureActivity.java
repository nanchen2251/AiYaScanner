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

import com.google.zxing.Result;
import com.nanchen.scanner.zxing.camera.CameraManager;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.SurfaceHolder;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public abstract class BaseCaptureActivity extends Activity implements SurfaceHolder.Callback {

    public abstract ViewfinderView getViewfinderView();

    public abstract Handler getHandler();

    public abstract CameraManager getCameraManager();

    public abstract void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor);

    public abstract void drawViewfinder();
}
