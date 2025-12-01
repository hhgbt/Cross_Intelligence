package com.example.cross_intelligence.mvc.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.integration.android.IntentResult;

/**
 * ZXing 结果辅助工具，便于单元测试。
 */
public final class QrScannerHelper {

    private QrScannerHelper() {
    }

    public static boolean validatePayload(@NonNull String expected, @Nullable IntentResult result) {
        if (result == null || result.getContents() == null) {
            return false;
        }
        return expected.equals(result.getContents());
    }
}





