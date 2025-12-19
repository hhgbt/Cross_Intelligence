package com.example.cross_intelligence.mvc.location;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.TrackPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图控制器，负责初始化地图、添加打卡点 Marker 以及事件分发。
 */
public class RaceMapController implements AMap.OnMapClickListener, AMap.OnMarkerClickListener {

    public interface MapEventListener {
        void onMapClicked(@NonNull LatLng latLng);

        void onMarkerClicked(@NonNull CheckPoint point);
    }

    private final MapView mapView;
    private final AMap aMap;
    private final List<Marker> currentMarkers = new ArrayList<>();
    private Polyline trackPolyline;
    private MapEventListener mapEventListener;
    
    // 轨迹优化：缓存点列表，避免频繁调用 getPoints()
    private List<LatLng> trackPointsCache = new ArrayList<>();
    // 上一个添加的点，用于防抖动
    private LatLng lastTrackPoint = null;
    // 相机是否应该跟随轨迹
    private boolean cameraFollowEnabled = false;

    public RaceMapController(@NonNull MapView mapView) {
        this(mapView, mapView.getMap());
    }

    RaceMapController(@NonNull MapView mapView, @NonNull AMap map) {
        this.mapView = mapView;
        this.aMap = map;
        configureMap();
    }

    private void configureMap() {
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setRotateGesturesEnabled(false);
        aMap.setTrafficEnabled(false);
        aMap.setOnMapClickListener(this);
        aMap.setOnMarkerClickListener(this);
        CameraUpdate update = CameraUpdateFactory.zoomTo(16f);
        aMap.moveCamera(update);
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
    }

    public void onResume() {
        mapView.onResume();
    }

    public void onPause() {
        mapView.onPause();
    }

    public void onDestroy() {
        clearMarkers();
        mapView.onDestroy();
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        mapView.onSaveInstanceState(outState);
    }

    public void addCheckPoints(@NonNull List<CheckPoint> points) {
        clearMarkers();
        for (CheckPoint point : points) {
            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(point.getLatitude(), point.getLongitude()))
                    .title(point.getName())
                    .snippet("序号：" + point.getOrderIndex()));
            marker.setObject(point);
            currentMarkers.add(marker);
        }
    }

    /**
     * 【状态同步】清除所有打卡点标记
     * 用于实时更新时先清除旧标记
     */
    public void clearCheckPoints() {
        clearMarkers();
    }

    public void moveCamera(double lat, double lng) {
        aMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
    }

    public void clearMarkers() {
        for (Marker marker : currentMarkers) {
            marker.remove();
        }
        currentMarkers.clear();
    }

    /**
     * 绘制完整轨迹（从 TrackPoint 列表）
     */
    public void drawTrack(@NonNull List<TrackPoint> trackPoints) {
        if (trackPolyline != null) {
            trackPolyline.remove();
        }
        if (trackPoints.isEmpty()) {
            return;
        }
        List<LatLng> latLngs = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (TrackPoint tp : trackPoints) {
            LatLng latLng = new LatLng(tp.getLatitude(), tp.getLongitude());
            latLngs.add(latLng);
            boundsBuilder.include(latLng);
        }
        trackPolyline = aMap.addPolyline(new PolylineOptions()
                .addAll(latLngs)
                .width(10)
                .useGradient(true)
                .color(0xFF2196F3));
        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80));
    }

    /**
     * 添加轨迹点（实时画线 - 优化版）
     * 优化策略：
     * 1. 防抖动：避免相同或极近位置的重复点
     * 2. 缓存机制：不每次都调用 getPoints()
     * 3. 平滑更新：只更新点列表，不重绘地图
     * 4. 可选相机跟随：平滑移动视角
     * 
     * @param lat 纬度
     * @param lng 经度
     */
    public void addTrackPoint(double lat, double lng) {
        LatLng newPoint = new LatLng(lat, lng);
        
        // 防抖动：如果与上一个点距离太近（< 2米），跳过
        if (lastTrackPoint != null) {
            double distance = calculateDistance(
                lastTrackPoint.latitude, lastTrackPoint.longitude,
                newPoint.latitude, newPoint.longitude
            );
            if (distance < 2.0) {
                return; // 距离太近，不添加
            }
        }
        
        if (trackPolyline == null) {
            // 首次创建轨迹线
            trackPointsCache.clear();
            trackPointsCache.add(newPoint);
            trackPolyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(trackPointsCache)
                    .width(10)
                    .useGradient(true)
                    .color(0xFF2196F3));
        } else {
            // 平滑更新：直接添加到缓存并更新 Polyline
            trackPointsCache.add(newPoint);
            trackPolyline.setPoints(trackPointsCache);
        }
        
        lastTrackPoint = newPoint;
        
        // 可选：相机平滑跟随
        if (cameraFollowEnabled) {
            aMap.animateCamera(CameraUpdateFactory.newLatLng(newPoint), 200, null);
        }
    }
    
    /**
     * 计算两点间距离（米）
     * 使用 Haversine 公式
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * 清除轨迹线
     */
    public void clearTrack() {
        if (trackPolyline != null) {
            trackPolyline.remove();
            trackPolyline = null;
        }
        trackPointsCache.clear();
        lastTrackPoint = null;
    }
    
    /**
     * 设置相机是否跟随轨迹
     * @param enabled true 为跟随，false 为不跟随
     */
    public void setCameraFollowEnabled(boolean enabled) {
        this.cameraFollowEnabled = enabled;
    }
    
    /**
     * 获取当前轨迹点数量
     */
    public int getTrackPointCount() {
        return trackPointsCache.size();
    }

    public void setMapEventListener(@Nullable MapEventListener listener) {
        this.mapEventListener = listener;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (mapEventListener != null) {
            mapEventListener.onMapClicked(latLng);
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (mapEventListener != null && marker.getObject() instanceof CheckPoint) {
            mapEventListener.onMarkerClicked((CheckPoint) marker.getObject());
            return true;
        }
        return false;
    }
}

