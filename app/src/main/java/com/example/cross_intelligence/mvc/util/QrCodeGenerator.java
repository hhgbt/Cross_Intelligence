package com.example.cross_intelligence.mvc.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具类
 * 使用 ZXing 库生成二维码图片
 */
public class QrCodeGenerator {

    private static final int DEFAULT_SIZE = 512; // 默认二维码尺寸
    private static final int DEFAULT_COLOR_BLACK = Color.BLACK;
    private static final int DEFAULT_COLOR_WHITE = Color.WHITE;

    /**
     * 根据字符串内容生成二维码 Bitmap
     *
     * @param content 二维码内容
     * @return 二维码 Bitmap，失败返回 null
     */
    @Nullable
    public static Bitmap generateQrCode(@NonNull String content) {
        return generateQrCode(content, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    /**
     * 根据字符串内容生成指定尺寸的二维码 Bitmap
     *
     * @param content 二维码内容
     * @param width   二维码宽度
     * @param height  二维码高度
     * @return 二维码 Bitmap，失败返回 null
     */
    @Nullable
    public static Bitmap generateQrCode(@NonNull String content, int width, int height) {
        if (content.isEmpty()) {
            return null;
        }

        try {
            // 配置二维码参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // 边距

            // 生成二维码矩阵
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            // 将 BitMatrix 转换为 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? DEFAULT_COLOR_BLACK : DEFAULT_COLOR_WHITE);
                }
            }

            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 为打卡点生成二维码 Payload（JSON 格式）
     *
     * @param raceId      赛事ID
     * @param checkPointId 打卡点ID
     * @return JSON 字符串，例如：{"raceId":"xxx","cpId":"xxx"}
     */
    @NonNull
    public static String generateCheckPointPayload(@NonNull String raceId, @NonNull String checkPointId) {
        try {
            JSONObject json = new JSONObject();
            json.put("raceId", raceId);
            json.put("cpId", checkPointId);
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            // 降级方案：返回简单拼接字符串
            return "raceId:" + raceId + ",cpId:" + checkPointId;
        }
    }

    /**
     * 为打卡点生成二维码 Bitmap
     *
     * @param raceId      赛事ID
     * @param checkPointId 打卡点ID
     * @return 二维码 Bitmap，失败返回 null
     */
    @Nullable
    public static Bitmap generateCheckPointQrCode(@NonNull String raceId, @NonNull String checkPointId) {
        String payload = generateCheckPointPayload(raceId, checkPointId);
        return generateQrCode(payload);
    }

    /**
     * 为打卡点生成指定尺寸的二维码 Bitmap
     *
     * @param raceId      赛事ID
     * @param checkPointId 打卡点ID
     * @param width       二维码宽度
     * @param height      二维码高度
     * @return 二维码 Bitmap，失败返回 null
     */
    @Nullable
    public static Bitmap generateCheckPointQrCode(@NonNull String raceId, @NonNull String checkPointId, 
                                                   int width, int height) {
        String payload = generateCheckPointPayload(raceId, checkPointId);
        return generateQrCode(payload, width, height);
    }
}


