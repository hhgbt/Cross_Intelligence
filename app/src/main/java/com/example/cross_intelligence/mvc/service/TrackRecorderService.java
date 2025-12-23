package com.example.cross_intelligence.mvc.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.controller.TrackManager;
import com.example.cross_intelligence.mvc.util.DistanceUtil;

/**
 * 轨迹记录后台服务（前台服务）
 * 功能：
 * - 每3秒获取一次高精度定位
 * - 智能过滤：只有位移超过5米才记录，防止原地跳点
 * - 自动保存到 Realm 的 TrackPoint 表
 */
public class TrackRecorderService extends Service implements AMapLocationListener {

    private static final String TAG = "TrackRecorderService";
    private static final String CHANNEL_ID = "track_recorder_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Intent 参数
    public static final String EXTRA_RACE_ID = "raceId";
    public static final String EXTRA_USER_ID = "userId";
    public static final String ACTION_START_TRACKING = "com.example.cross_intelligence.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.example.cross_intelligence.STOP_TRACKING";

    // 定位相关
    private AMapLocationClient locationClient;
    private TrackManager trackManager;
    private String currentRaceId;
    private String currentUserId;

    // 用于轨迹广播
    public static final String ACTION_TRACK_UPDATE = "com.example.cross_intelligence.TRACK_UPDATE";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";

    // 上一个记录点（用于距离过滤）
    private double lastRecordedLat = 0;
    private double lastRecordedLng = 0;

    // 省电模式相关
    private boolean powerSavingEnabled = true;  // 默认开启省电模式
    private static final float LOW_SPEED_THRESHOLD = 0.5f; // m/s，约1.8 km/h
    private static final long NORMAL_INTERVAL = 3000;  // 正常频率：3秒
    private static final long LOW_POWER_INTERVAL = 10000; // 省电频率：10秒
    private int lowSpeedCount = 0;  // 连续低速计数
    private static final int LOW_SPEED_TRIGGER = 3; // 连续3次低速才切换
    private AMapLocationClientOption locationOption;  // 保存定位配置

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TrackRecorderService onCreate");
        trackManager = new TrackManager();
        new Handler(Looper.getMainLooper());
        initLocationClient();
        createNotificationChannel();
    }

    /**
     * 初始化定位客户端
     */
    private void initLocationClient() {
        try {
            locationClient = new AMapLocationClient(getApplicationContext());
            locationClient.setLocationListener(this);

            locationOption = new AMapLocationClientOption();
            // 高精度模式
            locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            // 初始频率：3秒（省电模式会动态调整）
            locationOption.setInterval(NORMAL_INTERVAL);
            // 返回地址信息（可选）
            locationOption.setNeedAddress(false);
            // 允许模拟位置（调试用）
            locationOption.setMockEnable(true);
            // 单次定位超时时间
            locationOption.setHttpTimeOut(20000);
            // 连续定位超时时间
            locationOption.setLocationCacheEnable(false);

            locationClient.setLocationOption(locationOption);
            Log.d(TAG, "定位客户端初始化成功（省电模式：" + (powerSavingEnabled ? "开启" : "关闭") + "）");
        } catch (Exception e) {
            Log.e(TAG, "定位客户端初始化失败", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START_TRACKING.equals(action)) {
            currentRaceId = intent.getStringExtra(EXTRA_RACE_ID);
            currentUserId = intent.getStringExtra(EXTRA_USER_ID);

            if (currentRaceId == null || currentUserId == null) {
                Log.e(TAG, "raceId 或 userId 为空，无法启动轨迹记录");
                stopSelf();
                return START_NOT_STICKY;
            }

            startForeground(NOTIFICATION_ID, createNotification("正在记录轨迹..."));
            startLocationTracking();
            Log.d(TAG, "开始记录轨迹: raceId=" + currentRaceId + ", userId=" + currentUserId);

        } else if (ACTION_STOP_TRACKING.equals(action)) {
            stopLocationTracking();
            stopForeground(true);
            stopSelf();
            Log.d(TAG, "停止记录轨迹");
        }

        return START_STICKY;
    }

    /**
     * 开始定位跟踪
     */
    private void startLocationTracking() {
        if (locationClient != null) {
            locationClient.startLocation();
            Log.d(TAG, "定位已启动");
        }
    }

    /**
     * 停止定位跟踪
     */
    private void stopLocationTracking() {
        if (locationClient != null) {
            locationClient.stopLocation();
            Log.d(TAG, "定位已停止");
        }
        // 刷新未保存的轨迹点
        if (trackManager != null) {
            trackManager.flushAsync();
        }
    }

    @Override
    public void onLocationChanged(AMapLocation location) {
        if (location == null) {
            Log.w(TAG, "定位结果为空");
            return;
        }

        if (location.getErrorCode() != 0) {
            Log.w(TAG, "定位失败: code=" + location.getErrorCode() + ", info=" + location.getErrorInfo());
            return;
        }

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        float speed = location.getSpeed();
        long timestamp = System.currentTimeMillis();

        Log.d(TAG, String.format("定位成功: lat=%.6f, lng=%.6f, accuracy=%.1fm, speed=%.1fm/s",
                lat, lng, accuracy, speed));

        // 省电模式：根据速度动态调整定位频率
        if (powerSavingEnabled) {
            adjustLocationIntervalBySpeed(speed);
        }

        // 智能过滤：只有位移超过5米才记录
        if (lastRecordedLat != 0 && lastRecordedLng != 0) {
            double distance = DistanceUtil.distanceMeters(lastRecordedLat, lastRecordedLng, lat, lng);
            if (distance < 5.0) {
                Log.d(TAG, "位移不足5米，跳过记录（距离：" + distance + "m）");
                // 但仍然广播位置更新，用于地图实时显示
                broadcastLocationUpdate(lat, lng);
                return;
            }
        }

        // 记录到 TrackManager
        trackManager.onLocationUpdate(currentRaceId, currentUserId, lat, lng, accuracy, speed, timestamp);
        lastRecordedLat = lat;
        lastRecordedLng = lng;

        // 更新通知
        updateNotification(String.format("已记录轨迹点（精度: %.1fm，速度: %.1fkm/h）",
                accuracy, speed * 3.6)); // 转换为 km/h

        // 广播位置更新
        broadcastLocationUpdate(lat, lng);

        Log.d(TAG, "轨迹点已记录");
    }

    /**
     * 省电模式：根据速度动态调整定位频率
     * - 低速（< 0.5 m/s）：切换到 10 秒间隔
     * - 正常速度：恢复 3 秒间隔
     */
    private void adjustLocationIntervalBySpeed(float speed) {
        if (locationClient == null || locationOption == null) {
            return;
        }

        long currentInterval = locationOption.getInterval();

        if (speed < LOW_SPEED_THRESHOLD) {
            // 连续低速
            lowSpeedCount++;
            
            // 连续3次低速才切换到省电模式
            if (lowSpeedCount >= LOW_SPEED_TRIGGER && currentInterval != LOW_POWER_INTERVAL) {
                locationOption.setInterval(LOW_POWER_INTERVAL);
                locationClient.setLocationOption(locationOption);
                Log.i(TAG, "切换到省电模式（10秒间隔）- 速度: " + speed + " m/s");
                updateNotification("省电模式（低速移动）");
            }
        } else {
            // 恢复正常速度
            lowSpeedCount = 0;
            
            if (currentInterval != NORMAL_INTERVAL) {
                locationOption.setInterval(NORMAL_INTERVAL);
                locationClient.setLocationOption(locationOption);
                Log.i(TAG, "恢复正常模式（3秒间隔）- 速度: " + speed + " m/s");
                updateNotification("正常记录中");
            }
        }
    }

    /**
     * 开启/关闭省电模式
     * @param enabled true = 开启，false = 关闭
     */
    public void setPowerSavingEnabled(boolean enabled) {
        this.powerSavingEnabled = enabled;
        Log.i(TAG, "省电模式：" + (enabled ? "已开启" : "已关闭"));
    }

    /**
     * 广播位置更新（用于实时画线）
     */
    private void broadcastLocationUpdate(double lat, double lng) {
        Intent broadcast = new Intent(ACTION_TRACK_UPDATE);
        broadcast.putExtra(EXTRA_LATITUDE, lat);
        broadcast.putExtra(EXTRA_LONGITUDE, lng);
        sendBroadcast(broadcast);
    }

    /**
     * 更新通知内容
     */
    private void updateNotification(String content) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "轨迹记录服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("后台记录您的运动轨迹");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification(String content) {
        // 点击通知打开打卡页面（可选）
        Intent notificationIntent = new Intent(this, com.example.cross_intelligence.mvc.view.checkin.CheckInActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("轨迹记录中")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TrackRecorderService onDestroy");
        stopLocationTracking();
        if (locationClient != null) {
            locationClient.onDestroy();
            locationClient = null;
        }
        trackManager = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 启动轨迹记录服务
     */
    public static void startTracking(@NonNull Context context, @NonNull String raceId, @NonNull String userId) {
        Intent intent = new Intent(context, TrackRecorderService.class);
        intent.setAction(ACTION_START_TRACKING);
        intent.putExtra(EXTRA_RACE_ID, raceId);
        intent.putExtra(EXTRA_USER_ID, userId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * 停止轨迹记录服务
     */
    public static void stopTracking(@NonNull Context context) {
        Intent intent = new Intent(context, TrackRecorderService.class);
        intent.setAction(ACTION_STOP_TRACKING);
        context.startService(intent);
    }
}

