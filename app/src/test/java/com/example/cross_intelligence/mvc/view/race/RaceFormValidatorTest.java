package com.example.cross_intelligence.mvc.view.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.cross_intelligence.mvc.model.CheckPoint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RaceFormValidatorTest {

    @Test
    public void validate_success() {
        List<CheckPoint> points = mockPoints(2);
        RaceFormValidator.ValidationResult result = RaceFormValidator.validate(
                "晨练赛", "2025-01-01 09:00", "2025-01-01 11:00", points);
        assertTrue(result.isValid());
        assertNotNull(result.getStart());
        assertNotNull(result.getEnd());
    }

    @Test
    public void validate_missingName_error() {
        RaceFormValidator.ValidationResult result = RaceFormValidator.validate(
                "", "2025-01-01 09:00", "2025-01-01 11:00", mockPoints(2));
        assertFalse(result.isValid());
        assertEquals("请输入赛事名称", result.getMessage());
    }

    @Test
    public void validate_invalidTimeFormat_error() {
        RaceFormValidator.ValidationResult result = RaceFormValidator.validate(
                "晨练赛", "2025/01/01", "2025-01-01 11:00", mockPoints(2));
        assertFalse(result.isValid());
        assertEquals("时间格式应为 yyyy-MM-dd HH:mm", result.getMessage());
    }

    @Test
    public void validate_endBeforeStart_error() {
        RaceFormValidator.ValidationResult result = RaceFormValidator.validate(
                "晨练赛", "2025-01-01 11:00", "2025-01-01 09:00", mockPoints(2));
        assertFalse(result.isValid());
        assertEquals("结束时间必须晚于开始时间", result.getMessage());
    }

    @Test
    public void validate_pointsSize_error() {
        RaceFormValidator.ValidationResult result = RaceFormValidator.validate(
                "晨练赛", "2025-01-01 09:00", "2025-01-01 11:00", mockPoints(1));
        assertFalse(result.isValid());
        assertEquals("请至少添加两个打卡点", result.getMessage());
    }

    private List<CheckPoint> mockPoints(int count) {
        List<CheckPoint> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CheckPoint point = new CheckPoint();
            point.setName("P" + i);
            point.setLatitude(30 + i);
            point.setLongitude(120 + i);
            list.add(point);
        }
        return list;
    }
}





