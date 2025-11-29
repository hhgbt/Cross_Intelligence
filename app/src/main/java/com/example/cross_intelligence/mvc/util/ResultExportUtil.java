package com.example.cross_intelligence.mvc.util;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.example.cross_intelligence.mvc.model.Result;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 成绩导出工具：生成 CSV 内容并写入缓存文件。
 */
public final class ResultExportUtil {

    private ResultExportUtil() {
    }

    public static String buildCsvContent(List<Result> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("排名,用户ID,耗时(秒),罚时(秒),总计(秒),状态\n");
        for (Result result : results) {
            builder.append(result.getRank()).append(",")
                    .append(result.getUserId()).append(",")
                    .append(result.getElapsedSeconds()).append(",")
                    .append(result.getPenaltySeconds()).append(",")
                    .append(result.getTotalSeconds()).append(",")
                    .append(result.getStatus()).append("\n");
        }
        return builder.toString();
    }

    public static Uri exportToFile(Context context, List<Result> results, String fileName) throws IOException {
        String csv = buildCsvContent(results);
        File dir = new File(context.getFilesDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建导出目录: " + dir.getAbsolutePath());
        }
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(csv.getBytes(StandardCharsets.UTF_8));
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }
}


