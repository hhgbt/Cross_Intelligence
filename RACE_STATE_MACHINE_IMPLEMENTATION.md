# 比赛状态机实现文档

## ✅ 已完成的核心组件

### 1. RaceSession 模型
**文件**: `app/src/main/java/com/example/cross_intelligence/mvc/model/RaceSession.java`

存储比赛会话状态，包括：
- 状态：未开始 / 进行中 / 已完成 / 未完成(DNF)
- 开始时间、结束时间、总用时
- 起点/终点坐标
- 已打卡检查点数量
- 轨迹记录开关

### 2. RaceSessionManager
**文件**: `app/src/main/java/com/example/cross_intelligence/mvc/controller/RaceSessionManager.java`

提供会话管理方法：
- `getOrCreateSession()` - 获取或创建会话
- `startRace()` - 起点打卡，激活赛程
- `checkCheckpoint()` - 检查点打卡，增加计数
- `finishRace()` - 终点打卡，完成比赛
- `canStartRace()`, `canCheckCheckpoint()`, `canFinishRace()` - 状态验证

### 3. CheckInManager 扩展
**文件**: `app/src/main/java/com/example/cross_intelligence/mvc/controller/CheckInManager.java`

新增 `checkInWithStateMachine()` 方法，实现：

#### 起点逻辑（TYPE_START）
```java
if (CheckPoint.TYPE_START.equals(checkPointType)) {
    // 1. 验证状态：确保未开始
    if (session.isStarted()) {
        callback.onFailure(new IllegalStateException("已经开始比赛"));
        return;
    }
    
    // 2. 需要扫码验证
    // 3. 创建打卡记录
    // 4. 更新会话状态
    dbSession.setStatus(RaceSession.STATUS_IN_PROGRESS);
    dbSession.setStartTime(new Date());
    dbSession.setStartLatitude(currentLat);
    dbSession.setStartLongitude(currentLng);
    dbSession.setTrackingEnabled(true);
    
    // 回调会包含 TYPE_START 标识，Activity 中启动 TrackRecorderService
}
```

#### 检查点逻辑（TYPE_CHECKPOINT）
```java
else if (CheckPoint.TYPE_CHECKPOINT.equals(checkPointType)) {
    // 1. 验证状态：确保比赛进行中
    if (!session.isInProgress()) {
        callback.onFailure(new IllegalStateException("请先在起点开始比赛"));
        return;
    }
    
    // 2. 需要扫码验证
    // 3. 创建打卡记录
    // 4. 增加已打卡计数
    dbSession.setCheckpointsChecked(dbSession.getCheckpointsChecked() + 1);
    
    // 不停止计时，继续比赛
}
```

#### 终点逻辑（TYPE_FINISH）
```java
else if (CheckPoint.TYPE_FINISH.equals(checkPointType)) {
    // 1. 验证状态：确保比赛进行中
    if (!session.isInProgress()) {
        callback.onFailure(new IllegalStateException("请先在起点开始比赛"));
        return;
    }
    
    // 2. 终点无需扫码（自动触发）
    boolean requireQrCode = !CheckPoint.TYPE_FINISH.equals(checkPointType);
    
    // 3. 只需地理围栏验证（30米内）
    // 4. 创建打卡记录
    // 5. 完成比赛
    Date endTime = new Date();
    dbSession.setStatus(RaceSession.STATUS_FINISHED);
    dbSession.setEndTime(endTime);
    dbSession.setEndLatitude(currentLat);
    dbSession.setEndLongitude(currentLng);
    dbSession.setTrackingEnabled(false);
    
    // 计算总用时
    long totalMillis = endTime.getTime() - dbSession.getStartTime().getTime();
    dbSession.setTotalMillis(totalMillis);
    
    // 回调会包含 TYPE_FINISH 标识，Activity 中停止 TrackRecorderService
}
```

## 🚀 CheckInActivity 集成方案

### 修改 performCheckIn 方法

```java
private void performCheckIn(String qrContent) {
    String userId = PreferenceUtil.getString(this, "currentUserId", "");
    if (TextUtils.isEmpty(userId)) {
        UIUtil.showToast(this, "请先登录");
        return;
    }
    
    String currentRaceId = raceId != null ? raceId : "race-demo";
    
    binding.progressBar.setVisibility(View.VISIBLE);
    
    // 使用状态机打卡
    checkInManager.checkInWithStateMachine(
        currentRaceId, 
        userId, 
        currentPoint,
        lastLat, 
        lastLng, 
        qrContent, 
        !binding.switchOnline.isChecked(),
        new CheckInManager.StateCheckInCallback() {
            @Override
            public void onSuccess(@NonNull CheckInRecord record, 
                                @NonNull RaceSession session, 
                                @NonNull String checkPointType) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    
                    // 根据类型执行不同逻辑
                    handleCheckInSuccess(record, session, checkPointType);
                });
            }

            @Override
            public void onFailure(@NonNull Throwable throwable) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    String errorMessage = "✗ 打卡失败：" + throwable.getMessage();
                    binding.tvStatus.setText(errorMessage);
                    UIUtil.showToast(CheckInActivity.this, errorMessage);
                });
            }
        }
    );
}
```

### 新增 handleCheckInSuccess 方法

```java
private void handleCheckInSuccess(CheckInRecord record, RaceSession session, String checkPointType) {
    if (CheckPoint.TYPE_START.equals(checkPointType)) {
        // 起点逻辑：启动轨迹记录
        TrackRecorderService.startTracking(this, raceId, userId);
        isTrackingEnabled = true;
        
        // 更新 UI
        binding.tvStatus.setText("🏁 比赛开始！计时已启动");
        showSuccessAnimation();
        
        // 启动计时器显示
        startRaceTimer(session.getStartTime());
        
    } else if (CheckPoint.TYPE_CHECKPOINT.equals(checkPointType)) {
        // 检查点逻辑：常规打卡
        binding.tvStatus.setText(String.format(
            "📍 检查点打卡成功！已完成 %d/%d 个检查点",
            session.getCheckpointsChecked(),
            session.getTotalCheckpoints()
        ));
        showSuccessAnimation();
        
        // 继续比赛，不停止计时
        
    } else if (CheckPoint.TYPE_FINISH.equals(checkPointType)) {
        // 终点逻辑：完成比赛
        TrackRecorderService.stopTracking(this);
        isTrackingEnabled = false;
        
        // 停止计时器
        stopRaceTimer();
        
        // 显示结算页面
        showRaceFinishDialog(session);
    }
}
```

### 终点自动检测（地理围栏）

在 `onLocationUpdate()` 中添加：

```java
@Override
public void onLocationUpdate(double lat, double lng, float accuracy) {
    lastLat = lat;
    lastLng = lng;
    
    runOnUiThread(() -> {
        binding.tvLocation.setText(getString(R.string.location_format, lat, lng, accuracy));
        mapController.moveCamera(lat, lng);
        
        // 更新距离和按钮状态
        updateDistanceAndButton();
        
        // 终点自动检测
        checkFinishLineAuto(lat, lng);
    });
}

/**
 * 终点自动检测（地理围栏）
 * 进入终点 30 米范围内自动触发打卡，无需扫码
 */
private void checkFinishLineAuto(double lat, double lng) {
    // 获取当前会话
    RaceSessionManager sessionManager = new RaceSessionManager();
    RaceSession session = sessionManager.getSession(raceId, userId);
    
    if (session == null || !session.isInProgress()) {
        return; // 比赛未开始或已结束，不检测
    }
    
    // 查找终点
    CheckPoint finishPoint = null;
    for (CheckPoint point : allCheckPoints) {
        if (CheckPoint.TYPE_FINISH.equals(point.getType())) {
            finishPoint = point;
            break;
        }
    }
    
    if (finishPoint == null) {
        return; // 没有终点
    }
    
    // 计算距离
    double distance = checkInManager.calculateDistance(finishPoint, lat, lng);
    double radius = 30.0; // 终点自动触发半径：30米
    
    if (distance <= radius && !hasFinishedRace) {
        hasFinishedRace = true; // 防止重复触发
        
        // 自动触发终点打卡（无需扫码）
        UIUtil.showToast(this, "已进入终点区域，自动完成比赛！");
        performCheckInWithoutQr(finishPoint);
    }
}

/**
 * 无二维码打卡（仅用于终点自动触发）
 */
private void performCheckInWithoutQr(CheckPoint checkPoint) {
    currentPoint = checkPoint;
    
    // 调用状态机打卡，qrContent 传 null（终点无需扫码）
    checkInManager.checkInWithStateMachine(
        raceId, 
        userId, 
        checkPoint,
        lastLat, 
        lastLng, 
        null, // 终点无需扫码
        false,
        new CheckInManager.StateCheckInCallback() {
            @Override
            public void onSuccess(@NonNull CheckInRecord record, 
                                @NonNull RaceSession session, 
                                @NonNull String checkPointType) {
                runOnUiThread(() -> {
                    handleCheckInSuccess(record, session, checkPointType);
                });
            }

            @Override
            public void onFailure(@NonNull Throwable throwable) {
                runOnUiThread(() -> {
                    hasFinishedRace = false; // 失败则重置标志
                    UIUtil.showToast(CheckInActivity.this, "自动打卡失败：" + throwable.getMessage());
                });
            }
        }
    );
}
```

### 计时器实现

```java
private Handler timerHandler;
private Runnable timerRunnable;
private Date raceStartTime;

private void startRaceTimer(Date startTime) {
    raceStartTime = startTime;
    timerHandler = new Handler(Looper.getMainLooper());
    timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (raceStartTime != null) {
                long elapsedMillis = System.currentTimeMillis() - raceStartTime.getTime();
                String timeStr = formatElapsedTime(elapsedMillis);
                binding.tvTimer.setText("⏱️ " + timeStr);
                timerHandler.postDelayed(this, 1000); // 每秒更新
            }
        }
    };
    timerHandler.post(timerRunnable);
}

private void stopRaceTimer() {
    if (timerHandler != null && timerRunnable != null) {
        timerHandler.removeCallbacks(timerRunnable);
    }
}

private String formatElapsedTime(long millis) {
    long seconds = millis / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    
    return String.format(Locale.CHINA, "%02d:%02d:%02d", 
        hours, minutes % 60, seconds % 60);
}
```

## 📱 比赛结算页面

### 显示完成对话框

```java
private void showRaceFinishDialog(RaceSession session) {
    String timeStr = formatElapsedTime(session.getTotalMillis());
    
    new AlertDialog.Builder(this)
        .setTitle("🎉 比赛完成！")
        .setMessage(String.format(
            "恭喜完成比赛！\n\n" +
            "⏱️ 总用时：%s\n" +
            "📍 检查点：%d/%d\n" +
            "📊 状态：%s",
            timeStr,
            session.getCheckpointsChecked(),
            session.getTotalCheckpoints(),
            session.getStatus()
        ))
        .setPositiveButton("查看详情", (dialog, which) -> {
            // 跳转到结算详情页面
            Intent intent = new Intent(this, RaceResultActivity.class);
            intent.putExtra("sessionId", session.getSessionId());
            startActivity(intent);
        })
        .setNegativeButton("关闭", null)
        .setCancelable(false)
        .show();
}
```

## 📋 UI 布局修改

在 `activity_check_in.xml` 中添加计时器：

```xml
<TextView
    android:id="@+id/tvTimer"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:text="⏱️ 00:00:00"
    android:textSize="24sp"
    android:textStyle="bold"
    android:gravity="center"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toBottomOf="@id/tvLocation" />
```

## 🔄 完整流程示意

```
[选手进入CheckInActivity]
        ↓
[加载赛事打卡点]
        ↓
[到达起点，扫码打卡] → checkInWithStateMachine
        ↓                    (TYPE_START)
[状态：未开始 → 进行中]
        ↓
[启动 TrackRecorderService]
        ↓
[启动计时器显示]
        ↓
[移动到检查点，扫码打卡] → checkInWithStateMachine
        ↓                      (TYPE_CHECKPOINT)
[检查点计数 +1]
        ↓
[继续移动...]
        ↓
[进入终点30米范围] → checkFinishLineAuto
        ↓              (自动触发，无需扫码)
[状态：进行中 → 已完成]
        ↓
[停止 TrackRecorderService]
        ↓
[停止计时器]
        ↓
[计算总用时]
        ↓
[显示完成对话框]
        ↓
[跳转结算页面（可选）]
```

## ⚙️ 配置参数

```java
// CheckInManager.java
private static final double DEFAULT_RADIUS_METERS = 50.0; // 默认打卡半径

// CheckInActivity.java
private static final double FINISH_AUTO_RADIUS = 30.0; // 终点自动触发半径
```

## 📝 注意事项

1. **终点无需扫码**：`checkInWithStateMachine()` 方法中，当 `checkPointType` 为 `TYPE_FINISH` 时，`scannedQr` 可以为 `null`
2. **防止重复触发**：使用 `hasFinishedRace` 标志防止终点自动检测重复触发
3. **会话管理**：每个用户在每个赛事中只能有一个活跃会话
4. **轨迹关联**：`TrackPoint` 表中的 `raceId` 和 `userId` 用于关联轨迹到会话
5. **离线支持**：状态机打卡同样支持离线模式

## 🔍 测试场景

### 场景1：正常完成比赛
1. 选手到达起点，扫码打卡 → 比赛开始，计时启动
2. 依次到达检查点，扫码打卡 → 检查点计数增加
3. 进入终点30米范围 → 自动完成，显示结算

### 场景2：未在起点开始
1. 选手直接到达检查点，扫码打卡 → 提示"请先在起点开始比赛"

### 场景3：重复打卡
1. 选手在起点打卡后，再次扫码 → 提示"已经开始比赛，不能重复打卡起点"
2. 选手在同一检查点重复打卡 → 提示"该打卡点已完成打卡"

---

**开发者**: AI Assistant  
**更新时间**: 2025-12-18  
**版本**: v1.0


