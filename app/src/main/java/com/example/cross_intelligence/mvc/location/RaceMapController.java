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
import com.example.cross_intelligence.mvc.model.CheckInRecord;
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
    
    /**
     * 获取 AMap 实例（用于截图等操作）
     */
    public AMap getAMap() {
        return aMap;
    }
    
    /**
     * 获取 MapView 实例（用于截图等操作）
     */
    public MapView getMapView() {
        return mapView;
    }
    private final List<Marker> currentMarkers = new ArrayList<>();
    private Polyline trackPolyline;
    private MapEventListener mapEventListener;
    
    // 轨迹优化：缓存点列表，避免频繁调用 getPoints()
    private List<LatLng> trackPointsCache = new ArrayList<>();
    // 上一个添加的点，用于防抖动
    private LatLng lastTrackPoint = null;
    // 相机是否应该跟随轨迹
    private boolean cameraFollowEnabled = false;
    
    // 渐变轨迹：存储多个Polyline段以实现渐变效果
    private List<Polyline> gradientPolylines = new ArrayList<>();
    // 起点和终点标记
    private Marker startMarker;
    private Marker endMarker;

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
            // 根据打卡点类型在名称前加上不同的 emoji 图标
            String prefix;
            if (CheckPoint.TYPE_START.equals(point.getType())) {
                prefix = "🏁 ";
            } else if (CheckPoint.TYPE_FINISH.equals(point.getType())) {
                prefix = "🥇 ";
            } else {
                prefix = "📍 ";
            }
            String title = prefix + (point.getName() != null ? point.getName() : "");

            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(point.getLatitude(), point.getLongitude()))
                    .title(title)
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
    
    /**
     * 添加基于CheckInRecord的打卡点标记（选手实际打卡位置）
     * @param records 打卡记录列表，按时间排序
     */
    public void addCheckInRecords(@NonNull List<CheckInRecord> records) {
        for (CheckInRecord record : records) {
            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(record.getLatitude(), record.getLongitude()))
                    .title("打卡点")
                    .snippet("打卡时间：" + (record.getTimestamp() != null ? 
                            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(record.getTimestamp()) : "未知")));
            marker.setObject(record);
            currentMarkers.add(marker);
        }
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
     * 使用蓝色，和实时轨迹保持一致
     * @param trackPoints 轨迹点列表
     * @param checkInRecords 打卡记录列表（可选），用于将打卡位置连接到轨迹
     */
    public void drawTrack(@NonNull List<TrackPoint> trackPoints, @Nullable List<CheckInRecord> checkInRecords) {
        // 清除旧轨迹
        clearTrack();
        if (trackPoints.isEmpty()) {
            return;
        }
        
        // 清除起点终点标记
        if (startMarker != null) {
            startMarker.remove();
            startMarker = null;
        }
        if (endMarker != null) {
            endMarker.remove();
            endMarker = null;
        }
        
        List<LatLng> latLngs = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        
        // 将所有轨迹点转换为LatLng
        for (TrackPoint tp : trackPoints) {
            LatLng latLng = new LatLng(tp.getLatitude(), tp.getLongitude());
            latLngs.add(latLng);
            boundsBuilder.include(latLng);
        }
        
        // 如果有打卡记录，将打卡位置插入到轨迹中（按时间顺序）
        if (checkInRecords != null && !checkInRecords.isEmpty()) {
            // 按时间排序打卡记录
            List<CheckInRecord> sortedRecords = new ArrayList<>(checkInRecords);
            sortedRecords.sort((r1, r2) -> {
                if (r1.getTimestamp() == null && r2.getTimestamp() == null) return 0;
                if (r1.getTimestamp() == null) return 1;
                if (r2.getTimestamp() == null) return -1;
                return r1.getTimestamp().compareTo(r2.getTimestamp());
            });
            
            // 将打卡位置插入到轨迹中（在对应时间点附近）
            for (CheckInRecord record : sortedRecords) {
                LatLng checkInLatLng = new LatLng(record.getLatitude(), record.getLongitude());
                boundsBuilder.include(checkInLatLng);
                
                // 找到最接近的轨迹点位置插入
                if (record.getTimestamp() != null && !trackPoints.isEmpty()) {
                    int insertIndex = findInsertIndex(trackPoints, record.getTimestamp());
                    if (insertIndex >= 0 && insertIndex <= latLngs.size()) {
                        latLngs.add(insertIndex, checkInLatLng);
                    } else {
                        // 如果找不到合适位置，添加到末尾
                        latLngs.add(checkInLatLng);
                    }
                } else {
                    latLngs.add(checkInLatLng);
                }
            }
        }
        
        // 绘制完整轨迹（使用蓝色，和实时轨迹一样）
        if (latLngs.size() >= 2) {
            Polyline segment = aMap.addPolyline(new PolylineOptions()
                    .addAll(latLngs)
                    .width(10) // 轨迹线条宽度（调细）
                    .color(0xFF2196F3)); // 蓝色，和实时轨迹一样
            gradientPolylines.add(segment);
        }
        
        // 如果有打卡记录，使用打卡记录的位置作为起点和终点标记
        if (checkInRecords != null && !checkInRecords.isEmpty()) {
            // 按时间排序
            List<CheckInRecord> sortedRecords = new ArrayList<>(checkInRecords);
            sortedRecords.sort((r1, r2) -> {
                if (r1.getTimestamp() == null && r2.getTimestamp() == null) return 0;
                if (r1.getTimestamp() == null) return 1;
                if (r2.getTimestamp() == null) return -1;
                return r1.getTimestamp().compareTo(r2.getTimestamp());
            });
            
            // 起点标记（第一个打卡记录）
            CheckInRecord startRecord = sortedRecords.get(0);
            startMarker = aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(startRecord.getLatitude(), startRecord.getLongitude()))
                    .title("起点")
                    .snippet("比赛开始"));
            
            // 终点标记（最后一个打卡记录）
            if (sortedRecords.size() > 1) {
                CheckInRecord endRecord = sortedRecords.get(sortedRecords.size() - 1);
                endMarker = aMap.addMarker(new MarkerOptions()
                        .position(new LatLng(endRecord.getLatitude(), endRecord.getLongitude()))
                        .title("终点")
                        .snippet("比赛结束"));
            }
        } else {
            // 没有打卡记录，使用轨迹点作为起点和终点
            if (!trackPoints.isEmpty()) {
                TrackPoint startPoint = trackPoints.get(0);
                startMarker = aMap.addMarker(new MarkerOptions()
                        .position(new LatLng(startPoint.getLatitude(), startPoint.getLongitude()))
                        .title("起点")
                        .snippet("比赛开始"));
            }
            
            if (trackPoints.size() > 1) {
                TrackPoint endPoint = trackPoints.get(trackPoints.size() - 1);
                endMarker = aMap.addMarker(new MarkerOptions()
                        .position(new LatLng(endPoint.getLatitude(), endPoint.getLongitude()))
                        .title("终点")
                        .snippet("比赛结束"));
            }
        }
        
        // 调整相机视角
        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80));
    }
    
    /**
     * 重载方法：不包含打卡记录
     */
    public void drawTrack(@NonNull List<TrackPoint> trackPoints) {
        drawTrack(trackPoints, null);
    }
    
    /**
     * 找到打卡记录应该插入到轨迹中的位置（按时间）
     */
    private int findInsertIndex(@NonNull List<TrackPoint> trackPoints, @NonNull java.util.Date checkInTime) {
        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint tp = trackPoints.get(i);
            if (tp.getTimestamp() != null && tp.getTimestamp().after(checkInTime)) {
                return i;
            }
        }
        return trackPoints.size(); // 如果所有轨迹点都在打卡时间之前，插入到末尾
    }
    
    /**
     * 根据速度计算颜色
     * 绿色（快）-> 黄色 -> 橙色 -> 红色（慢）
     */
    private int calculateSpeedColor(float speed, float minSpeed, float maxSpeed) {
        if (maxSpeed == minSpeed) {
            return 0xFF4CAF50; // 默认绿色
        }
        
        // 归一化速度到0-1
        float normalized = (speed - minSpeed) / (maxSpeed - minSpeed);
        normalized = Math.max(0f, Math.min(1f, normalized)); // 限制在0-1
        
        // 反转：速度越快，normalized越大，颜色越绿
        float reversed = 1f - normalized;
        
        int r, g, b;
        if (reversed < 0.33f) {
            // 绿色区域（快）
            float t = reversed / 0.33f;
            r = (int) (76 + (255 - 76) * t); // 76 -> 255
            g = (int) (175 + (193 - 175) * t); // 175 -> 193
            b = (int) (80 + (7 - 80) * t); // 80 -> 7
        } else if (reversed < 0.66f) {
            // 黄色到橙色区域（中）
            float t = (reversed - 0.33f) / 0.33f;
            r = 255;
            g = (int) (193 - (87 - 193) * t); // 193 -> 87
            b = (int) (7 + (6 - 7) * t); // 7 -> 6
        } else {
            // 红色区域（慢）
            float t = (reversed - 0.66f) / 0.34f;
            r = 255;
            g = (int) (87 - (87 - 0) * t); // 87 -> 0
            b = 6;
        }
        
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
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
                    .width(10) // 实时轨迹线条宽度（调细）
                    .useGradient(true)
                    .color(0xFF2196F3)); // 蓝色
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
        
        // 清除渐变轨迹段
        for (Polyline polyline : gradientPolylines) {
            if (polyline != null) {
                polyline.remove();
            }
        }
        gradientPolylines.clear();
        
        // 清除起点终点标记
        if (startMarker != null) {
            startMarker.remove();
            startMarker = null;
        }
        if (endMarker != null) {
            endMarker.remove();
            endMarker = null;
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

