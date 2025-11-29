package com.example.cross_intelligence.mvc.location;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.example.cross_intelligence.mvc.model.CheckPoint;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

public class RaceMapControllerTest {

    private MapView mapView;
    private AMap aMap;
    private UiSettings uiSettings;
    private RaceMapController controller;

    @Before
    public void setUp() {
        mapView = mock(MapView.class);
        aMap = mock(AMap.class);
        uiSettings = mock(UiSettings.class);
        when(mapView.getMap()).thenReturn(aMap);
        when(aMap.getUiSettings()).thenReturn(uiSettings);
        when(aMap.addMarker(any(MarkerOptions.class))).thenAnswer(invocation -> mock(Marker.class));
        controller = new RaceMapController(mapView, aMap);
    }

    @Test
    public void addCheckPoints_addsMarkers() {
        CheckPoint p1 = new CheckPoint();
        p1.setLatitude(30);
        p1.setLongitude(120);
        p1.setName("P1");
        p1.setOrderIndex(1);
        CheckPoint p2 = new CheckPoint();
        p2.setLatitude(31);
        p2.setLongitude(121);
        p2.setName("P2");
        p2.setOrderIndex(2);

        controller.addCheckPoints(Arrays.asList(p1, p2));
        verify(aMap).addMarker(any(MarkerOptions.class));
    }

    @Test
    public void mapClick_callbackTriggered() {
        RaceMapController.MapEventListener listener = mock(RaceMapController.MapEventListener.class);
        controller.setMapEventListener(listener);
        controller.onMapClick(new LatLng(30, 120));
        verify(listener).onMapClicked(any(LatLng.class));
    }

    @Test
    public void markerClick_callbackTriggered() {
        Marker marker = mock(Marker.class);
        CheckPoint point = new CheckPoint();
        when(marker.getObject()).thenReturn(point);

        RaceMapController.MapEventListener listener = mock(RaceMapController.MapEventListener.class);
        controller.setMapEventListener(listener);
        controller.onMarkerClick(marker);
        verify(listener).onMarkerClicked(point);
    }

    @Test
    public void drawTrack_addsPolyline() {
        when(aMap.addPolyline(any())).thenReturn(mock(Polyline.class));
        TrackPoint tp1 = new TrackPoint();
        tp1.setLatitude(30);
        tp1.setLongitude(120);
        TrackPoint tp2 = new TrackPoint();
        tp2.setLatitude(31);
        tp2.setLongitude(121);
        controller.drawTrack(Arrays.asList(tp1, tp2));
        verify(aMap).addPolyline(any());
    }
}

