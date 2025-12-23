package com.example.cross_intelligence.mvc.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 二维码保存和分享工具类
 */
public class QrCodeUtil {

    /**
     * 保存二维码图片到相册
     *
     * @param context  上下文
     * @param bitmap   二维码 Bitmap
     * @param fileName 文件名（不含扩展名）
     * @return 是否保存成功
     */
    public static boolean saveQrCodeToGallery(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String fileName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 及以上使用 MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CrossIntelligence");

                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                        if (outputStream != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            return true;
                        }
                    }
                }
            } else {
                // Android 9 及以下使用传统方式
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File appDir = new File(picturesDir, "CrossIntelligence");
                if (!appDir.exists()) {
                    appDir.mkdirs();
                }

                File imageFile = new File(appDir, fileName + ".png");
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();

                    // 通知相册更新
                    context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imageFile)));
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 分享二维码图片
     *
     * @param context  上下文
     * @param bitmap   二维码 Bitmap
     * @param fileName 文件名（不含扩展名）
     */
    public static void shareQrCode(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String fileName) {
        try {
            // 保存到缓存目录
            File cachePath = new File(context.getCacheDir(), "qrcodes");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }

            File imageFile = new File(cachePath, fileName + ".png");
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            }

            // 使用 FileProvider 获取 Uri
            Uri imageUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    imageFile
            );

            // 创建分享 Intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(shareIntent, "分享二维码"));
        } catch (IOException e) {
            e.printStackTrace();
            UIUtil.showToast(context, "分享失败：" + e.getMessage());
        }
    }

    /**
     * 生成二维码文件名
     *
     * @param checkPointName 打卡点名称
     * @return 文件名
     */
    @NonNull
    public static String generateQrCodeFileName(@NonNull String checkPointName) {
        // 移除文件名中的非法字符
        String sanitized = checkPointName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return "QR_" + sanitized + "_" + System.currentTimeMillis();
    }
}










