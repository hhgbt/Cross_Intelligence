package com.example.cross_intelligence.mvc.view.race;

import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapOptions;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityRaceDetailBinding;
import com.example.cross_intelligence.databinding.DialogCheckpointMapBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.MapThumbnailUtil;
import com.example.cross_intelligence.mvc.util.QrCodeGenerator;
import com.example.cross_intelligence.mvc.util.QrCodeUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 赛事详情页面：显示赛事的完整信息
 */
public class RaceDetailActivity extends BaseActivity {

    private ActivityRaceDetailBinding binding;
    private RaceManager raceManager;
    private CheckpointAdapter adapter;
    private List<CheckPoint> checkPoints = new ArrayList<>();
    private Race currentRace;
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private static final int REQUEST_WRITE_STORAGE = 1001;
    private Bitmap pendingSaveBitmap;
    private String pendingSaveFileName;
    private AlertDialog mapDialog;
    private MapView dialogMapView;
    private AMap dialogAMap;
    private AlertDialog routeMapDialog;
    private MapView routeMapView;
    private AMap routeAMap;
    private Polyline routePolyline;

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置状态栏为白色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                // 使用新的 WindowInsetsController API (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.getInsetsController().setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // 兼容旧版本 (Android 6.0 - 10)
                    decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
                }
            }
        }
        
        binding = ActivityRaceDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        raceManager = new RaceManager();
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置工具栏返回按钮（保留一个返回入口）
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // 完全隐藏标题，不显示应用名称
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setTitle("");
        }
        // 设置 CollapsingToolbarLayout 的标题为赛事名称（右上角显示）
        // 标题会在 initData 中设置，这里先保持启用状态
        if (binding.collapsingToolbar != null) {
            binding.collapsingToolbar.setTitleEnabled(true);
            // 标题颜色设置为白色，确保在背景图上可见
            binding.collapsingToolbar.setExpandedTitleColor(ContextCompat.getColor(this, android.R.color.white));
            binding.collapsingToolbar.setCollapsedTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
        // 设置返回箭头为白色
        binding.toolbar.setNavigationIconTint(ContextCompat.getColor(this, android.R.color.white));
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 初始化打卡点列表（只读模式，不显示删除按钮）
        adapter = new CheckpointAdapter(checkPoints, null);
        adapter.setOnItemLocationClickListener(this::showCheckpointMapDialog);
        binding.rvCheckpoints.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpoints.setAdapter(adapter);

    }

    @Override
    protected void initData() {
        String raceId = getIntent().getStringExtra("raceId");
        android.util.Log.d("RaceDetailActivity", "========== initData 开始 ==========");
        android.util.Log.d("RaceDetailActivity", "从 Intent 获取的 raceId: " + raceId);
        
        if (raceId == null) {
            android.util.Log.e("RaceDetailActivity", "raceId 为 null，退出");
            UIUtil.showToast(this, "赛事ID不存在");
            finish();
            return;
        }

        currentRace = raceManager.getRaceById(raceId);
        if (currentRace == null) {
            android.util.Log.e("RaceDetailActivity", "根据 raceId 未找到赛事，raceId: " + raceId);
            UIUtil.showToast(this, "赛事不存在");
            finish();
            return;
        }
        
        android.util.Log.d("RaceDetailActivity", "成功加载赛事: " + currentRace.getName());
        android.util.Log.d("RaceDetailActivity", "赛事 thumbnailPath: " + currentRace.getThumbnailPath());

        // 设置右上角赛事名称（CollapsingToolbarLayout 标题）
        if (binding.collapsingToolbar != null && currentRace.getName() != null) {
            binding.collapsingToolbar.setTitle(currentRace.getName());
        }

        // 显示基本信息
        String description = currentRace.getDescription();
        if (description != null && !description.isEmpty()) {
            binding.tvDescription.setText(description);
        } else {
            binding.tvDescription.setText("暂无描述");
        }

        // 加载头部背景图片（轨迹缩略图）
        loadHeroImage(currentRace);

        // 加载打卡点
        if (currentRace.getCheckPoints() != null) {
            checkPoints.clear();
            checkPoints.addAll(currentRace.getCheckPoints());
            // 按顺序排序
            checkPoints.sort(Comparator.comparingInt(CheckPoint::getOrderIndex));
            adapter.notifyDataSetChanged();
        }

        // 显示报名人数（两个位置）
        updateSignupCount();

        // 显示比赛时间（在核心数据栏）
        updateTimeRange();
        
        // 显示比赛路线
        updateRoute();
        
        // 加载路线图（轨迹缩略图）
        loadRouteThumbnail(currentRace);
    }


    /**
     * 更新报名人数（宣传图右下角）
     */
    private void updateSignupCount() {
        if (currentRace != null) {
            RaceSignupController signupController = new RaceSignupController();
            int signupCount = signupController.getSignedUpCount(currentRace.getRaceId());
            // 使用SpannableString设置数字为橙色、加粗、更大
            String signupText = "已有 " + signupCount + " 人报名";
            signupController.close();
            
            // 更新白色背景部分的报名人数（右上角）
            if (binding.tvSignupCount != null) {
                android.text.SpannableString spannableString = new android.text.SpannableString(signupText);
                int startIndex = signupText.indexOf(String.valueOf(signupCount));
                int endIndex = startIndex + String.valueOf(signupCount).length();
                
                // 设置数字为橙色
                spannableString.setSpan(
                    new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.trail_orange)),
                    startIndex, endIndex, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                
                // 设置数字为加粗
                spannableString.setSpan(
                    new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    startIndex, endIndex, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                
                // 设置数字字体更大（相对大小）
                spannableString.setSpan(
                    new android.text.style.RelativeSizeSpan(1.3f),
                    startIndex, endIndex, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                
                binding.tvSignupCount.setText(spannableString);
            }
        }
    }
    
    /**
     * 获取路线文本（起点 - 检查点1 - 检查点2 - ... - 终点）
     */
    private String getRouteText() {
        if (checkPoints == null || checkPoints.isEmpty()) {
            return "暂无打卡点";
        }
        
        // 按顺序排序
        List<CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
        sortedPoints.sort(Comparator.comparingInt(CheckPoint::getOrderIndex));
        
        // 构建完整路线文本（使用箭头连接）
        StringBuilder routeBuilder = new StringBuilder();
        for (int i = 0; i < sortedPoints.size(); i++) {
            CheckPoint point = sortedPoints.get(i);
            if (i > 0) {
                routeBuilder.append(" → ");
            }
            routeBuilder.append(point.getName());
        }
        
        return routeBuilder.toString();
    }
    
    /**
     * 更新比赛路线显示
     */
    private void updateRoute() {
        if (binding.tvRoute != null) {
            String routeText = getRouteText();
            binding.tvRoute.setText(routeText);
        }
    }


    /**
     * 加载路线图（轨迹缩略图）
     */
    private void loadRouteThumbnail(Race race) {
        if (binding.ivRouteThumbnail == null) {
            android.util.Log.w("RaceDetailActivity", "ivRouteThumbnail 为 null");
            return;
        }

        // 确保卡片可见
        if (binding.cardRouteThumbnail != null) {
            binding.cardRouteThumbnail.setVisibility(View.VISIBLE);
        }

        // 先显示占位符
        binding.ivRouteThumbnail.setImageResource(android.R.drawable.ic_menu_mapmode);
        binding.ivRouteThumbnail.setContentDescription("路线图");
        binding.ivRouteThumbnail.setVisibility(View.VISIBLE);

        String thumbnailPath = race.getThumbnailPath();
        android.util.Log.d("RaceDetailActivity", "========== 开始加载路线图 ==========");
        android.util.Log.d("RaceDetailActivity", "raceId: " + race.getRaceId());
        android.util.Log.d("RaceDetailActivity", "thumbnailPath: " + thumbnailPath);
        
        if (thumbnailPath != null && !thumbnailPath.isEmpty()) {
            // 检查文件是否存在、可读、大小
            java.io.File file = new java.io.File(thumbnailPath);
            boolean fileExists = file.exists();
            boolean fileReadable = file.canRead();
            long fileSize = fileExists ? file.length() : 0;
            
            android.util.Log.d("RaceDetailActivity", "文件检查 - 存在: " + fileExists + ", 可读: " + fileReadable + ", 大小: " + fileSize + " 字节");
            
            if (fileExists && fileReadable && fileSize > 0) {
                // 使用 Glide 异步加载本地缩略图，内部已使用线程池与缓存
                android.util.Log.d("RaceDetailActivity", "使用 Glide 加载缩略图: " + thumbnailPath);
                Glide.with(this)
                        .load(thumbnailPath)
                        .placeholder(android.R.drawable.ic_menu_mapmode)
                        .error(android.R.drawable.ic_menu_mapmode)
                        .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                android.util.Log.e("RaceDetailActivity", "Glide 加载失败: " + (e != null ? e.getMessage() : "未知错误"), e);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                android.util.Log.d("RaceDetailActivity", "Glide 加载成功");
                                return false;
                            }
                        })
                        .into(binding.ivRouteThumbnail);
            } else {
                android.util.Log.w("RaceDetailActivity", "文件不存在、不可读或大小为0，尝试动态生成路线预览图");
                generateRoutePreview(race);
            }
        } else {
            // 如果没有缩略图路径，根据打卡点动态生成路线预览图
            android.util.Log.d("RaceDetailActivity", "没有缩略图路径，尝试动态生成路线预览图");
            generateRoutePreview(race);
        }
        android.util.Log.d("RaceDetailActivity", "========== 路线图加载流程结束 ==========");
    }

    /**
     * 根据打卡点动态生成路线预览图（使用真实地图容器）
     */
    private void generateRoutePreview(Race race) {
        List<CheckPoint> checkPoints = race.getCheckPoints();
        if (checkPoints == null || checkPoints.isEmpty()) {
            android.util.Log.d("RaceDetailActivity", "没有打卡点，显示占位符");
            binding.ivRouteThumbnail.setImageResource(android.R.drawable.ic_menu_mapmode);
            binding.ivRouteThumbnail.setContentDescription("路线图（暂无打卡点）");
            return;
        }

        // 先显示占位符
        binding.ivRouteThumbnail.setImageResource(android.R.drawable.ic_menu_mapmode);
        binding.ivRouteThumbnail.setContentDescription("正在生成路线图...");

        // 在主线程创建隐藏的地图容器
        runOnUiThread(() -> {
            try {
                // 创建隐藏的 MapView（动态创建，不添加到布局中）
                MapView hiddenMapView = new MapView(this);
                hiddenMapView.onCreate(null);
                hiddenMapView.onResume();
                
                AMap hiddenAMap = hiddenMapView.getMap();
                
                // 配置地图
                hiddenAMap.getUiSettings().setZoomControlsEnabled(false);
                hiddenAMap.getUiSettings().setZoomGesturesEnabled(false);
                hiddenAMap.getUiSettings().setScrollGesturesEnabled(false);
                hiddenAMap.getUiSettings().setRotateGesturesEnabled(false);
                
                // 按顺序排序打卡点
                List<CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
                sortedPoints.sort(Comparator.comparingInt(CheckPoint::getOrderIndex));
                
                // 计算边界
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                List<LatLng> routePoints = new ArrayList<>();
                
                for (CheckPoint cp : sortedPoints) {
                    LatLng latLng = new LatLng(cp.getLatitude(), cp.getLongitude());
                    boundsBuilder.include(latLng);
                    routePoints.add(latLng);
                    
                    // 添加标记
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(latLng)
                            .title(cp.getName());
                    hiddenAMap.addMarker(markerOptions);
                }
                
                // 绘制路线
                if (routePoints.size() > 1) {
                    PolylineOptions polylineOptions = new PolylineOptions()
                            .addAll(routePoints)
                            .color(0xFF4CAF50) // 绿色
                            .width(8);
                    hiddenAMap.addPolyline(polylineOptions);
                }
                
                // 调整地图视野以包含所有打卡点
                LatLngBounds bounds = boundsBuilder.build();
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100); // 100dp 边距
                hiddenAMap.moveCamera(cameraUpdate);
                
                // 等待地图加载完成后截图
                hiddenAMap.setOnMapLoadedListener(() -> {
                    android.util.Log.d("RaceDetailActivity", "地图加载完成，开始截图");
                    hiddenAMap.getMapScreenShot(new AMap.OnMapScreenShotListener() {
                        @Override
                        public void onMapScreenShot(Bitmap bitmap) {
                            if (bitmap != null && !bitmap.isRecycled()) {
                                final Bitmap finalBitmap = bitmap;
                                runOnUiThread(() -> {
                                    binding.ivRouteThumbnail.setImageBitmap(finalBitmap);
                                    binding.ivRouteThumbnail.setContentDescription("路线图");
                                    android.util.Log.d("RaceDetailActivity", "路线预览图生成成功，尺寸: " + finalBitmap.getWidth() + "x" + finalBitmap.getHeight());
                                });
                            } else {
                                android.util.Log.w("RaceDetailActivity", "地图截图失败，显示占位符");
                                runOnUiThread(() -> {
                                    binding.ivRouteThumbnail.setImageResource(android.R.drawable.ic_menu_mapmode);
                                    binding.ivRouteThumbnail.setContentDescription("路线图（生成失败）");
                                });
                            }
                            
                            // 清理地图资源
                            hiddenMapView.onPause();
                            hiddenMapView.onDestroy();
                        }
                        
                        @Override
                        public void onMapScreenShot(Bitmap bitmap, int i) {
                            // 这里留空！不要调用上面的方法
                        }
                    });
                });
                
            } catch (Exception e) {
                android.util.Log.e("RaceDetailActivity", "生成路线预览图时出错", e);
                binding.ivRouteThumbnail.setImageResource(android.R.drawable.ic_menu_mapmode);
                binding.ivRouteThumbnail.setContentDescription("路线图（生成失败）");
            }
        });
    }

    /**
     * 更新比赛时间显示（开始时间→结束时间，完整年月日时分）
     */
    private void updateTimeRange() {
        if (binding.tvTimeRange == null || currentRace == null) {
            return;
        }

        // 使用中文格式：yyyy年MM月dd日 HH:mm
        SimpleDateFormat chineseFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);

        if (currentRace.getStartTime() != null && currentRace.getEndTime() != null) {
            String startTime = chineseFormat.format(currentRace.getStartTime());
            String endTime = chineseFormat.format(currentRace.getEndTime());
            binding.tvTimeRange.setText(startTime + " - " + endTime);
        } else if (currentRace.getStartTime() != null) {
            String startTime = chineseFormat.format(currentRace.getStartTime());
            binding.tvTimeRange.setText(startTime + " - --");
        } else if (currentRace.getEndTime() != null) {
            String endTime = chineseFormat.format(currentRace.getEndTime());
            binding.tvTimeRange.setText("-- - " + endTime);
        } else {
            binding.tvTimeRange.setText("-- - --");
        }
    }

    /**
     * 在地图中打开位置（跳转到高德地图）
     */
    private void openLocationInMap() {
        // 查找起点或第一个打卡点
        CheckPoint targetPoint = null;
        for (CheckPoint cp : checkPoints) {
            if (CheckPoint.TYPE_START.equals(cp.getType())) {
                targetPoint = cp;
                break;
            }
        }
        if (targetPoint == null && !checkPoints.isEmpty()) {
            targetPoint = checkPoints.get(0);
        }

        if (targetPoint == null) {
            UIUtil.showToast(this, "位置信息不存在");
            return;
        }

        // 使用高德地图打开位置
        try {
            String uri = String.format(Locale.CHINA, "androidamap://route?sourceApplication=CrossIntelligence&dlat=%.6f&dlon=%.6f&dname=%s&dev=0&t=0",
                    targetPoint.getLatitude(), targetPoint.getLongitude(), targetPoint.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.autonavi.minimap");
            startActivity(intent);
        } catch (Exception e) {
            // 如果高德地图未安装，使用通用地图
            String uri = String.format(Locale.CHINA, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                    targetPoint.getLatitude(), targetPoint.getLongitude(),
                    targetPoint.getLatitude(), targetPoint.getLongitude(), targetPoint.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                UIUtil.showToast(this, "未找到地图应用");
            }
        }
    }


    /**
     * 显示二维码对话框
     */
    private void showQrCodeDialog(CheckPoint checkPoint) {
        // 生成二维码
        Bitmap qrBitmap = QrCodeGenerator.generateCheckPointQrCode(
                checkPoint.getRaceId(),
                checkPoint.getCheckPointId()
        );

        if (qrBitmap == null) {
            UIUtil.showToast(this, "生成二维码失败");
            return;
        }

        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.view.View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null);
        builder.setView(dialogView);

        // 设置打卡点名称
        TextView tvCheckPointName = dialogView.findViewById(R.id.tvCheckPointName);
        tvCheckPointName.setText(checkPoint.getName());

        // 显示二维码
        ImageView ivQrCode = dialogView.findViewById(R.id.ivQrCode);
        ivQrCode.setImageBitmap(qrBitmap);

        AlertDialog dialog = builder.create();

        // 保存按钮
        MaterialButton btnSaveQr = dialogView.findViewById(R.id.btnSaveQr);
        btnSaveQr.setOnClickListener(v -> {
            saveQrCode(qrBitmap, checkPoint.getName());
        });

        // 分享按钮
        MaterialButton btnShareQr = dialogView.findViewById(R.id.btnShareQr);
        btnShareQr.setOnClickListener(v -> {
            String fileName = QrCodeUtil.generateQrCodeFileName(checkPoint.getName());
            QrCodeUtil.shareQrCode(this, qrBitmap, fileName);
        });

        dialog.show();
    }

    /**
     * 保存二维码到相册
     */
    private void saveQrCode(Bitmap bitmap, String checkPointName) {
        // Android 10 及以上不需要存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performSaveQrCode(bitmap, checkPointName);
        } else {
            // Android 9 及以下需要检查存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // 保存待处理的数据
                pendingSaveBitmap = bitmap;
                pendingSaveFileName = checkPointName;
                // 请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_STORAGE);
            } else {
                performSaveQrCode(bitmap, checkPointName);
            }
        }
    }

    /**
     * 执行保存二维码操作
     */
    private void performSaveQrCode(Bitmap bitmap, String checkPointName) {
        String fileName = QrCodeUtil.generateQrCodeFileName(checkPointName);
        boolean success = QrCodeUtil.saveQrCodeToGallery(this, bitmap, fileName);
        if (success) {
            UIUtil.showToast(this, "二维码已保存到相册");
        } else {
            UIUtil.showToast(this, "保存失败");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，执行保存
                if (pendingSaveBitmap != null && pendingSaveFileName != null) {
                    performSaveQrCode(pendingSaveBitmap, pendingSaveFileName);
                    pendingSaveBitmap = null;
                    pendingSaveFileName = null;
                }
            } else {
                UIUtil.showToast(this, "需要存储权限才能保存二维码");
            }
        }
    }

    /**
     * 加载头部背景图片
     * 使用固定的 race_hero.jpg 作为背景图
     */
    private void loadHeroImage(Race race) {
        if (binding.ivHeroImage == null) {
            return;
        }
        
        // 直接使用 race_hero.jpg 作为背景图
        binding.ivHeroImage.setImageResource(R.drawable.race_hero);
        
        // 启动背景图呼吸动画
        startHeroImageAnimation();
    }
    
    /**
     * 背景图呼吸动画：缓慢缩放，模拟呼吸感
     */
    private void startHeroImageAnimation() {
        if (binding.ivHeroImage == null) {
            return;
        }
        
        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(1.0f, 1.05f, 1.0f);
        scaleAnimator.setDuration(8000); // 8秒一个周期
        scaleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scaleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleAnimator.addUpdateListener(animation -> {
            float scale = (Float) animation.getAnimatedValue();
            binding.ivHeroImage.setScaleX(scale);
            binding.ivHeroImage.setScaleY(scale);
        });
        scaleAnimator.start();
    }

    /**
     * 显示路线图对话框（显示创建赛事时保存的轨迹缩略图）
     */
    private void showRaceRouteMapDialog() {
        android.util.Log.d("RaceDetailActivity", "========== 显示路线图对话框 ==========");
        
        if (currentRace == null) {
            android.util.Log.e("RaceDetailActivity", "currentRace 为 null");
            UIUtil.showToast(this, "赛事信息不存在");
            return;
        }

        String thumbnailPath = currentRace.getThumbnailPath();
        android.util.Log.d("RaceDetailActivity", "对话框 - thumbnailPath: " + thumbnailPath);
        
        if (thumbnailPath == null || thumbnailPath.isEmpty()) {
            android.util.Log.w("RaceDetailActivity", "对话框 - thumbnailPath 为空");
            UIUtil.showToast(this, "暂无路线图");
            return;
        }
        
        // 检查文件是否存在、可读、大小
        java.io.File file = new java.io.File(thumbnailPath);
        boolean fileExists = file.exists();
        boolean fileReadable = file.canRead();
        long fileSize = fileExists ? file.length() : 0;
        android.util.Log.d("RaceDetailActivity", "对话框 - 文件检查 - 存在: " + fileExists + ", 可读: " + fileReadable + ", 大小: " + fileSize + " 字节");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_route_thumbnail, null);
        builder.setView(dialogView);
        
        // 设置点击外部关闭
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        
        // 加载并显示轨迹缩略图（使用 Glide 替代手动线程与 Bitmap 管理）
        ImageView ivThumbnail = dialogView.findViewById(R.id.ivRouteThumbnail);
        
        android.util.Log.d("RaceDetailActivity", "对话框 - 使用 Glide 加载: " + thumbnailPath);
        Glide.with(this)
                .load(thumbnailPath)
                .placeholder(android.R.drawable.ic_menu_mapmode)
                .error(android.R.drawable.ic_menu_mapmode)
                .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        android.util.Log.e("RaceDetailActivity", "对话框 - Glide 加载失败: " + (e != null ? e.getMessage() : "未知错误"), e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        android.util.Log.d("RaceDetailActivity", "对话框 - Glide 加载成功");
                        return false;
                    }
                })
                .into(ivThumbnail);
        
        dialog.show();
        android.util.Log.d("RaceDetailActivity", "========== 路线图对话框已显示 ==========");
    }

    /**
     * 显示打卡点地图对话框（显示所有打卡点）
     */
    private void showCheckpointMapDialog(CheckPoint clickedCheckPoint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_checkpoint_map, null);
        builder.setView(dialogView);
        
        // 设置点击外部关闭
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        
        // 初始化地图
        MapView mapView = dialogView.findViewById(R.id.mapView);
        mapView.onCreate(null);
        AMap aMap = mapView.getMap();
        
        // 配置地图
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setZoomGesturesEnabled(true);
        aMap.getUiSettings().setScrollGesturesEnabled(true);
        aMap.getUiSettings().setRotateGesturesEnabled(true);
        
        // 只添加点击的打卡点标记（蓝色，突出显示）
        LatLng clickedLatLng = new LatLng(clickedCheckPoint.getLatitude(), clickedCheckPoint.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions()
                .position(clickedLatLng)
                .title(clickedCheckPoint.getName())
                .snippet("序号：" + clickedCheckPoint.getOrderIndex());
        aMap.addMarker(markerOptions);
        
        // 地图居中显示点击的打卡点位置，使用更大的缩放级别以突出显示该打卡点
        // 使用平滑动画移动到该位置
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(clickedLatLng, 18f);
        aMap.animateCamera(cameraUpdate, 300, null);
        
        // 保存引用以便在dialog关闭时销毁
        dialogMapView = mapView;
        dialogAMap = aMap;
        mapDialog = dialog;
        
        // 监听dialog关闭，销毁地图
        dialog.setOnDismissListener(dialog1 -> {
            if (dialogMapView != null) {
                dialogMapView.onPause();
                dialogMapView.onDestroy();
                dialogMapView = null;
                dialogAMap = null;
            }
        });
        
        dialog.show();
        
        // 地图生命周期管理
        mapView.onResume();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (dialogMapView != null) {
            dialogMapView.onPause();
        }
        if (routeMapView != null) {
            routeMapView.onPause();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (dialogMapView != null) {
            dialogMapView.onResume();
        }
        if (routeMapView != null) {
            routeMapView.onResume();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialogMapView != null) {
            dialogMapView.onDestroy();
            dialogMapView = null;
        }
        if (mapDialog != null && mapDialog.isShowing()) {
            mapDialog.dismiss();
            mapDialog = null;
        }
        if (routeMapView != null) {
            routeMapView.onDestroy();
            routeMapView = null;
        }
        if (routeMapDialog != null && routeMapDialog.isShowing()) {
            routeMapDialog.dismiss();
            routeMapDialog = null;
        }
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
    }
}

