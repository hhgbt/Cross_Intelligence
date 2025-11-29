package com.example.cross_intelligence.mvc.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DistanceUtilTest {

    @Test
    public void distanceMeters_samePoint_zero() {
        double distance = DistanceUtil.distanceMeters(30.0, 120.0, 30.0, 120.0);
        assertEquals(0, distance, 0.0001);
    }

    @Test
    public void distanceMeters_knownValue() {
        // 北京天安门 (39.9087,116.3975) 到 故宫神武门 (39.9163,116.3970)
        double distance = DistanceUtil.distanceMeters(39.9087, 116.3975, 39.9163, 116.3970);
        assertEquals(850, distance, 100); // 约 850 米，允许误差
    }
}




