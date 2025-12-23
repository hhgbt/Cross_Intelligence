package com.example.cross_intelligence.mvc.util;

import androidx.annotation.NonNull;

import com.amap.api.maps.model.LatLng;
import com.example.cross_intelligence.mvc.model.CheckPoint;

/**
 * 地理围栏工具类
 * 用于判断选手是否进入指定范围（自动触发终点等场景）
 */
public final class GeoFenceUtil {

    private GeoFenceUtil() {
    }

    /**
     * 检查是否进入地理围栏
     * 
     * @param current 当前位置
     * @param target  目标位置
     * @param radius  围栏半径（米）
     * @return true = 已进入围栏，false = 未进入
     */
    public static boolean isInFence(@NonNull LatLng current, @NonNull LatLng target, double radius) {
        double distance = DistanceUtil.distanceMeters(
                current.latitude, current.longitude,
                target.latitude, target.longitude
        );
        return distance <= radius;
    }

    /**
     * 检查是否进入地理围栏（重载方法，直接传经纬度）
     * 
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param targetLat  目标纬度
     * @param targetLng  目标经度
     * @param radius     围栏半径（米）
     * @return true = 已进入围栏，false = 未进入
     */
    public static boolean isInFence(double currentLat, double currentLng,
                                     double targetLat, double targetLng,
                                     double radius) {
        double distance = DistanceUtil.distanceMeters(currentLat, currentLng, targetLat, targetLng);
        return distance <= radius;
    }

    /**
     * 检查是否进入打卡点围栏
     * 
     * @param currentLat  当前纬度
     * @param currentLng  当前经度
     * @param checkPoint  打卡点
     * @param customRadius 自定义半径（米），如果 <= 0 则使用打卡点自身的半径
     * @return true = 已进入围栏，false = 未进入
     */
    public static boolean isInCheckPointFence(double currentLat, double currentLng,
                                               @NonNull CheckPoint checkPoint,
                                               double customRadius) {
        double radius = customRadius > 0 ? customRadius : checkPoint.getCheckRadius();
        if (radius <= 0) {
            radius = 50.0; // 默认50米
        }
        return isInFence(currentLat, currentLng,
                checkPoint.getLatitude(), checkPoint.getLongitude(),
                radius);
    }

    /**
     * 检查是否进入打卡点围栏（使用打卡点自身的半径）
     * 
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param checkPoint 打卡点
     * @return true = 已进入围栏，false = 未进入
     */
    public static boolean isInCheckPointFence(double currentLat, double currentLng,
                                               @NonNull CheckPoint checkPoint) {
        return isInCheckPointFence(currentLat, currentLng, checkPoint, 0);
    }

    /**
     * 计算当前位置到目标的距离（米）
     * 
     * @param current 当前位置
     * @param target  目标位置
     * @return 距离（米）
     */
    public static double getDistance(@NonNull LatLng current, @NonNull LatLng target) {
        return DistanceUtil.distanceMeters(
                current.latitude, current.longitude,
                target.latitude, target.longitude
        );
    }

    /**
     * 计算到打卡点的距离（米）
     * 
     * @param currentLat 当前纬度
     * @param currentLng 当前经度
     * @param checkPoint 打卡点
     * @return 距离（米）
     */
    public static double getDistanceToCheckPoint(double currentLat, double currentLng,
                                                  @NonNull CheckPoint checkPoint) {
        return DistanceUtil.distanceMeters(currentLat, currentLng,
                checkPoint.getLatitude(), checkPoint.getLongitude());
    }
}










