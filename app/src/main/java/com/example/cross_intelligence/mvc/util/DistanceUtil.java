package com.example.cross_intelligence.mvc.util;

/**
 * Haversine 距离计算工具，返回单位：米。
 */
public final class DistanceUtil {

    private static final double EARTH_RADIUS = 6371000d; // meters

    private DistanceUtil() {
    }

    public static double distanceMeters(double startLat, double startLng, double endLat, double endLng) {
        double dLat = Math.toRadians(endLat - startLat);
        double dLng = Math.toRadians(endLng - startLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(startLat))
                * Math.cos(Math.toRadians(endLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}





