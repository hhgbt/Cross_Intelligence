package com.example.cross_intelligence.mvc.view.checkin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityCheckInBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.CheckInManager;
import com.example.cross_intelligence.mvc.controller.TrackManager;
import com.example.cross_intelligence.mvc.location.MapLocationManager;
import com.example.cross_intelligence.mvc.location.RaceMapController;
import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.TrackPoint;
import com.example.cross_intelligence.mvc.service.TrackRecorderService;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.MapThumbnailUtil;
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
    private boolean isTrackingEnabled = false; // 轨迹记录是否开启
    private boolean isPaused = false; // 是否暂停
    private BroadcastReceiver trackUpdateReceiver; // 轨迹更新广播接收器
    
    // 【状态同步】Realm 实时监听
    private Realm realm;
    private RealmResults<Race> raceResults;
    private RealmChangeListener<RealmResults<Race>> raceChangeListener;
    
    // 实时更新面板相关
    private Handler updateHandler; // 用于定时更新面板
    private Runnable updateRunnable; // 更新面板的Runnable
    private Date raceStartTime; // 比赛开始时间
    private TrackManager trackManager; // 轨迹管理器，用于计算里程
    private long pausedTimeMillis = 0; // 暂停时累计的时间（毫秒）
    private Date pauseStartTime; // 暂停开始时间
    private Handler pauseHandler; // 用于处理20分钟自动继续
    private Runnable autoResumeRunnable; // 20分钟后自动继续的Runnable
    private boolean hasInitialLocation = false; // 是否已经进行过初始定位
    private List<CheckInRecord> playerCheckInRecords = new java.util.ArrayList<>(); // 选手实际打卡记录

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), this::handleScanResult);

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置状态栏为白色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.view.Window window = getWindow();
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.view.View decorView = window.getDecorView();
                // 使用新的 WindowInsetsController API (Android 11+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    window.getInsetsController().setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // 兼容旧版本 (Android 6.0 - 10)
                    decorView.setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
                }
            }
        }
        
        binding = ActivityCheckInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapController = new RaceMapController(binding.mapView);
        mapController.onCreate(savedInstanceState);
        mapController.setMapEventListener(this);
        initView();
        initData();
        locationManager = new MapLocationManager(this, this);
        
        // 初始化轨迹管理器
        trackManager = new TrackManager();
        
        // 初始化更新Handler
        updateHandler = new Handler(Looper.getMainLooper());
        pauseHandler = new Handler(Looper.getMainLooper());
        
        // 初始化更新面板的Runnable
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPaused && isTrackingEnabled && raceStartTime != null) {
                    updateStatsPanel();
                    updateHandler.postDelayed(this, 1000); // 每秒更新一次
                }
            }
        };
    }

    @Override
    protected void initView() {
        // 返回按钮
        binding.btnBack.setOnClickListener(v -> finish());
        
        // 点击悬浮二维码按钮查看打卡二维码（无论是否在打卡范围内都可以查看）
        binding.btnShowQr.setOnClickListener(v -> {
            showQrCodeDialog();
        });
        
        // 确保二维码图标居中显示
        binding.btnShowQr.post(() -> {
            Drawable originalIcon = ContextCompat.getDrawable(this, R.drawable.ic_qr_code);
            if (originalIcon != null) {
                // 计算 padding 以确保图标居中（约 18% 的按钮尺寸）
                int padding = (int) (binding.btnShowQr.getWidth() * 0.18);
                InsetDrawable insetDrawable = new InsetDrawable(originalIcon, padding, padding, padding, padding);
                binding.btnShowQr.setImageDrawable(insetDrawable);
            }
        });
        
        // 暂停/继续按钮
        if (binding.btnToggleTrack != null) {
            binding.btnToggleTrack.setText("暂停");
            binding.btnToggleTrack.setOnClickListener(v -> togglePauseResume());
            binding.btnToggleTrack.setVisibility(View.GONE); // 初始隐藏，起点打卡后显示
            // 初始设置为暂停状态样式（橙底白字，暂停图标）
            updatePauseButtonStyle();
        }
        
        // 按钮始终可用，允许随时查看二维码
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
                            
                            // 如果选手已经开始比赛（有打卡记录），隐藏管理员创建的打卡点标记
                            if (raceId != null && userId != null) {
                                List<CheckInRecord> existingRecords = checkInManager.queryCheckInRecords(raceId, userId);
                                if (!existingRecords.isEmpty()) {
                                    // 选手已打卡，只显示选手实际打卡位置
                                    updateCheckInMarkers();
                                } else {
                                    // 选手未打卡，显示管理员创建的打卡点
                                    mapController.clearCheckPoints();
                                    mapController.addCheckPoints(allCheckPoints);
                                }
                            } else {
                                // 刷新地图标记（默认显示管理员创建的打卡点）
                                mapController.clearCheckPoints();
                                mapController.addCheckPoints(allCheckPoints);
                            }
                            
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
     * 显示打卡二维码对话框（无论是否在打卡范围内都可以查看）
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
        if (lastLat != 0 && lastLng != 0) {
            double distance = checkInManager.calculateDistance(currentPoint, lastLat, lastLng);
            tvDistance.setText(String.format("距离：%.1f米", distance));
        } else {
            tvDistance.setText("距离：定位中...");
        }

        // 显示二维码
        ImageView ivQrCode = dialogView.findViewById(R.id.ivQrCode);
        ivQrCode.setImageBitmap(qrBitmap);

        // 长按二维码进行扫描打卡
        ivQrCode.setOnLongClickListener(v -> {
            performCheckInDirectly();
            return true;
        });

        // 启动扫描线动画（上下往复移动）
        View scanLine = dialogView.findViewById(R.id.scanLine);
        FrameLayout qrContainer = dialogView.findViewById(R.id.qrContainer);
        if (scanLine != null && qrContainer != null) {
            // 等待布局完成后获取实际高度
            qrContainer.post(() -> {
                int containerHeight = qrContainer.getHeight();
                int scanLineHeight = scanLine.getHeight();
                float maxTranslation = containerHeight - scanLineHeight - qrContainer.getPaddingTop() - qrContainer.getPaddingBottom();
                
                android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofFloat(
                        scanLine, "translationY", 0, maxTranslation);
                animator.setDuration(2000);
                animator.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
                animator.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.start();
            });
        }

        qrDialog = builder.create();

        // 备用方案：使用相机扫码
        TextView tvCameraScan = dialogView.findViewById(R.id.tvCameraScan);
        tvCameraScan.setOnClickListener(v -> {
            if (qrDialog != null) {
                qrDialog.dismiss();
            }
            startCameraScan();
        });

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
     * 如果不在打卡范围内，弹窗提醒
     */
    private void performCheckInDirectly() {
        if (currentPoint == null) {
            UIUtil.showToast(this, "打卡点信息错误");
            return;
        }

        // 检查是否在打卡范围内
        if (lastLat == 0 && lastLng == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("定位失败")
                    .setMessage("正在获取位置信息，请稍候...")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        // 计算距离
        double distance = checkInManager.calculateDistance(currentPoint, lastLat, lastLng);
        double radius = currentPoint.getCheckRadius() > 0 ? currentPoint.getCheckRadius() : 50.0;

        // 判断是否在范围内
        if (distance > radius) {
            String message = String.format("未在打卡范围内\n当前距离：%.1f米\n需要进入：%.0f米内", 
                    distance, radius);
            new AlertDialog.Builder(this)
                    .setTitle("打卡失败")
                    .setMessage(message)
                    .setPositiveButton("确定", null)
                    .show();
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
            checkInManager.checkInWithStateMachine(currentRaceId, userId, currentPoint,
                    lastLat, lastLng, qrContent,
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
                                
                                // 打卡成功后，隐藏管理员创建的打卡点标记，只显示选手实际打卡位置
                                updateCheckInMarkers();
                                
                                // 如果是起点，显示特殊弹窗（放大居中，无需确认）并立即切换打卡点
                                if (CheckPoint.TYPE_START.equals(checkPointType)) {
                                    // 保存起点名称（用于显示）
                                    String startPointName = currentPoint != null ? currentPoint.getName() : "起点";
                                    // 立即切换到下一个打卡点（在显示弹窗前）
                                    switchToNextCheckPoint();
                                    // 显示弹窗
                                    showStartPointSuccessDialog();
                                    // 更新状态显示
                                    if (binding != null && binding.tvStatus != null) {
                                        String successMessage = String.format("✓ 起点打卡成功！\n打卡点：%s\n时间：%s",
                                                startPointName,
                                                new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                                        .format(record.getTimestamp()));
                                        binding.tvStatus.setText(successMessage);
                                    }
                                } else if (CheckPoint.TYPE_CHECKPOINT.equals(checkPointType)) {
                                    // 检查点打卡成功，显示弹窗
                                    String checkpointName = currentPoint != null ? currentPoint.getName() : "检查点";
                                    showCheckpointSuccessDialog(checkpointName);
                                    // 更新状态显示
                                    if (binding != null && binding.tvStatus != null && currentPoint != null) {
                                        String successMessage = String.format("✓ 检查点打卡成功！\n打卡点：%s\n时间：%s",
                                                currentPoint.getName(),
                                                new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                                        .format(record.getTimestamp()));
                                        binding.tvStatus.setText(successMessage);
                                    }
                                } else {
                                    // 其他类型（如终点手动打卡）
                                    // 更新状态显示
                                    if (binding != null && binding.tvStatus != null && currentPoint != null) {
                                        String typeLabel = getTypeLabel(checkPointType);
                                        String successMessage = String.format("✓ %s打卡成功！\n打卡点：%s\n时间：%s",
                                                typeLabel,
                                                currentPoint.getName(),
                                                new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                                        .format(record.getTimestamp()));
                                        binding.tvStatus.setText(successMessage);
                                    }
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
            checkInManager.checkIn(currentRaceId, userId, currentPoint,
                    lastLat, lastLng, qrContent,
                    new CheckInManager.CheckInCallback() {
                        @Override
                        public void onSuccess(@NonNull com.example.cross_intelligence.mvc.model.CheckInRecord record) {
                            runOnUiThread(() -> {
                                if (binding != null && binding.progressBar != null) {
                                    binding.progressBar.setVisibility(View.GONE);
                                }
                                
                                // 更新状态显示
                                if (binding != null && binding.tvStatus != null && currentPoint != null) {
                                    String successMessage = String.format("✓ 打卡成功！\n打卡点：%s\n时间：%s",
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
            // 保存开始时间
            raceStartTime = session.getStartTime();
            if (raceStartTime == null) {
                raceStartTime = new Date();
            }
            
            // 自动启动轨迹记录服务
            TrackRecorderService.startTracking(this, raceId, userId);
            isTrackingEnabled = true;
            isPaused = false;
            
            // 显示暂停/继续按钮
            if (binding.btnToggleTrack != null) {
                binding.btnToggleTrack.setText("暂停");
                binding.btnToggleTrack.setVisibility(View.VISIBLE);
                // 设置为暂停状态样式（橙底白字，暂停图标）
                updatePauseButtonStyle();
            }
            // 比赛开始后，将“距离打卡点…”文字移动到卡片上方区域
            if (binding.tvStatus != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.tvStatus.getLayoutParams();
                lp.verticalBias = 0f; // 顶部
                binding.tvStatus.setLayoutParams(lp);
            }
            
            // 清除之前的轨迹
            if (mapController != null) {
                mapController.clearTrack();
            }
            
            // 隐藏管理员创建的打卡点标记，只显示选手实际打卡位置
            updateCheckInMarkers();
            
            // 启动实时更新面板
            updateHandler.post(updateRunnable);
        } catch (Exception e) {
            android.util.Log.e("CheckInActivity", "启动轨迹记录失败", e);
            UIUtil.showToast(this, "启动轨迹记录失败：" + e.getMessage());
        }
    }
    
    /**
     * 显示起点打卡成功弹窗（放大居中，无需确认）
     */
    private void showStartPointSuccessDialog() {
        showSuccessDialog("起点打卡成功，开始比赛吧！");
    }
    
    /**
     * 显示终点到达弹窗（放大居中，无需确认）
     */
    private void showFinishPointSuccessDialog() {
        showSuccessDialog("已到达终点，恭喜你完成比赛！");
    }
    
    /**
     * 显示检查点打卡成功弹窗（放大居中，无需确认）
     */
    private void showCheckpointSuccessDialog(String checkpointName) {
        showSuccessDialog("检查点 " + checkpointName + " 打卡成功！");
    }
    
    /**
     * 通用成功弹窗（放大居中，无需确认）
     */
    private void showSuccessDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_start_success, null);
        if (dialogView == null) {
            // 如果布局文件不存在，使用简单的TextView
            TextView textView = new TextView(this);
            textView.setText(message);
            textView.setTextSize(20);
            textView.setPadding(60, 40, 60, 40);
            textView.setGravity(android.view.Gravity.CENTER);
            builder.setView(textView);
        } else {
            // 设置消息文本
            TextView tvMessage = dialogView.findViewById(R.id.tvMessage);
            TextView tvSubMessage = dialogView.findViewById(R.id.tvSubMessage);
            if (tvMessage != null) {
                tvMessage.setText(message);
                // 如果是起点，显示副消息；其他情况隐藏
                if (tvSubMessage != null) {
                    if (message.contains("起点")) {
                        tvSubMessage.setVisibility(View.VISIBLE);
                    } else {
                        tvSubMessage.setVisibility(View.GONE);
                    }
                }
            }
            builder.setView(dialogView);
        }
        
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        
        // 设置对话框窗口属性，使其放大居中
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
            window.setGravity(android.view.Gravity.CENTER);
        }
        
        dialog.show();
        
        // 3秒后自动关闭（无需确认）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }, 3000);
    }
    
    /**
     * 更新统计面板（里程和时间）
     */
    private void updateStatsPanel() {
        if (binding == null || raceStartTime == null) {
            return;
        }
        
        // 计算已用时间（排除暂停时间）
        long currentTime = System.currentTimeMillis();
        long elapsedMillis = currentTime - raceStartTime.getTime() - pausedTimeMillis;
        if (isPaused && pauseStartTime != null) {
            elapsedMillis -= (currentTime - pauseStartTime.getTime());
        }
        
        // 更新时间显示
        if (binding.tvElapsedTime != null) {
            long totalSeconds = elapsedMillis / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            binding.tvElapsedTime.setText(timeStr);
        }
        
        // 计算并更新里程
        if (binding.tvDistance != null && trackManager != null) {
            List<TrackPoint> trackPoints = trackManager.queryTrack(raceId, userId);
            double totalDistance = 0.0;
            if (trackPoints != null && trackPoints.size() > 1) {
                for (int i = 1; i < trackPoints.size(); i++) {
                    TrackPoint prev = trackPoints.get(i - 1);
                    TrackPoint curr = trackPoints.get(i);
                    totalDistance += DistanceUtil.distanceMeters(
                            prev.getLatitude(), prev.getLongitude(),
                            curr.getLatitude(), curr.getLongitude()
                    );
                }
            }
            // 转换为公里，保留2位小数
            double distanceKm = totalDistance / 1000.0;
            binding.tvDistance.setText(String.format("%.2f km", distanceKm));
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
                
                // 比赛结束后隐藏暂停按钮
                if (binding != null && binding.btnToggleTrack != null) {
                    binding.btnToggleTrack.setVisibility(View.GONE);
                }
            }
            
            // 自动保存地图缩略图（静默保存）
            saveResultThumbnail(session, () -> {
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
                new AlertDialog.Builder(CheckInActivity.this)
                        .setTitle("比赛完成")
                        .setMessage(message)
                        .setPositiveButton("查看我的成绩", (dialog, which) -> {
                            // 跳转到"我的成绩"页面
                            Intent intent = new Intent(CheckInActivity.this, com.example.cross_intelligence.mvc.view.result.MyResultsActivity.class);
                            startActivity(intent);
                        })
                        .setNeutralButton("查看排行榜", (dialog, which) -> {
                            // 跳转到排行榜
                            navigateToRaceResults(raceId);
                        })
                        .setNegativeButton("关闭", null)
                        .show();
            });
        } catch (Exception e) {
            android.util.Log.e("CheckInActivity", "停止轨迹记录失败", e);
            UIUtil.showToast(this, "处理完成信息失败：" + e.getMessage());
        }
    }
    
    /**
     * 保存结果缩略图（自动静默保存）
     */
    private void saveResultThumbnail(com.example.cross_intelligence.mvc.model.RaceSession session, Runnable onComplete) {
        if (mapController == null || mapController.getAMap() == null || mapController.getMapView() == null) {
            // 地图未初始化，直接完成
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        AMap aMap = mapController.getAMap();
        TrackManager trackManager = new TrackManager();
        
        // 获取轨迹点
        List<TrackPoint> trackPoints = trackManager.queryTrack(raceId, userId);
        
        if (trackPoints == null || trackPoints.isEmpty()) {
            // 没有轨迹点，直接完成
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        // 第一步：调整视野包含所有轨迹点
        adjustMapViewToFitTrack(aMap, trackPoints, () -> {
            // 等待一小段时间确保地图渲染完成
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // 第二步：监听地图加载完成，然后截图
                aMap.setOnMapLoadedListener(() -> {
                    // 第三步：静默截屏
                    captureResultScreenshot(session, onComplete);
                });
            }, 300); // 延迟300ms确保地图渲染完成
        });
    }
    
    /**
     * 调整地图视野以包含所有轨迹点
     */
    private void adjustMapViewToFitTrack(AMap aMap, List<TrackPoint> trackPoints, Runnable onComplete) {
        if (trackPoints.isEmpty() || aMap == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        // 构建包含所有轨迹点的边界
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (TrackPoint point : trackPoints) {
            boundsBuilder.include(new LatLng(point.getLatitude(), point.getLongitude()));
        }
        LatLngBounds bounds = boundsBuilder.build();
        
        // 调整视野，添加边距
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100);
        aMap.moveCamera(cameraUpdate);
        
        // 等待地图移动完成
        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                // 相机正在移动
            }
            
            @Override
            public void onCameraChangeFinish(CameraPosition cameraPosition) {
                // 相机移动完成，移除监听器
                aMap.setOnCameraChangeListener(null);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
    
    /**
     * 截取结果截图并保存
     */
    private void captureResultScreenshot(com.example.cross_intelligence.mvc.model.RaceSession session, Runnable onComplete) {
        AMap aMap = mapController.getAMap();
        if (aMap == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        // 获取 resultId（从最新的 Result 中获取）
        io.realm.Realm realm = io.realm.Realm.getDefaultInstance();
        com.example.cross_intelligence.mvc.model.Result latestResult = realm.where(com.example.cross_intelligence.mvc.model.Result.class)
                .equalTo("raceId", raceId)
                .equalTo("userId", userId)
                .findAll()
                .sort("elapsedSeconds")
                .last();
        
        if (latestResult == null) {
            realm.close();
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        String resultId = latestResult.getResultId();
        realm.close();
        
        aMap.getMapScreenShot(new AMap.OnMapScreenShotListener() {
            @Override
            public void onMapScreenShot(Bitmap bitmap) {
                if (bitmap != null) {
                    // 第四步：异步保存
                    new Thread(() -> {
                        String thumbnailPath = MapThumbnailUtil.saveThumbnailFromBitmap(
                                CheckInActivity.this, resultId, bitmap);
                        
                        // 更新 Result 的缩略图路径
                        if (thumbnailPath != null) {
                            io.realm.Realm updateRealm = io.realm.Realm.getDefaultInstance();
                            updateRealm.executeTransaction(r -> {
                                com.example.cross_intelligence.mvc.model.Result result = r.where(com.example.cross_intelligence.mvc.model.Result.class)
                                        .equalTo("resultId", resultId)
                                        .findFirst();
                                if (result != null) {
                                    result.setThumbnailPath(thumbnailPath);
                                }
                            });
                            updateRealm.close();
                        }
                        
                        // 保存完成后继续显示完成对话框
                        runOnUiThread(() -> {
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        });
                    }).start();
                } else {
                    // 截图失败，继续显示完成对话框
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
            
            @Override
            public void onMapScreenShot(Bitmap bitmap, int i) {
                // 兼容旧版本接口，但为了防止双重触发，这里留空
            }
        });
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
     * 显示打卡成功详细信息对话框
     */
    private void showCheckInSuccessDialog(com.example.cross_intelligence.mvc.model.CheckInRecord record) {
        String message = String.format(
                "打卡点：%s\n" +
                "打卡时间：%s",
                currentPoint.getName(),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                        .format(record.getTimestamp())
        );

        new AlertDialog.Builder(this)
                .setTitle("✓ 打卡成功")
                .setMessage(message)
                .setPositiveButton("继续", null)
                .show();
    }

    /**
     * 更新打卡点标记：
     * - 选手尚未在某个打卡点打卡前：显示该管理员创建的打卡点
     * - 选手完成该点打卡后：只显示选手自己的打卡位置，该管理员打卡点标记可消失
     */
    private void updateCheckInMarkers() {
        if (raceId == null || userId == null) {
            return;
        }

        // 查询选手的所有打卡记录
        playerCheckInRecords = checkInManager.queryCheckInRecords(raceId, userId);

        // 根据打卡记录隐藏已完成的管理员打卡点，仅保留尚未打卡的打卡点
        if (allCheckPoints != null && !allCheckPoints.isEmpty()) {
            java.util.Set<String> checkedPointIds = new java.util.HashSet<>();
            for (CheckInRecord record : playerCheckInRecords) {
                if (record.getCheckPointId() != null) {
                    checkedPointIds.add(record.getCheckPointId());
                }
            }

            java.util.List<CheckPoint> remainingPoints = new java.util.ArrayList<>();
            for (CheckPoint cp : allCheckPoints) {
                if (cp.getCheckPointId() != null && !checkedPointIds.contains(cp.getCheckPointId())) {
                    remainingPoints.add(cp);
                }
            }

            mapController.clearCheckPoints();
            if (!remainingPoints.isEmpty()) {
                mapController.addCheckPoints(remainingPoints);
            }
        } else {
            mapController.clearCheckPoints();
        }

        // 显示选手实际打卡位置标记
        if (!playerCheckInRecords.isEmpty()) {
            mapController.addCheckInRecords(playerCheckInRecords);
        }
    }
    
    /**
     * 切换到下一个打卡点（立即切换，不显示已完成的打卡点）
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
            
            // 更新二维码对话框（如果已打开）
            if (qrDialog != null && qrDialog.isShowing()) {
                qrDialog.dismiss();
                showQrCodeDialog();
            }
        } else {
            UIUtil.showToast(this, "恭喜！所有打卡点已完成！");
        }
    }

    @Override
    public void onLocationUpdate(double lat, double lng, float accuracy) {
        lastLat = lat;
        lastLng = lng;
        runOnUiThread(() -> {
            // 只在第一次定位时移动地图到定位位置，后续不再自动跟随
            if (!hasInitialLocation) {
                mapController.moveCamera(lat, lng);
                hasInitialLocation = true;
            }
            
            // 更新距离和按钮状态
            updateDistanceAndButton();
            
            // 检查是否进入终点范围（自动结束）
            if (currentPoint != null && CheckPoint.TYPE_FINISH.equals(currentPoint.getType()) 
                    && isTrackingEnabled && !isPaused) {
                checkAutoFinish(lat, lng);
            }
        });
    }
    
    /**
     * 检查是否自动结束比赛（进入终点范围）
     */
    private void checkAutoFinish(double lat, double lng) {
        if (currentPoint == null || !CheckPoint.TYPE_FINISH.equals(currentPoint.getType())) {
            return;
        }
        
        // 检查是否在终点打卡范围内
        double distance = checkInManager.calculateDistance(currentPoint, lat, lng);
        double radius = currentPoint.getCheckRadius() > 0 ? currentPoint.getCheckRadius() : 10.0;
        
        if (distance <= radius) {
            // 自动结束比赛
            autoFinishRace(lat, lng);
        }
    }
    
    /**
     * 自动结束比赛
     */
    private void autoFinishRace(double lat, double lng) {
        if (raceId == null || userId == null || currentPoint == null) {
            return;
        }
        
        // 使用finishRace方法自动结束
        checkInManager.finishRace(raceId, userId, currentPoint, lat, lng,
                new CheckInManager.RaceFinishCallback() {
                    @Override
                    public void onFinished(@NonNull Result result) {
                        runOnUiThread(() -> {
                            // 停止轨迹记录
                            if (isTrackingEnabled) {
                                TrackRecorderService.stopTracking(CheckInActivity.this);
                                isTrackingEnabled = false;
                                updateHandler.removeCallbacks(updateRunnable);
                            }
                            
                            // 隐藏暂停按钮
                            if (binding.btnToggleTrack != null) {
                                binding.btnToggleTrack.setVisibility(View.GONE);
                            }
                            
                            // 显示终点到达弹窗（和起点打卡提醒样式一样）
                            showFinishPointSuccessDialog();
                            
                            // 显示完成信息
                            long totalSeconds = result.getElapsedSeconds();
                            long minutes = totalSeconds / 60;
                            long seconds = totalSeconds % 60;
                            String timeStr = String.format("%d分%02d秒", minutes, seconds);
                            
                            String message = String.format(
                                    "🎉 恭喜完成比赛！\n" +
                                    "总用时：%s\n" +
                                    "成绩已自动保存到「我的成绩」",
                                    timeStr
                            );
                            
                            // 延迟显示完成对话框，让终点弹窗先显示
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                new AlertDialog.Builder(CheckInActivity.this)
                                        .setTitle("比赛完成")
                                        .setMessage(message)
                                        .setPositiveButton("查看我的成绩", (dialog, which) -> {
                                            Intent intent = new Intent(CheckInActivity.this, com.example.cross_intelligence.mvc.view.result.MyResultsActivity.class);
                                            startActivity(intent);
                                        })
                                        .setNeutralButton("查看排行榜", (dialog, which) -> {
                                            navigateToRaceResults(raceId);
                                        })
                                        .setNegativeButton("关闭", null)
                                        .setCancelable(false)
                                        .show();
                            }, 3500); // 3.5秒后显示（终点弹窗3秒后关闭）
                        });
                    }
                    
                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        runOnUiThread(() -> {
                            UIUtil.showToast(CheckInActivity.this, "自动结束失败：" + throwable.getMessage());
                        });
                    }
                });
    }

    /**
     * 更新距离显示和按钮状态（使用最后一次定位）
     */
    private void updateDistanceAndButton() {
        updateDistanceAndButton(lastLat, lastLng);
    }

    /**
     * 更新距离显示和状态（按钮始终可用）
     */
    private void updateDistanceAndButton(double lat, double lng) {
        if (currentPoint == null || (lat == 0 && lng == 0)) {
            binding.tvStatus.setText("状态：待打卡");
            return;
        }

        // 计算距离
        double distance = checkInManager.calculateDistance(currentPoint, lat, lng);
        double radius = currentPoint.getCheckRadius() > 0 ? currentPoint.getCheckRadius() : 50.0;

        // 更新状态显示（按钮始终可用，不根据距离禁用）
        if (distance <= radius) {
            isInRange = true;
            binding.tvStatus.setText(String.format("已进入打卡范围（距离：%.1f米）", distance));
        } else {
            isInRange = false;
            binding.tvStatus.setText(String.format("距离打卡点：%.1f米（需要：%.0f米内）", distance, radius));
        }
    }

    @Override
    public void onLocationError(int errorCode, String errorInfo) {
        runOnUiThread(() -> {
            // 定位失败时弹窗显示（红色标签提醒）
            String errorMessage = getString(R.string.location_error_format, errorCode, errorInfo);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("定位失败")
                    .setMessage(errorMessage)
                    .setPositiveButton("确定", null)
                    .create();
            dialog.show();
            // 设置标题为红色
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) {
                titleView.setTextColor(0xFFFF0000); // 红色
            }
        });
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
        // Android 13+ (API 33+) 需要明确指定接收器标志
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trackUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(trackUpdateReceiver, filter);
        }
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
     * 切换暂停/继续
     */
    private void togglePauseResume() {
        if (!isTrackingEnabled) {
            return;
        }
        
        if (isPaused) {
            // 继续比赛
            resumeRace();
        } else {
            // 暂停比赛
            pauseRace();
        }
    }
    
    /**
     * 暂停比赛
     */
    private void pauseRace() {
        isPaused = true;
        pauseStartTime = new Date();
        
        // 停止轨迹记录服务
        TrackRecorderService.stopTracking(this);
        
        // 更新按钮为继续状态（绿底白字，继续图标）
        if (binding.btnToggleTrack != null) {
            binding.btnToggleTrack.setText("继续");
            updateResumeButtonStyle();
        }
        
        // 停止面板更新
        updateHandler.removeCallbacks(updateRunnable);
        
        // 显示暂停提示
        new AlertDialog.Builder(this)
                .setTitle("已暂停比赛")
                .setMessage("已暂停比赛，可暂停时间：20分钟")
                .setPositiveButton("确定", null)
                .show();
        
        // 设置20分钟后自动继续
        if (autoResumeRunnable != null) {
            pauseHandler.removeCallbacks(autoResumeRunnable);
        }
        autoResumeRunnable = () -> {
            if (isPaused) {
                resumeRace();
                UIUtil.showToast(this, "暂停时间已达20分钟，已自动继续比赛");
            }
        };
        pauseHandler.postDelayed(autoResumeRunnable, 20 * 60 * 1000); // 20分钟
    }
    
    /**
     * 继续比赛
     */
    private void resumeRace() {
        if (!isPaused) {
            return;
        }
        
        isPaused = false;
        
        // 计算暂停时间并累加
        if (pauseStartTime != null) {
            long pauseDuration = System.currentTimeMillis() - pauseStartTime.getTime();
            pausedTimeMillis += pauseDuration;
            pauseStartTime = null;
        }
        
        // 重新启动轨迹记录服务
        TrackRecorderService.startTracking(this, raceId, userId);
        
        // 更新按钮为暂停状态（橙底白字，暂停图标）
        if (binding.btnToggleTrack != null) {
            binding.btnToggleTrack.setText("暂停");
            updatePauseButtonStyle();
        }
        
        // 恢复面板更新
        updateHandler.post(updateRunnable);
        
        // 取消自动继续
        if (autoResumeRunnable != null) {
            pauseHandler.removeCallbacks(autoResumeRunnable);
            autoResumeRunnable = null;
        }
    }
    
    /**
     * 更新暂停按钮样式：橙底白字，暂停图标
     */
    private void updatePauseButtonStyle() {
        if (binding.btnToggleTrack != null) {
            binding.btnToggleTrack.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.trail_orange)));
            binding.btnToggleTrack.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            binding.btnToggleTrack.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_pause));
            binding.btnToggleTrack.setIconTint(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.white)));
        }
    }
    
    /**
     * 更新继续按钮样式：绿底白字，继续图标
     */
    private void updateResumeButtonStyle() {
        if (binding.btnToggleTrack != null) {
            binding.btnToggleTrack.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.forest_green)));
            binding.btnToggleTrack.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            binding.btnToggleTrack.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_resume));
            binding.btnToggleTrack.setIconTint(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.white)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 停止更新面板
        if (updateHandler != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        
        // 取消自动继续
        if (pauseHandler != null && autoResumeRunnable != null) {
            pauseHandler.removeCallbacks(autoResumeRunnable);
        }
        
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

        // 恢复状态文字默认位置（纵向居中）
        if (binding != null && binding.tvStatus != null) {
            android.view.ViewGroup.LayoutParams lpGeneric = binding.tvStatus.getLayoutParams();
            if (lpGeneric instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) lpGeneric;
                lp.verticalBias = 0.5f;
                binding.tvStatus.setLayoutParams(lp);
            }
        }
    }
    
    /**
     * 跳转到成绩排行榜页面
     */
    private void navigateToRaceResults(String raceId) {
        Intent intent = new Intent(CheckInActivity.this, com.example.cross_intelligence.mvc.view.admin.AdminRaceResultsActivity.class);
        intent.putExtra(com.example.cross_intelligence.mvc.view.admin.AdminRaceResultsActivity.EXTRA_RACE_ID, raceId);
        // 获取赛事名称
        com.example.cross_intelligence.mvc.model.Race race = new com.example.cross_intelligence.mvc.controller.RaceManager().getRaceById(raceId);
        if (race != null && race.getName() != null) {
            intent.putExtra(com.example.cross_intelligence.mvc.view.admin.AdminRaceResultsActivity.EXTRA_RACE_NAME, race.getName());
        }
        startActivity(intent);
    }
}


