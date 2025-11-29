package com.example.cross_intelligence.mvc.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 封装高德定位 SDK 的管理器，负责能耗控制、精度切换与回调分发。
 */
public class MapLocationManager implements AMapLocationListener {

    public interface LocationCallback {
        void onLocationUpdate(double lat, double lng, float accuracy);

        void onLocationError(int errorCode, String errorInfo);
    }

    interface LocationClient {
        void setLocationListener(AMapLocationListener listener);

        void setLocationOption(AMapLocationClientOption option);

        void startLocation();

        void stopLocation();

        void onDestroy();

        void setMockEnable(boolean enable);

        void setMockMode(boolean mockMode);

        void setMockLocation(AMapLocation location);
    }

    static class AmapLocationClientWrapper implements LocationClient {
        private static final String TAG = "MLocationWrapper";
        private final AMapLocationClient client;

        AmapLocationClientWrapper(Context context) {
            try {
                client = new AMapLocationClient(context.getApplicationContext());
            } catch (Exception e) {
                throw new IllegalStateException("初始化 AMapLocationClient 失败", e);
            }
        }

        @Override
        public void setLocationListener(AMapLocationListener listener) {
            client.setLocationListener(listener);
        }

        @Override
        public void setLocationOption(AMapLocationClientOption option) {
            client.setLocationOption(option);
        }

        @Override
        public void startLocation() {
            client.startLocation();
        }

        @Override
        public void stopLocation() {
            client.stopLocation();
        }

        @Override
        public void onDestroy() {
            client.onDestroy();
        }

        @Override
        public void setMockEnable(boolean enable) {
            invokeOptional("setMockEnable", new Class[]{boolean.class}, enable);
        }

        @Override
        public void setMockMode(boolean mockMode) {
            invokeOptional("setMockMode", new Class[]{boolean.class}, mockMode);
        }

        @Override
        public void setMockLocation(AMapLocation location) {
            invokeOptional("setMockLocation", new Class[]{AMapLocation.class}, location);
        }

        private void invokeOptional(String methodName, Class<?>[] paramTypes, Object... args) {
            try {
                Method method = client.getClass().getMethod(methodName, paramTypes);
                method.invoke(client, args);
            } catch (NoSuchMethodException ignored) {
                // 当前 AMap SDK 版本不支持该方法，忽略。
            } catch (IllegalAccessException | InvocationTargetException e) {
                Log.w(TAG, "调用可选方法失败: " + methodName, e);
            }
        }
    }

    private final LocationClient client;
    private final LocationCallback callback;
    private final AMapLocationClientOption option;

    public MapLocationManager(@NonNull Context context, @NonNull LocationCallback callback) {
        this(new AmapLocationClientWrapper(context), callback);
    }

    MapLocationManager(@NonNull LocationClient client, @NonNull LocationCallback callback) {
        this.client = client;
        this.callback = callback;
        this.option = createDefaultOption();
        client.setLocationListener(this);
        client.setLocationOption(option);
    }

    private AMapLocationClientOption createDefaultOption() {
        AMapLocationClientOption opt = new AMapLocationClientOption();
        opt.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        opt.setGpsFirst(false);
        opt.setNeedAddress(false);
        opt.setSensorEnable(false);
        opt.setInterval(4000);
        opt.setLocationCacheEnable(true);
        opt.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.Transport);
        opt.setHttpTimeOut(8000);
        opt.setMockEnable(false);
        return opt;
    }

    public void setHighPrecision(boolean enable) {
        if (enable) {
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setInterval(2000);
        } else {
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Battery_Saving);
            option.setInterval(8000);
        }
        client.setLocationOption(option);
    }

    public void start() {
        client.startLocation();
    }

    public void stop() {
        client.stopLocation();
    }

    public void destroy() {
        client.onDestroy();
    }

    public void enableMockLocation(boolean enable) {
        client.setMockEnable(enable);
    }

    public void setMockMode(boolean mockMode) {
        client.setMockMode(mockMode);
    }

    @VisibleForTesting
    public void injectMockLocation(@NonNull AMapLocation location) {
        client.setMockLocation(location);
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation == null) {
            callback.onLocationError(-1, "location is null");
            return;
        }
        if (aMapLocation.getErrorCode() == 0) {
            callback.onLocationUpdate(
                    aMapLocation.getLatitude(),
                    aMapLocation.getLongitude(),
                    aMapLocation.getAccuracy());
        } else {
            callback.onLocationError(aMapLocation.getErrorCode(), aMapLocation.getErrorInfo());
        }
    }
}


