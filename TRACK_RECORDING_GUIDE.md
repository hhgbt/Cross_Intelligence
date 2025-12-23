# 轨迹记录功能使用说明

## 功能概述

轨迹记录功能通过后台服务（`TrackRecorderService`）实现了选手运动轨迹的自动记录和实时显示。该功能具有以下特点：

- ✅ **高精度定位**：使用高德地图 SDK 的 High_Accuracy 模式
- ✅ **智能过滤**：只记录位移超过 5 米的点，避免原地跳点
- ✅ **定时采样**：每 3 秒获取一次定位
- ✅ **后台运行**：使用前台服务，支持锁屏后继续记录
- ✅ **实时画线**：通过广播机制实时更新地图上的轨迹线
- ✅ **数据持久化**：自动保存到 Realm 数据库的 `TrackPoint` 表

## 核心组件

### 1. TrackRecorderService（后台服务）

**位置**：`app/src/main/java/com/example/cross_intelligence/mvc/service/TrackRecorderService.java`

**主要功能**：
- 持续高精度定位（每 3 秒一次）
- 智能过滤轨迹点（位移 < 5 米则跳过）
- 前台服务通知显示
- 广播位置更新供 UI 实时显示

**启动服务**：
```java
TrackRecorderService.startTracking(context, raceId, userId);
```

**停止服务**：
```java
TrackRecorderService.stopTracking(context);
```

### 2. TrackManager（数据管理）

**位置**：`app/src/main/java/com/example/cross_intelligence/mvc/controller/TrackManager.java`

**主要功能**：
- 采样过滤（精度 > 30m 的点会被丢弃）
- 批量写入（每 5 个点批量保存，提升性能）
- 轨迹查询

**记录轨迹点**：
```java
trackManager.onLocationUpdate(raceId, userId, lat, lng, accuracy, speed, timestamp);
```

**查询轨迹**：
```java
List<TrackPoint> track = trackManager.queryTrack(raceId, userId);
```

### 3. RaceMapController（地图绘制）

**位置**：`app/src/main/java/com/example/cross_intelligence/mvc/location/RaceMapController.java`

**新增方法**：

**实时添加轨迹点**（人走线出）：
```java
mapController.addTrackPoint(lat, lng);
```

**绘制完整轨迹**：
```java
List<TrackPoint> points = trackManager.queryTrack(raceId, userId);
mapController.drawTrack(points);
```

**清除轨迹**：
```java
mapController.clearTrack();
```

## 使用流程

### 在 CheckInActivity 中的集成

1. **开始记录轨迹**：
   - 用户点击"开始记录轨迹"按钮
   - 检查 `raceId` 和 `userId` 是否有效
   - 调用 `TrackRecorderService.startTracking()`
   - 服务在后台持续定位并保存数据

2. **实时显示轨迹**：
   - 服务通过广播发送位置更新（`ACTION_TRACK_UPDATE`）
   - `CheckInActivity` 注册 `BroadcastReceiver` 接收更新
   - 每次收到新位置，调用 `mapController.addTrackPoint()` 绘制

3. **停止记录**：
   - 用户点击"停止记录轨迹"按钮
   - 调用 `TrackRecorderService.stopTracking()`
   - 服务自动保存剩余的待写入数据

4. **Activity 销毁时清理**：
   - 在 `onDestroy()` 中注销广播接收器
   - 如果服务还在运行，自动停止它

## 数据模型

### TrackPoint

```java
public class TrackPoint extends RealmObject {
    @PrimaryKey
    private String pointId;      // 唯一ID
    @Index
    private String raceId;       // 所属赛事
    @Index
    private String userId;       // 所属用户
    @Index
    private Date timestamp;      // 记录时间
    private double latitude;     // 纬度
    private double longitude;    // 经度
    private float speed;         // 速度（m/s）
}
```

## 配置参数

在 `TrackRecorderService` 中可调整的参数：

```java
// 定位间隔（毫秒）
option.setInterval(3000);  // 默认 3 秒

// 在 TrackManager 中：
private static final double MIN_DISTANCE_METERS = 5.0;  // 最小位移（米）
private static final long MIN_INTERVAL_MS = 4_000;      // 最小时间间隔（毫秒）
private static final int BATCH_SIZE = 5;                // 批量写入大小
```

## 权限要求

已在 `AndroidManifest.xml` 中配置：

```xml
<!-- 前台服务权限（Android 9+）-->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 前台服务位置权限（Android 10+）-->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- 精确位置权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- 粗略位置权限 -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

## 性能优化

1. **智能过滤**：
   - 精度低于 30 米的点会被丢弃
   - 位移小于 5 米的点会被跳过（防止原地跳点）

2. **批量写入**：
   - 每 5 个点批量保存到数据库
   - 使用 `executeTransactionAsync` 异步写入

3. **内存管理**：
   - 使用 `Realm.copyFromRealm()` 避免持有 Realm 对象引用
   - 及时关闭 Realm 实例

## 未来扩展

可以基于此功能实现：

1. **轨迹回放**：根据 `timestamp` 排序后按时间轴播放
2. **轨迹统计**：计算总距离、平均速度、运动时长
3. **轨迹分享**：导出 GPX 格式文件
4. **热力图**：多用户轨迹叠加显示
5. **离线地图**：下载地图瓦片后离线记录

## 故障排查

1. **服务未启动**：
   - 检查 `raceId` 和 `userId` 是否为空
   - 检查定位权限是否授予
   - 查看 Logcat 中的 `TrackRecorderService` 日志

2. **轨迹不显示**：
   - 确认广播接收器已注册
   - 检查 `mapController` 是否为 null
   - 验证位置更新是否触发（查看日志）

3. **定位不准确**：
   - 确保在室外环境测试（室内 GPS 信号弱）
   - 检查手机定位服务是否开启
   - 调整 `MIN_DISTANCE_METERS` 阈值

## 示例代码

完整的使用示例已集成在 `CheckInActivity.java` 中：

```java
// 注册广播接收器
private void registerTrackUpdateReceiver() {
    trackUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra(TrackRecorderService.EXTRA_LATITUDE, 0);
            double lng = intent.getDoubleExtra(TrackRecorderService.EXTRA_LONGITUDE, 0);
            if (lat != 0 && lng != 0) {
                runOnUiThread(() -> mapController.addTrackPoint(lat, lng));
            }
        }
    };
    IntentFilter filter = new IntentFilter(TrackRecorderService.ACTION_TRACK_UPDATE);
    registerReceiver(trackUpdateReceiver, filter);
}

// 切换轨迹记录
private void toggleTrackRecording() {
    if (isTrackingEnabled) {
        TrackRecorderService.stopTracking(this);
        isTrackingEnabled = false;
        btnToggleTrack.setText("开始记录轨迹");
    } else {
        TrackRecorderService.startTracking(this, raceId, userId);
        isTrackingEnabled = true;
        btnToggleTrack.setText("停止记录轨迹");
    }
}

// 清理资源
@Override
protected void onDestroy() {
    super.onDestroy();
    if (trackUpdateReceiver != null) {
        unregisterReceiver(trackUpdateReceiver);
    }
    if (isTrackingEnabled) {
        TrackRecorderService.stopTracking(this);
    }
}
```

---

**开发者**: AI Assistant  
**更新时间**: 2025-12-18  
**版本**: v1.0










