package com.example.cross_intelligence.mvc.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.MapView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 地图缩略图生成工具类
 * 用于在保存赛事时自动生成轨迹缩略图
 */
public class MapThumbnailUtil {

    /**
     * 截图 MapView 并保存到本地
     *
     * @param context 上下文
     * @param mapView 地图视图
     * @param raceId  赛事ID
     * @return 缩略图文件路径，失败返回 null
     */
    @Nullable
    public static String captureAndSaveThumbnail(@NonNull Context context,
                                                  @NonNull MapView mapView,
                                                  @NonNull String raceId) {
        try {
            // 截图 MapView
            Bitmap bitmap = captureMapView(mapView);
            if (bitmap == null) {
                return null;
            }

            // 保存到本地
            String filePath = saveThumbnail(context, raceId, bitmap);
            bitmap.recycle();
            return filePath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 截图 MapView
     */
    @Nullable
    private static Bitmap captureMapView(@NonNull MapView mapView) {
        try {
            // 获取 MapView 的宽高
            int width = mapView.getWidth();
            int height = mapView.getHeight();

            // 如果宽高为0，使用默认尺寸
            if (width <= 0 || height <= 0) {
                width = 800;
                height = 600;
            }

            // 创建 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            mapView.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 保存缩略图到本地文件
     *
     * @param context 上下文
     * @param raceId  赛事ID
     * @param bitmap  缩略图 Bitmap
     * @return 文件路径
     */
    @Nullable
    private static String saveThumbnail(@NonNull Context context,
                                         @NonNull String raceId,
                                         @NonNull Bitmap bitmap) {
        try {
            // 保存到应用的内部存储
            File thumbnailsDir = new File(context.getFilesDir(), "race_thumbnails");
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs();
            }

            File thumbnailFile = new File(thumbnailsDir, raceId + "_thumbnail.png");
            try (FileOutputStream fos = new FileOutputStream(thumbnailFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.flush();
            }

            return thumbnailFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 加载缩略图
     *
     * @param context 上下文
     * @param filePath 文件路径
     * @return Bitmap，失败返回 null
     */
    @Nullable
    public static Bitmap loadThumbnail(@NonNull Context context, @NonNull String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && file.canRead()) {
                return android.graphics.BitmapFactory.decodeFile(filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 从 Bitmap 保存缩略图到本地文件
     * 用于高德地图截图后的保存
     *
     * @param context 上下文
     * @param raceId  赛事ID
     * @param bitmap  缩略图 Bitmap
     * @return 缩略图文件路径，失败返回 null
     */
    @Nullable
    public static String saveThumbnailFromBitmap(@NonNull Context context,
                                                  @NonNull String raceId,
                                                  @NonNull Bitmap bitmap) {
        try {
            // 增加安全检查
            if (bitmap == null || bitmap.isRecycled()) {
                android.util.Log.e("MapThumbnailUtil", "Bitmap is null or recycled, cannot save.");
                return null;
            }

            // 保存到应用的内部存储
            File thumbnailsDir = new File(context.getFilesDir(), "race_thumbnails");
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs();
            }

            File thumbnailFile = new File(thumbnailsDir, raceId + "_thumbnail.png");
            try (FileOutputStream fos = new FileOutputStream(thumbnailFile)) {
                // 再次检查，防止在 IO 准备期间 bitmap 被回收
                if (bitmap.isRecycled()) {
                     android.util.Log.e("MapThumbnailUtil", "Bitmap recycled before compression.");
                     return null;
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.flush();
            }

            return thumbnailFile.getAbsolutePath();
        } catch (Exception e) { // 捕获所有异常，包括 IllegalStateException
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 删除缩略图
     *
     * @param filePath 文件路径
     * @return 是否删除成功
     */
    public static boolean deleteThumbnail(@NonNull String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

