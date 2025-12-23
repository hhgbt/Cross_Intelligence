package com.example.cross_intelligence.mvc.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 二维码 Payload 解析工具类
 * 解析格式：{"raceId":"xxx","cpId":"xxx"}
 */
public class QrPayloadParser {

    /**
     * 解析结果类
     */
    public static class ParseResult {
        private final boolean success;
        private final String raceId;
        private final String cpId;
        private final String errorMessage;

        private ParseResult(boolean success, String raceId, String cpId, String errorMessage) {
            this.success = success;
            this.raceId = raceId;
            this.cpId = cpId;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getRaceId() {
            return raceId;
        }

        public String getCpId() {
            return cpId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public static ParseResult success(String raceId, String cpId) {
            return new ParseResult(true, raceId, cpId, null);
        }

        public static ParseResult failure(String errorMessage) {
            return new ParseResult(false, null, null, errorMessage);
        }
    }

    /**
     * 解析二维码内容
     *
     * @param qrContent 二维码内容（JSON 格式）
     * @return 解析结果
     */
    @NonNull
    public static ParseResult parse(@Nullable String qrContent) {
        if (qrContent == null || qrContent.isEmpty()) {
            return ParseResult.failure("二维码内容为空");
        }

        try {
            JSONObject json = new JSONObject(qrContent);
            
            // 检查必需字段
            if (!json.has("raceId") || !json.has("cpId")) {
                return ParseResult.failure("二维码格式错误：缺少必需字段");
            }

            String raceId = json.getString("raceId");
            String cpId = json.getString("cpId");

            // 验证字段内容
            if (raceId == null || raceId.isEmpty()) {
                return ParseResult.failure("赛事ID为空");
            }
            if (cpId == null || cpId.isEmpty()) {
                return ParseResult.failure("打卡点ID为空");
            }

            return ParseResult.success(raceId, cpId);
        } catch (JSONException e) {
            return ParseResult.failure("二维码格式错误：" + e.getMessage());
        }
    }

    /**
     * 验证二维码是否匹配指定的赛事和打卡点
     *
     * @param qrContent      二维码内容
     * @param expectedRaceId 期望的赛事ID
     * @param expectedCpId   期望的打卡点ID
     * @return 是否匹配
     */
    public static boolean validate(@Nullable String qrContent, @NonNull String expectedRaceId, @NonNull String expectedCpId) {
        ParseResult result = parse(qrContent);
        if (!result.isSuccess()) {
            return false;
        }
        return expectedRaceId.equals(result.getRaceId()) && expectedCpId.equals(result.getCpId());
    }
}










