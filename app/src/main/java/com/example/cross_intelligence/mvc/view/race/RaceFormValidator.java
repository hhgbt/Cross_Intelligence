package com.example.cross_intelligence.mvc.view.race;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.cross_intelligence.mvc.model.CheckPoint;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class RaceFormValidator {

    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    private RaceFormValidator() {
    }

    static ValidationResult validate(String raceName,
                                     String startTime,
                                     String endTime,
                                     List<CheckPoint> points) {
        if (TextUtils.isEmpty(raceName)) {
            return ValidationResult.error("请输入赛事名称");
        }
        if (TextUtils.isEmpty(startTime) || TextUtils.isEmpty(endTime)) {
            return ValidationResult.error("请输入开始和结束时间");
        }

        Date start;
        Date end;
        try {
            start = FORMAT.parse(startTime);
            end = FORMAT.parse(endTime);
        } catch (ParseException e) {
            return ValidationResult.error("时间格式应为 yyyy-MM-dd HH:mm");
        }

        if (start == null || end == null || !end.after(start)) {
            return ValidationResult.error("结束时间必须晚于开始时间");
        }
        if (points == null || points.size() < 2) {
            return ValidationResult.error("请至少添加两个打卡点");
        }
        return ValidationResult.success(start, end);
    }

    static final class ValidationResult {
        private final boolean valid;
        private final String message;
        private final Date start;
        private final Date end;

        private ValidationResult(boolean valid, String message, Date start, Date end) {
            this.valid = valid;
            this.message = message;
            this.start = start;
            this.end = end;
        }

        static ValidationResult success(Date start, Date end) {
            return new ValidationResult(true, null, start, end);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message, null, null);
        }

        boolean isValid() {
            return valid;
        }

        @Nullable
        String getMessage() {
            return message;
        }

        Date getStart() {
            return start;
        }

        Date getEnd() {
            return end;
        }
    }
}





