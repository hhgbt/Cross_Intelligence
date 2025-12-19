package com.example.cross_intelligence.mvc.view.checkin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.airbnb.lottie.LottieAnimationView;

import com.amap.api.maps.model.LatLng;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityCheckInBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.CheckInManager;
import com.example.cross_intelligence.mvc.location.MapLocationManager;
import com.example.cross_intelligence.mvc.location.RaceMapController;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.service.TrackRecorderService;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.QrCodeGenerator;
import com.example.cross_intelligence.mvc.util.QrPayloadParser;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.google.android.material.button.MaterialButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import com.example.cross_intelligence.mvc.model.Race;

/**
 * 打卡界面示例：GPS + ZXing 扫码双重验证。
 * 【状态同步优化】添加 Realm ChangeListener 实现实时数据同步
 */
public class CheckInActivity extends BaseActivity implements
        MapLocationManager.LocationCallback,
        RaceMapController.MapEventListener {

    private ActivityCheckInBinding binding;
    private MapLocationManager locationManager;
    private RaceMapController mapController;
    private CheckInManager checkInManager;
    private CheckPoint currentPoint;
    private double lastLat;
    private double lastLng;
    private String raceId; // 当前赛事ID
    private String userId; // 当前用户ID
    private List<CheckPoint> allCheckPoints; // 所有打卡点
    private boolean isInRange = false; // 是否在打卡范围内
    private AlertDialog qrDialog; // 二维码对话框
    private View successOverlay; // 成功动画覆盖层
    private boolean isTrackingEnabled = false; // 轨迹记录是否开启
    private BroadcastReceiver trackUpdateReceiver; // 轨迹更新广播接收器
    
    // 【状态同步】Realm 实时监听
    private Realm realm;
    private RealmResults<Race> raceResults;
    private RealmChangeListener<RealmResults<Race>> raceChangeListener;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), this::handleScanResult);

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapController = new RaceMapController(binding.mapView);
        mapController.onCreate(savedInstanceState);
        mapController.setMapEventListener(this);
        initView();
        initData();
        locationManager = new MapLocationManager(this, this);
    }

    @Override
    protected void initView() {
        // 主要方案：查看打卡二维码按钮
        binding.btnShowQr.setOnClickListener(v -> {
            if (checkGeofence()) {
                showQrCodeDialog();
            }
        });
        
        // 备用方案：使用相机扫码
        binding.tvCameraScan.setOnClickListener(v -> {
            if (checkGeofence()) {
                startCameraScan();
            }
        });
        
        // 轨迹记录开关（使用现有按钮或添加新按钮，这里假设已有 btnToggleTrack）
        if (binding.btnToggleTrack != null) {
            binding.btnToggleTrack.setOnClickListener(v -> toggleTrackRecording());
        }
        
        // 初始时禁用按钮，等待定位和选择打卡点
        binding.btnShowQr.setEnabled(false);
        binding.tvCameraScan.setEnabled(false);
    }

    @Override
    protected void initData() {
        checkInManager = new CheckInManager();
        
        // 从 Intent 获取赛事ID
        raceId = getIntent().getStringExtra("raceId");
        
        // 【修复】统一使用 "account" 作为键名获取用户ID，与 performCheckIn() 保持一致
        userId = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
        
        if (raceId != null && !raceId.isEmpty()) {
            // 加载真实赛事数据
            loadRaceData(raceId);
        } else {
            // 兼容旧版本：加载演示数据
            loadDemoData();
        }
        
        // 注册轨迹更新广播接收器
        registerTrackUpdateReceiver();
    }

    /**
     * 加载真实赛事数据
     * 【状态同步优化】使用 Realm 实时监听，管理员修改后自动更新
     */
    private void loadRaceData(String raceId) {
        // 初始化 Realm
        realm = Realm.getDefaultInstance();
        
        // 查询并监听赛事数据
        raceResults = realm.where(Race.class)
                .equalTo("raceId", raceId)
                .findAllAsync();
        
        // 添加变化监听器
        raceChangeListener = new RealmChangeListener<RealmResults<Race>>() {
            @Override
            public void onChange(@NonNull RealmResults<Race> results) {
                if (results.isEmpty()) {
                    runOnUiThread(() -> {
                        UIUtil.showToast(CheckInActivity.this, "赛事已被删除");
                        finish();
                    });
                    return;
                }
                
                // 获取最新赛事数据
                Race race = results.first();
                if (race != null) {
                    // 复制到非托管对象（避免线程问题）
                    Race copiedRace = realm.copyFromRealm(race);
                    
                    runOnUiThread(() -> {
                        // 更新打卡点列表
                        if (copiedRace.getCheckPoints() != null && !copiedRace.getCheckPoints().isEmpty()) {
                            allCheckPoints = new java.util.ArrayList<>(copiedRace.getCheckPoints());
                            // 按顺序排序
                            allCheckPoints.sort((p1, p2) -> Integer.compare(p1.getOrderIndex(), p2.getOrderIndex()));
                            
                            // 刷新地图标记
                            mapController.clearCheckPoints();
                            mapController.addCheckPoints(allCheckPoints);
                            
                            // 如果当前没有选中打卡点，选择第一个
                            if (currentPoint == null) {
                                currentPoint = allCheckPoints.get(0);
                                updateCheckPointInfo();
                            } else {
                                // 如果当前打卡点仍存在，更新其数据
                                updateCurrentCheckPointIfExists();
                            }
                            
                            // 只在非首次加载时显示更新提示
                            if (currentPoint != null) {
                                UIUtil.showToast(CheckInActivity.this, "赛事数据已更新");
                            }
                        } else {
                            UIUtil.showToast(CheckInActivity.this, "该赛事暂无打卡点");
                        }
                    });
                }
            }
        };
        
        raceResults.addChangeListener(raceChangeListener);
    }
    
    /**
     * 【状态同步】更新当前打卡点（如果在新数据中仍存在）
     */
    private void updateCurrentCheckPointIfExists() {
        if (currentPoint == null || allCheckPoints == null) {
            return;
        }
        
        // 查找当前打卡点是否仍在列表中
        for (CheckPoint cp : allCheckPoints) {
            if (cp.getCheckPointId().equals(currentPoint.getCheckPointId())) {
                // 更新为最新数据
                currentPoint = cp;
                updateCheckPointInfo();
                
                // 重新计算距离
                if (lastLat != 0 && lastLng != 0) {
                    updateDistanceAndButton(lastLat, lastLng);
                }
                return;
            }
        }
        
        // 如果当前打卡点已被删除，切换到第一个
        if (!allCheckPoints.isEmpty()) {
            currentPoint = allCheckPoints.get(0);
            updateCheckPointInfo();
            UIUtil.showToast(this, "当前打卡点已被删除，已切换到其他打卡点");
        } else {
            currentPoint = null;
            UIUtil.showToast(this, "所有打卡点已被删除");
        }
    }

    /**
     * 加载演示数据（兼容旧版本）
     */
    private void loadDemoData() {
        currentPoint = new CheckPoint();
        currentPoint.setCheckPointId("cp-demo");
        currentPoint.setName("示例打卡点");
        currentPoint.setLatitude(30.0000);
        currentPoint.setLongitude(120.0000);
        currentPoint.setCheckRadius(50.0);
        currentPoint.setQrCodePayload("DEMO_QR");
        allCheckPoints = java.util.Collections.singletonList(currentPoint);
        mapController.addCheckPoints(allCheckPoints);
        updateCheckPointInfo();
    }

    /**
     * 更新打卡点信息显示
     */
    private void updateCheckPointInfo() {
        if (currentPoint != null) {
            String info = String.format("当前打卡点：%s（打卡半径：%.0f米）", 
                    currentPoint.getName(), 
                    currentPoint.getCheckRadius() > 0 ? currentPoint.getCheckRadius() : 50.0);
            binding.tvStatus.setText(info);
        }
    }

    /**
     * 检查地理围栏（在扫码前判断）
     * 
     * @return 是否在打卡范围内
     */
    private boolean checkGeofence() {
        if (currentPoint == null) {
            UIUtil.showToast(this, "请先选择打卡点");
            return false;
        }

        if (lastLat == 0 && lastLng == 0) {
            UIUtil.showToast(this, "正在获取位置信息，请稍候...");
            return false;
        }

        // 计算距离
        double distance = checkInManager.calculateDistance(currentPoint, lastLat, lastLng);
        double radius = currentPoint.getCheckRadius() > 0 ? currentPoint.getCheckRadius() : 50.0;

        // 判断是否在范围内
        if (distance > radius) {
            String message = String.format("未进入打卡范围\n当前距离：%.1f米\n需要进入：%.0f米内", 
                    distance, radius);
            UIUtil.showToast(this, message);
            binding.tvStatus.setText(message.replace("\n", " "));
            isInRange = false;
            return false;
        }

        isInRange = true;
        binding.tvStatus.setText(String.format("已进入打卡范围（距离：%.1f米），请扫描二维码", distance));
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapController != null) {
            mapController.onResume();
        }
        if (locationManager != null) {
            locationManager.setHighPrecision(true);
            locationManager.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapController != null) {
            mapController.onPause();
        }
        if (locationManager != null) {
            locationManager.stop();
        }
    }

    /**
     * 显示打卡二维码对话框（主要方案）
     */
    private void showQrCodeDialog() {
        if (currentPoint == null) {
            UIUtil.showToast(this, "请先选择打卡点");
            return;
        }

        // 生成二维码
        Bitmap qrBitmap = QrCodeGenerator.generateCheckPointQrCode(
                raceId != null ? raceId : "race-demo",
                currentPoint.getCheckPointId(),
                400,
                400
        );

        if (qrBitmap == null) {
            UIUtil.showToast(this, "生成二维码失败，请使用相机扫码");
            return;
        }

        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_check_in_qr, null);
        builder.setView(dialogView);
        builder.setCancelable(true);

        // 设置打卡点名称
        TextView tvCheckPointName = dialogView.findViewById(R.id.tvCheckPointName);
        tvCheckPointName.setText(currentPoint.getName());

        // 设置距离信息
        TextView tvDistance = dialogView.findViewById(R.id.tvDistance);
        double distance = checkInManager.calculateDistance(currentPoint, lastLat, lastLng);
        tvDistance.setText(String.format("距离：%.1f米", distance));

        // 显示二维码
        ImageView ivQrCode = dialogView.findViewById(R.id.ivQrCode);
        ivQrCode.setImageBitmap(qrBitmap);

        // 长按二维码进行扫描打卡
        ivQrCode.setOnLongClickListener(v -> {
            performCheckInDirectly();
            return true;
        });

        qrDialog = builder.create();

        // 关闭按钮
        MaterialButton btnClose = dialogView.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> {
            if (qrDialog != null) {
                qrDialog.dismiss();
            }
        });

        qrDialog.show();
    }

    /**
     * 直接执行打卡（长按二维码时）
     */
    private void performCheckInDirectly() {
        if (currentPoint == null) {
            UIUtil.showToast(this, "打卡点信息错误");
            return;
        }

        // 生成二维码内容（模拟扫描结果）
        String qrContent = QrCodeGenerator.generateCheckPointPayload(
                raceId != null ? raceId : "race-demo",
                currentPoint.getCheckPointId()
        );

        // 关闭对话框
        if (qrDialog != null) {
            qrDialog.dismiss();
        }

        // 执行打卡
        performCheckIn(qrContent);
    }

    /**
     * 启动相机扫码（备用方案）
     */
    private void startCameraScan() {
        ScanOptions options = new ScanOptions();
        options.setBeepEnabled(false);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("请对准打卡点二维码");
        qrLauncher.launch(options);
    }

    private void handleScanResult(ScanIntentResult result) {
        if (result == null || result.getContents() == null) {
            UIUtil.showToast(this, "未识别到二维码");
            return;
        }
        binding.tvQrContent.setText(result.getContents());
        performCheckIn(result.getContents());
    }

    private void performCheckIn(String qrContent) {
        // 【防御性检查】检查 binding 是否为空
        if (binding == null) {
            android.util.Log.e("CheckInActivity", "binding is null in performCheckIn");
            return;
        }
        
        // 获取当前用户ID
        String userId = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
        if (android.text.TextUtils.isEmpty(userId)) {
            com.example.cross_intelligence.mvc.util.UIUtil.showToast(this, "请先登录");
            return;
        }
        
        // 使用真实赛事ID，如果没有则使用演示ID
        String currentRaceId = raceId != null ? raceId : "race-demo";
        
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }
        
        // 【修复】判断打卡点类型，如果有类型则使用状态机打卡
        boolean hasType = currentPoint != null && currentPoint.getType() != null && !currentPoint.getType().isEmpty();
        
        if (hasType) {
            // 使用状态机打卡（支持起点/检查点/终点的不同逻辑）
            boolean isOffline = binding.switchOnline != null && !binding.switchOnline.isChecked();
            checkInManager.checkInWithStateMachine(currentRaceId, userId, currentPoint,
                    lastLat, lastLng, qrContent, isOffline,
                    new CheckInManager.StateCheckInCallback() {
                        @Override
                        public void onSuccess(@NonNull com.example.cross_intelligence.mvc.model.CheckInRecord record,
                                            @NonNull com.example.cross_intelligence.mvc.model.RaceSession session,
                                            @NonNull String checkPointType) {
                            runOnUiThread(() -> {
                                if (binding != null && binding.progressBar != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                
                                // 【关键修复】如果是起点，自动启动轨迹记录和计时
                                if (CheckPoint.TYPE_START.equals(checkPointType)) {
                                    startTrackingAfterStartPoint(session);
                                }
                                
                                // 【关键修复】如果是终点，自动停止轨迹记录
                                if (CheckPoint.TYPE_FINISH.equals(checkPointType)) {
                                    stopTrackingAfterFinishPoint(session);
                                }
                                
                                // 显示 Lottie 动画
                                showCheckInSuccessAnimation(record);
                                
                                // 更新状态显示
                                if (binding != null && binding.tvStatus != null && currentPoint != null) {
                                    String typeLabel = getTypeLabel(checkPointType);
                                    String successMessage = String.format("✓ %s%s打卡成功！\n打卡点：%s\n时间：%s",
                                            record.isOffline() ? "离线" : "",
                                            typeLabel,
                                            currentPoint.getName(),
                                            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                                    .format(record.getTimestamp()));
                                    binding.tvStatus.setText(successMessage);
                                }
                            });
                        }

                        @Override
                        public void onFailure(@NonNull Throwable throwable) {
                            runOnUiThread(() -> {
                                if (binding != null && binding.progressBar != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                String errorMessage = "✗ 打卡失败：" + 
                                        (throwable.getMessage() != null ? throwable.getMessage() : "未知错误");
                                if (binding != null && binding.tvStatus != null) {
                                    binding.tvStatus.setText(errorMessage);
                                }
                                UIUtil.showToast(CheckInActivity.this, errorMessage);
                            });
                        }
                    });
        } else {
            // 兼容旧版本：使用普通打卡（无类型信息）
            boolean isOffline = binding.switchOnline != null && !binding.switchOnline.isChecked();
            checkInManager.checkIn(currentRaceId, userId, currentPoint,
                    lastLat, lastLng, qrContent, isOffline,
                    new CheckInManager.CheckInCallback() {
                        @Override
                        public void onSuccess(@NonNull com.example.cross_intelligence.mvc.model.CheckInRecord record) {
                            runOnUiThread(() -> {
                                if (binding != null && binding.progressBar != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                
                                // 显示 Lottie 动画
                                showCheckInSuccessAnimation(record);
                                
                                // 更新状态显示
                                if (binding != null && binding.tvStatus != null && currentPoint != null) {
                                    String successMessage = String.format("✓ %s打卡成功！\n打卡点：%s\n时间：%s",
                                            record.isOffline() ? "离线" : "",
                                            currentPoint.getName(),
                                            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                                    .format(record.getTimestamp()));
                                    binding.tvStatus.setText(successMessage);
                                }
                            });
                        }

                        @Override
                        public void onFailure(@NonNull Throwable throwable) {
                            runOnUiThread(() -> {
                                if (binding != null && binding.progressBar != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                String errorMessage = "✗ 打卡失败：" + 
                                        (throwable.getMessage() != null ? throwable.getMessage() : "未知错误");
                                if (binding != null && binding.tvStatus != null) {
                                    binding.tvStatus.setText(errorMessage);
                                }
                                UIUtil.showToast(CheckInActivity.this, errorMessage);
                            });
                        }
                    });
        }
    }
    
    /**
     * 【新增】起点打卡成功后，自动启动轨迹记录和计时
     */
    private void startTrackingAfterStartPoint(com.example.cross_intelligence.mvc.model.RaceSession session) {
        if (session == null) {
            UIUtil.showToast(this, "会话信息错误");
            return;
        }
        
        if (raceId == null || raceId.isEmpty() || userId == null || userId.isEmpty()) {
            UIUtil.showToast(this, "缺少必要信息，无法启动轨迹记录");
            return;
        }
        
        try {
            // 自动启动轨迹记录服务
            TrackRecorderService.startTracking(this, raceId, userId);
            isTrackingEnabled = true;
            
            // 更新按钮状态
            if (binding.btnToggleTrack != null) {
                binding.btnToggleTrack.setText("停止记录轨迹");
            }
            
            // 清除之前的轨迹
            if (mapController != null) {
                mapController.clearTrack();
            }
            
            // 显示提示
            if (session.getStartTime() != null) {
                String startTime = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                        .format(session.getStartTime());
                UIUtil.showToast(this, "比赛已开始！计时器和轨迹记录已自动启动（开始时间：" + startTime + "）");
            } else {
                UIUtil.showToast(this, "比赛已开始！计时器和轨迹记录已自动启动");
            }
        } catch (Exception e) {
            android.util.Log.e("CheckInActivity", "启动轨迹记录失败", e);
            UIUtil.showToast(this, "启动轨迹记录失败：" + e.getMessage());
        }
    }
    
    /**
     * 【新增】终点打卡成功后，自动停止轨迹记录并保存成绩
     */
    private void stopTrackingAfterFinishPoint(com.example.cross_intelligence.mvc.model.RaceSession session) {
        if (session == null) {
            UIUtil.showToast(this, "会话信息错误");
            return;
        }
        
        try {
            if (isTrackingEnabled) {
                // 先停止轨迹记录服务
                TrackRecorderService.stopTracking(this);
                isTrackingEnabled = false;
                
                // 更新按钮状态
                if (binding != null && binding.btnToggleTrack != null) {
                    binding.btnToggleTrack.setText("开始记录轨迹");
                }
            }
            
            // 显示完成信息
            long totalSeconds = session.getTotalMillis() / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            String timeStr = String.format("%d分%02d秒", minutes, seconds);
            
            // 【关键】显示成绩已保存提示（Result 已在 CheckInManager.checkInWithStateMachine 中自动保存）
            String message = String.format(
                    "🎉 恭喜完成比赛！\n" +
                    "总用时：%s\n" +
                    "成绩已自动保存到「我的成绩」",
                    timeStr
            );
            
            // 使用对话框显示（更醒目）
            new AlertDialog.Builder(this)
                    .setTitle("比赛完成")
                    .setMessage(message)
                    .setPositiveButton("查看我的成绩", (dialog, which) -> {
                        // 跳转到"我的成绩"页面
                        Intent intent = new Intent(this, com.example.cross_intelligence.mvc.view.result.MyResultsActivity.class);
                        startActivity(intent);
                    })
                    .setNeutralButton("查看排行榜", (dialog, which) -> {
                        // 跳转到排行榜
                        Intent intent = new Intent(this, com.example.cross_intelligence.mvc.view.result.LeaderboardActivity.class);
                        intent.putExtra(com.example.cross_intelligence.mvc.view.result.LeaderboardActivity.EXTRA_RACE_ID, raceId);
                        startActivity(intent);
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e("CheckInActivity", "停止轨迹记录失败", e);
            UIUtil.showToast(this, "处理完成信息失败：" + e.getMessage());
        }
    }
    
    /**
     * 【新增】获取打卡点类型的显示标签
     */
    private String getTypeLabel(String checkPointType) {
        if (CheckPoint.TYPE_START.equals(checkPointType)) {
            return "起点";
        } else if (CheckPoint.TYPE_FINISH.equals(checkPointType)) {
            return "终点";
        } else if (CheckPoint.TYPE_CHECKPOINT.equals(checkPointType)) {
            return "检查点";
        }
        return "";
    }

    /**
     * 显示打卡成功 Lottie 动画
     */
    private void showCheckInSuccessAnimation(com.example.cross_intelligence.mvc.model.CheckInRecord record) {
        // 创建动画覆盖层
        ViewGroup rootView = (ViewGroup) binding.getRoot();
        successOverlay = LayoutInflater.from(this).inflate(R.layout.overlay_check_in_success, rootView, false);
        
        // 设置打卡点名称
        TextView tvCheckPointName = successOverlay.findViewById(R.id.tvCheckPointName);
        tvCheckPointName.setText(currentPoint.getName());
        
        // 获取 Lottie 动画视图
        LottieAnimationView lottieAnimation = successOverlay.findViewById(R.id.lottieAnimation);
        
        // 添加动画监听器
        lottieAnimation.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束后延迟 500ms 再移除覆盖层
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    hideSuccessAnimation();
                    // 显示详细信息对话框
                    showCheckInSuccessDialog(record);
                    // 切换到下一个打卡点
                    switchToNextCheckPoint();
                }, 500);
            }
        });
        
        // 添加覆盖层到根视图
        rootView.addView(successOverlay);
        
        // 设置初始透明度为 0
        successOverlay.setAlpha(0f);
        
        // 淡入动画
        successOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        
        // 开始播放 Lottie 动画
        lottieAnimation.playAnimation();
    }

    /**
     * 隐藏成功动画覆盖层
     */
    private void hideSuccessAnimation() {
        if (successOverlay != null) {
            // 淡出动画
            successOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        ViewGroup rootView = (ViewGroup) binding.getRoot();
                        rootView.removeView(successOverlay);
                        successOverlay = null;
                    })
                    .start();
        }
    }

    /**
     * 显示打卡成功详细信息对话框（在动画之后）
     */
    private void showCheckInSuccessDialog(com.example.cross_intelligence.mvc.model.CheckInRecord record) {
        String message = String.format(
                "打卡点：%s\n" +
                "打卡时间：%s\n" +
                "状态：%s",
                currentPoint.getName(),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                        .format(record.getTimestamp()),
                record.isOffline() ? "离线打卡" : "在线打卡"
        );

        new AlertDialog.Builder(this)
                .setTitle("✓ 打卡成功")
                .setMessage(message)
                .setPositiveButton("继续", null)
                .show();
    }

    /**
     * 切换到下一个打卡点
     */
    private void switchToNextCheckPoint() {
        if (allCheckPoints == null || allCheckPoints.isEmpty()) {
            return;
        }

        // 查找当前打卡点的索引
        int currentIndex = -1;
        for (int i = 0; i < allCheckPoints.size(); i++) {
            if (allCheckPoints.get(i).getCheckPointId().equals(currentPoint.getCheckPointId())) {
                currentIndex = i;
                break;
            }
        }

        // 切换到下一个打卡点
        if (currentIndex >= 0 && currentIndex < allCheckPoints.size() - 1) {
            currentPoint = allCheckPoints.get(currentIndex + 1);
            updateCheckPointInfo();
            updateDistanceAndButton();
            mapController.moveCamera(currentPoint.getLatitude(), currentPoint.getLongitude());
            UIUtil.showToast(this, "已切换到下一个打卡点：" + currentPoint.getName());
        } else {
            UIUtil.showToast(this, "恭喜！所有打卡点已完成！");
        }
    }

    @Override
    public void onLocationUpdate(double lat, double lng, float accuracy) {
        lastLat = lat;
        lastLng = lng;
        runOnUiThread(() -> {
            binding.tvLocation.setText(getString(R.string.location_format, lat, lng, accuracy));
            mapController.moveCamera(lat, lng);
            
            // 更新距离和按钮状态
            updateDistanceAndButton();
        });
    }

    /**
     * 更新距离显示和按钮状态（使用最后一次定位）
     */
    private void updateDistanceAndButton() {
        updateDistanceAndButton(lastLat, lastLng);
    }

    /**
     * 更新距离显示和按钮状态（指定位置）
     */
    private void updateDistanceAndButton(double lat, double lng) {
        if (currentPoint == null || (lat == 0 && lng == 0)) {
            binding.btnShowQr.setEnabled(false);
            return;
        }

        // 计算距离
        double distance = checkInManager.calculateDistance(currentPoint, lat, lng);
        double radius = currentPoint.getCheckRadius() > 0 ? currentPoint.getCheckRadius() : 50.0;

        // 更新状态显示
        if (distance <= radius) {
            isInRange = true;
            binding.btnShowQr.setEnabled(true);
            binding.tvStatus.setText(String.format("已进入打卡范围（距离：%.1f米）", distance));
        } else {
            isInRange = false;
            binding.btnShowQr.setEnabled(false);
            binding.tvStatus.setText(String.format("距离打卡点：%.1f米（需要：%.0f米内）", distance, radius));
        }
    }

    @Override
    public void onLocationError(int errorCode, String errorInfo) {
        runOnUiThread(() -> binding.tvStatus.setText(
                getString(R.string.location_error_format, errorCode, errorInfo)));
    }

    @Override
    public void onMapClicked(@NonNull LatLng latLng) {
        UIUtil.showToast(this, getString(R.string.map_click_format, latLng.latitude, latLng.longitude));
    }

    @Override
    public void onMarkerClicked(@NonNull CheckPoint point) {
        // 切换当前打卡点
        currentPoint = point;
        updateCheckPointInfo();
        updateDistanceAndButton();
        UIUtil.showToast(this, "已选择打卡点：" + point.getName());
    }

    /**
     * 注册轨迹更新广播接收器（优化版）
     * 优化：使用 Handler 避免过于频繁的 UI 更新
     */
    private void registerTrackUpdateReceiver() {
        trackUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                double lat = intent.getDoubleExtra(TrackRecorderService.EXTRA_LATITUDE, 0);
                double lng = intent.getDoubleExtra(TrackRecorderService.EXTRA_LONGITUDE, 0);
                if (lat != 0 && lng != 0) {
                    // 直接在主线程更新（广播已在主线程）
                    mapController.addTrackPoint(lat, lng);
                    
                    // 更新轨迹统计信息（可选）
                    updateTrackStats();
                }
            }
        };
        IntentFilter filter = new IntentFilter(TrackRecorderService.ACTION_TRACK_UPDATE);
        registerReceiver(trackUpdateReceiver, filter);
    }
    
    /**
     * 更新轨迹统计信息
     */
    private void updateTrackStats() {
        if (mapController != null) {
            int pointCount = mapController.getTrackPointCount();
            // 可以在这里更新 UI 显示轨迹点数量
            // 例如：binding.tvTrackStats.setText("已记录 " + pointCount + " 个轨迹点");
        }
    }

    /**
     * 切换轨迹记录
     */
    private void toggleTrackRecording() {
        if (raceId == null || raceId.isEmpty() || userId == null || userId.isEmpty()) {
            UIUtil.showToast(this, "无法启动轨迹记录：缺少必要信息");
            return;
        }

        if (isTrackingEnabled) {
            // 停止轨迹记录
            TrackRecorderService.stopTracking(this);
            isTrackingEnabled = false;
            if (binding.btnToggleTrack != null) {
                binding.btnToggleTrack.setText("开始记录轨迹");
            }
            // 停止时禁用相机跟随
            mapController.setCameraFollowEnabled(false);
            UIUtil.showToast(this, "轨迹记录已停止，共记录 " + mapController.getTrackPointCount() + " 个点");
        } else {
            // 开始轨迹记录
            // 清除之前的轨迹
            mapController.clearTrack();
            
            TrackRecorderService.startTracking(this, raceId, userId);
            isTrackingEnabled = true;
            if (binding.btnToggleTrack != null) {
                binding.btnToggleTrack.setText("停止记录轨迹");
            }
            // 启动时可选择开启相机跟随（根据需求）
            // mapController.setCameraFollowEnabled(true);
            UIUtil.showToast(this, "轨迹记录已启动");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 【状态同步】移除 Realm 监听器并关闭 Realm
        if (raceChangeListener != null && raceResults != null) {
            raceResults.removeChangeListener(raceChangeListener);
            raceChangeListener = null;
        }
        if (realm != null && !realm.isClosed()) {
            realm.close();
            realm = null;
        }
        
        // 注销广播接收器
        if (trackUpdateReceiver != null) {
            unregisterReceiver(trackUpdateReceiver);
            trackUpdateReceiver = null;
        }
        // 如果轨迹记录还在运行，停止它
        if (isTrackingEnabled) {
            TrackRecorderService.stopTracking(this);
        }
        // 释放地图资源
        if (mapController != null) {
            mapController.onDestroy();
        }
        // 释放定位资源
        if (locationManager != null) {
            locationManager.destroy();
        }
    }
}


