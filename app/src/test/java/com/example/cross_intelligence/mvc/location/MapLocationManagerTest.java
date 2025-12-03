package com.example.cross_intelligence.mvc.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClientOption;

import org.junit.Before;
import org.junit.Test;

public class MapLocationManagerTest {

    private FakeLocationClient fakeClient;
    private TestCallback callback;
    private MapLocationManager manager;

    @Before
    public void setUp() {
        fakeClient = new FakeLocationClient();
        callback = new TestCallback();
        manager = new MapLocationManager(fakeClient, callback);
    }

    @Test
    public void start_shouldApplyDefaultOption() {
        manager.start();
        assertTrue(fakeClient.started);
        AMapLocationClientOption option = fakeClient.option;
        assertEquals(4000, option.getInterval());
        assertFalse(option.isGpsFirst());
        assertFalse(option.isNeedAddress());
    }

    @Test
    public void setHighPrecision_adjustsInterval() {
        manager.setHighPrecision(true);
        assertEquals(2000, fakeClient.option.getInterval());
        manager.setHighPrecision(false);
        assertEquals(8000, fakeClient.option.getInterval());
    }

    @Test
    public void onLocationChanged_success() {
        AMapLocation location = new AMapLocation("mock");
        location.setLatitude(30.0);
        location.setLongitude(120.0);
        location.setAccuracy(5f);
        location.setErrorCode(0);

        manager.onLocationChanged(location);
        assertEquals(30.0, callback.lat, 0.0001);
        assertEquals(0, callback.lastErrorCode);
    }

    @Test
    public void onLocationChanged_error() {
        AMapLocation location = new AMapLocation("mock");
        location.setErrorCode(12);
        location.setErrorInfo("定位失败");

        manager.onLocationChanged(location);
        assertEquals(12, callback.lastErrorCode);
        assertEquals("定位失败", callback.lastErrorMsg);
    }

    @Test
    public void enableMockLocation_delegateCalled() {
        manager.enableMockLocation(true);
        assertTrue(fakeClient.mockEnabled);
        manager.setMockMode(true);
        assertTrue(fakeClient.mockMode);
    }

    private static class FakeLocationClient implements MapLocationManager.LocationClient {
        boolean started;
        boolean mockEnabled;
        boolean mockMode;
        AMapLocationClientOption option;

        @Override
        public void setLocationListener(com.amap.api.location.AMapLocationListener listener) { }

        @Override
        public void setLocationOption(AMapLocationClientOption option) {
            this.option = option;
        }

        @Override
        public void startLocation() {
            started = true;
        }

        @Override
        public void stopLocation() {
            started = false;
        }

        @Override
        public void onDestroy() { }

        @Override
        public void setMockEnable(boolean enable) {
            mockEnabled = enable;
        }

        @Override
        public void setMockMode(boolean mockMode) {
            this.mockMode = mockMode;
        }

        @Override
        public void setMockLocation(AMapLocation location) { }
    }

    private static class TestCallback implements MapLocationManager.LocationCallback {
        double lat;
        int lastErrorCode;
        String lastErrorMsg;

        @Override
        public void onLocationUpdate(double lat, double lng, float accuracy) {
            this.lat = lat;
        }

        @Override
        public void onLocationError(int errorCode, String errorInfo) {
            lastErrorCode = errorCode;
            lastErrorMsg = errorInfo;
        }
    }
}






