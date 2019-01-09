package com.nanchen.scanner.module;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.nanchen.scanner.R;
import com.nanchen.scanner.utils.PermissionConstants;
import com.nanchen.scanner.utils.PermissionUtils;
import com.nanchen.scanner.utils.QRUtils;
import com.nanchen.scanner.zxing.BaseCaptureActivity;
import com.nanchen.scanner.zxing.BeepManager;
import com.nanchen.scanner.zxing.CaptureActivityHandler;
import com.nanchen.scanner.zxing.DecodeFormatManager;
import com.nanchen.scanner.zxing.DecodeHintManager;
import com.nanchen.scanner.zxing.FinishListener;
import com.nanchen.scanner.zxing.InactivityTimer;
import com.nanchen.scanner.zxing.Intents;
import com.nanchen.scanner.zxing.ViewfinderView;
import com.nanchen.scanner.zxing.camera.CameraManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author nanchen
 * 拍照真正的 Activity，实际业务可以自己编写 UI
 */
public class CaptureActivity extends BaseCaptureActivity implements View.OnClickListener {
    private static final String TAG = CaptureActivity.class.getSimpleName();
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private SurfaceView surfaceView;
    private boolean hasSurface;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private static final int REQUEST_IMAGE_GET = 1001;

    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType, ?> decodeHints;
    private String characterSet;
    private boolean torchOpen = false;
    private TextView tvTitle;

    public static void startForResult(final Activity activity, final int requestCode) {
        PermissionUtils.permission(activity, PermissionConstants.CAMERA, PermissionConstants.STORAGE)
                .rationale(new PermissionUtils.OnRationaleListener() {
                    @Override
                    public void rationale(final ShouldRequest shouldRequest) {
                        shouldRequest.again(true);
                    }
                })
                .callback(new PermissionUtils.FullCallback() {
                    @Override
                    public void onGranted(List<String> permissionsGranted) {
                        Intent intent = new Intent(activity.getApplicationContext(), CaptureActivity.class);
                        activity.startActivityForResult(intent, requestCode);
                    }

                    @Override
                    public void onDenied(List<String> permissionsDeniedForever,
                                         List<String> permissionsDenied) {
                        Toast.makeText(activity.getApplicationContext(), "摄像头权限被拒绝！", Toast.LENGTH_SHORT).show();

                    }
                }).request();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 保持屏幕常亮
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capture);
        initView();
    }

    private void initView() {
        findViewById(R.id.ivBack).setOnClickListener(this);
        findViewById(R.id.tvGallery).setOnClickListener(this);
        findViewById(R.id.tvFlash).setOnClickListener(this);

        viewfinderView = findViewById(R.id.viewfinderView);
        surfaceView = findViewById(R.id.surfaceView);
        tvTitle = findViewById(R.id.tvTitle);

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.ivBack) {
            finish();

        } else if (i == R.id.tvFlash) {
            clickTorch();

        } else if (i == R.id.tvGallery) {
            openGallery();

        }
    }

    private void openGallery() {
        Intent intentToPickPic = new Intent(Intent.ACTION_PICK, null);
        // 如果限制上传到服务器的图片类型时可以直接写如："image/jpeg 、 image/png等的类型" 所有类型则写 "image/*"
        intentToPickPic.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg");
        startActivityForResult(intentToPickPic, REQUEST_IMAGE_GET);
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }


    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.common_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    private void resetStatusView() {
        viewfinderView.setVisibility(View.VISIBLE);
    }

    public void clickTorch() {
        torchOpen = !torchOpen;
        if (cameraManager != null) {
            cameraManager.setTorch(torchOpen);
        }
    }

    @Override
    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

    @Override
    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();

        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
            // Then not from history, so beep/vibrate and we have an image to draw on
            beepManager.playBeepSoundAndVibrate();
        }
        doParseResult(rawResult.getText());
    }

    private void doParseResult(String result) {
        Intent data = new Intent();
        data.putExtra("result", result);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void drawViewfinder() {

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    private int getCurrentOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switch (rotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                default:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            }
        } else {
            switch (rotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_270:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                default:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to pick the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());

        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        setRequestedOrientation(getCurrentOrientation());

        resetStatusView();


        beepManager.updatePrefs();
        //        ambientLightManager.startForResult(cameraManager);

        inactivityTimer.onResume();

        Intent intent = getIntent();

        decodeFormats = null;
        characterSet = null;

        if (intent != null) {
            String action = intent.getAction();

            if (Intents.Scan.ACTION.equals(action)) {

                // Scan the formats the intent requested, and return the result to the calling activity.
//                decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
                // 仅仅支持二维码
                decodeFormats = DecodeFormatManager.onlyQrCode();
                decodeHints = DecodeHintManager.parseDecodeHints(intent);

                if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
                    int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
                    int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
                    if (width > 0 && height > 0) {
                        cameraManager.setManualFramingRect(width, height);
                    }
                }

                if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
                    int cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1);
                    if (cameraId >= 0) {
                        cameraManager.setManualCameraId(cameraId);
                    }
                }
            }

            characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);

        }

        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback(this);
        }
    }


    @Override
    public void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        //        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();
        //historyManager = null; // Keep for onActivityResult
        if (!hasSurface) {
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    Bitmap bitmap;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_GET && resultCode == RESULT_OK && data.getData() != null) {
            Uri uri = data.getData();
            bitmap = null;
            try {
                bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            } catch (FileNotFoundException e) {
                bitmap = null;
            }
            if (bitmap == null) {
                Toast.makeText(getApplicationContext(), "获取图片失败", Toast.LENGTH_SHORT).show();
                return;
            }
            showProgressDialog("请稍后...");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String result = QRUtils.getInstance().decodeQRcodeByZxing(bitmap);
                    if (!TextUtils.isEmpty(result)) {
                        closeProgressDialog();
                        doParseResult(result);
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(CaptureActivity.this.getApplicationContext(), "识别失败！", Toast.LENGTH_SHORT).show();
                            }
                        });
                        closeProgressDialog();
                    }
                }
            }).start();
        }
    }

    private AlertDialog progressDialog;

    public void showProgressDialog(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
        builder.setCancelable(false);
        View view = View.inflate(this, R.layout.dialog_loading, null);
        builder.setView(view);
        ProgressBar pb_loading = view.findViewById(R.id.pb_loading);
        TextView tv_hint = view.findViewById(R.id.tv_hint);
        tv_hint.setText(msg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pb_loading.setIndeterminateTintList(ContextCompat.getColorStateList(this, R.color.dialog_pro_color));
        }
        progressDialog = builder.create();
        progressDialog.show();
    }

    public void closeProgressDialog() {
        try {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
