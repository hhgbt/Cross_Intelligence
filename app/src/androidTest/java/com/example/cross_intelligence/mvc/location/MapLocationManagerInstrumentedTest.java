package com.example.cross_intelligence.mvc.location;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.amap.api.location.AMapLocation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MapLocationManagerInstrumentedTest {

    private MapLocationManager manager;
    private TestCallback callback;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        callback = new TestCallback();
        manager = new MapLocationManager(context, callback);
        manager.enableMockLocation(true);
        manager.setMockMode(true);
    }

    @Test
    public void injectMockLocation_triggersCallback() {
        AMapLocation location = new AMapLocation("mock");
        location.setLatitude(30.123);
        location.setLongitude(120.456);
        location.setAccuracy(3f);
        location.setErrorCode(0);
        manager.onLocationChanged(location);
        assertEquals(30.123, callback.lat, 0.0001);
    }

    @Test
    public void injectErrorLocation_triggersErrorCallback() {
        AMapLocation location = new AMapLocation("mock");
        location.setErrorCode(4);
        location.setErrorInfo("mock error");
        manager.onLocationChanged(location);
        assertEquals(4, callback.errorCode);
    }

    private static class TestCallback implements MapLocationManager.LocationCallback {

        double lat;
        int errorCode;

        @Override
        public void onLocationUpdate(double lat, double lng, float accuracy) {
            this.lat = lat;
        }

        @Override
        public void onLocationError(int errorCode, String errorInfo) {
            this.errorCode = errorCode;
        }
    }
}




