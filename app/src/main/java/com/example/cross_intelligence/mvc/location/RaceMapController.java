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

    public void moveCamera(double lat, double lng) {
        aMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
    }

    public void clearMarkers() {
        for (Marker marker : currentMarkers) {
            marker.remove();
        }
        currentMarkers.clear();
    }

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
                .width(8)
                .useGradient(true)
                .color(0xFF2196F3));
        aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80));
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

