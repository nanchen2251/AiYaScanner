package com.nanchen.scanner.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.Hashtable;


public class QRUtils {
    private static QRUtils instance;


    public static QRUtils getInstance() {
        if (instance == null)
            instance = new QRUtils();
        return instance;
    }

    /**
     * 扫描二维码图片的方法
     */
    public String decodeQRcodeByZxing(Bitmap scanBitmap) {
        if (scanBitmap == null) {
            return null;
        }
        Hashtable<DecodeHintType, String> hints = new Hashtable<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8"); // 设置二维码内容的编码
        int[] data = new int[scanBitmap.getWidth() * scanBitmap.getHeight()];
        scanBitmap.getPixels(data, 0, scanBitmap.getWidth(), 0, 0, scanBitmap.getWidth(), scanBitmap.getHeight());
        RGBLuminanceSource rgbLuminanceSource = new RGBLuminanceSource(scanBitmap.getWidth(), scanBitmap.getHeight(), data);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(rgbLuminanceSource));
        QRCodeReader reader = new QRCodeReader();
        try {
            Result result = reader.decode(binaryBitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            return "";
        } catch (ChecksumException e) {
            return "";
        } catch (FormatException e) {
            return "";
        }

    }
}
