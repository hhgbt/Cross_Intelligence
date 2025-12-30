package com.example.cross_intelligence.mvc.view.race;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.transition.TransitionManager;

import com.google.android.material.appbar.AppBarLayout;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import android.graphics.Point;

import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.amap.api.services.core.AMapException;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityCreateRaceBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.location.MapLocationManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.MapThumbnailUtil;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.QrCodeGenerator;
import com.example.cross_intelligence.mvc.util.QrCodeUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.admin.AdminMainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import android.graphics.Bitmap;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 赛事创建页面：集成高德地图选择打卡点，并完成数据校验与保存。
 */
public class CreateRaceActivity extends BaseActivity implements 
        AMap.OnMapClickListener, 
        MapLocationManager.LocationCallback {

    private static final int MAX_CHECKPOINTS = 40;
    private static final double DUPLICATE_THRESHOLD = 0.00005;
    // 使用 CheckPoint 模型中定义的常量
    private static final String TYPE_START = CheckPoint.TYPE_START;
    private static final String TYPE_CHECKPOINT = CheckPoint.TYPE_CHECKPOINT;
    private static final String TYPE_END = CheckPoint.TYPE_FINISH;
    private static final double DEFAULT_CHECK_RADIUS = 50.0; // 默认打卡半径50米

    private ActivityCreateRaceBinding binding;
    private MapView mapView;
    private AMap aMap;
    private final RaceManager raceManager = new RaceManager();
    private final List<CheckPoint> checkPoints = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();
    private CheckpointAdapter adapter;
    private SearchResultAdapter searchResultAdapter; // 搜索结果适配器
    private Polyline routePolyline; // 路线预览折线
    private final SimpleDateFormat dateTimeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private String editingRaceId; // 编辑模式下的赛事ID，为 null 表示创建模式
    private MapLocationManager locationManager; // 定位管理器
    private boolean isFirstLocation = true; // 标记是否首次定位
    private AppBarLayout appBarLayout; // AppBarLayout 控制器
    private boolean isAppBarExpanded = true; // AppBarLayout 是否展开
    private boolean isBasicInfoCollapsed = false; // 基础信息是否已折叠
    private boolean isFirstMapClickAfterExpand = false; // 地图全屏后是否是第一次点击地图

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置状态栏为白色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white));
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
        
        binding = ActivityCreateRaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        
        // 检查是否为编辑模式
        editingRaceId = getIntent().getStringExtra("raceId");
        
        // 设置 Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(editingRaceId != null ? 
                getString(R.string.edit_race_title) : 
                getString(R.string.create_race_title));
        }
        // 初始化 AppBarLayout
        appBarLayout = binding.appBarLayout;
        // 监听 AppBarLayout 的展开/折叠状态（用于返回键逻辑）
        appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            isAppBarExpanded = verticalOffset == 0;
        });
        
        
        // 注册 OnBackPressedCallback（替代已废弃的 onBackPressed）
        OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 如果基础信息已折叠，先展开
                if (isBasicInfoCollapsed) {
                    toggleBasicInfo();
                } else {
                    // 否则正常返回
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        
        // Toolbar 返回按钮也使用相同的逻辑
        binding.toolbar.setNavigationOnClickListener(v -> backPressedCallback.handleOnBackPressed());
        
        initView();
        initData();
        
        // 初始化时显示打卡点计数（即使为0也要显示）
        updateCheckpointCount();
        binding.cardCheckpoints.setVisibility(android.view.View.VISIBLE);
        
        // 初始化定位管理器
        locationManager = new MapLocationManager(this, this);
        
        // 初始化搜索功能
        initSearch();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initView() {
        // 打卡点列表直接显示（无需折叠功能）

        adapter = new CheckpointAdapter(checkPoints, position -> {
            removeMarker(position);
            checkPoints.remove(position);
            reindexCheckpoints();
            adapter.notifyItemRemoved(position);
            updateRoutePreview(); // 更新路线预览
            updateCheckpointCount(); // 更新打卡点数量显示
            autoSaveDraft(); // 自动保存草稿
        });
        // 设置二维码点击监听器
        adapter.setOnItemQrClickListener(this::showQrCodeDialog);
        binding.rvCheckpoints.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpoints.setAdapter(adapter);

        // 手动添加打卡点按钮（在打卡点卡片右上角）
        // 初始状态显示"地图"，点击后切换为"手动添加打卡点"
        binding.btnManualAddCheckpoint.setOnClickListener(v -> {
            if (isBasicInfoCollapsed) {
                // 地图已全屏，显示手动添加打卡点对话框
                showCheckpointDialog(null);
            } else {
                // 地图未全屏，点击后展开地图
                toggleBasicInfo();
            }
        });
        
        // BottomSheet 中的手动添加打卡点按钮
        binding.btnManualAddCheckpointBottomSheet.setOnClickListener(v -> {
            showCheckpointDialog(null);
        });
        
        // 顶部返回按钮（地图全屏时显示）
        binding.btnBackFromMap.setOnClickListener(v -> {
            toggleBasicInfo();
        });
        
        // 不再使用 BottomSheet Behavior，改为固定悬浮卡片（参考选手端成绩详情页）

        // 保存按钮（Extended FAB）
        binding.btnSaveRace.setOnClickListener(v -> {
            android.util.Log.d("CreateRaceActivity", "保存按钮被点击");
            saveRace();
        });
        
        // 根据模式设置按钮文本
        if (editingRaceId != null) {
            binding.btnSaveRace.setText("保存修改");
        }


        // 时间选择器：通过图标点击
        binding.tilStartTime.setStartIconOnClickListener(v -> pickDateTime(binding.etStartTime));
        binding.tilEndTime.setStartIconOnClickListener(v -> pickDateTime(binding.etEndTime));
        // 保留EditText点击也支持
        binding.etStartTime.setOnClickListener(v -> pickDateTime(binding.etStartTime));
        binding.etEndTime.setOnClickListener(v -> pickDateTime(binding.etEndTime));

        // 设置描述字数限制（2000字符）
        binding.etDescription.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter.LengthFilter(2000)
        });
        // 开启计数器显示
        binding.tilDescription.setCounterEnabled(true);
        binding.tilDescription.setCounterMaxLength(2000);

        // 允许输入框内部滚动（解决与外部 ScrollView 冲突）
        binding.etDescription.setOnTouchListener((v, event) -> {
            if (binding.etDescription.hasFocus()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if ((event.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        // 初始化 BottomSheet 中的 RecyclerView（使用同一个 adapter）
        binding.rvCheckpointsBottomSheet.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpointsBottomSheet.setAdapter(adapter);
        
        // 初始按钮文本为"地图"
        binding.btnManualAddCheckpoint.setText("地图");
        binding.btnManualAddCheckpoint.setIcon(null);

        // 添加文本变化监听，实现自动保存
        binding.etRaceName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                autoSaveDraft();
            }
        });
        addTextWatcher(binding.etDescription);
        binding.etStartTime.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                autoSaveDraft();
            }
        });
        binding.etEndTime.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                autoSaveDraft();
            }
        });

        initMap();
        binding.mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                // 点击地图时自动折叠 AppBarLayout，为地图腾出空间
                if (appBarLayout != null) {
                    appBarLayout.setExpanded(false, true);
                }
                hideKeyboard();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        });
    }
    


    /**
     * 隐藏软键盘
     */
    private void hideKeyboard() {
        android.view.View view = getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    /**
     * 初始化搜索功能
     */
    private void initSearch() {
        // 初始化搜索结果适配器
        searchResultAdapter = new SearchResultAdapter();
        searchResultAdapter.setOnItemClickListener(tip -> {
            // 点击搜索结果后跳转地图
            onSearchResultClick(tip);
        });
        
        // 设置搜索结果列表
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchResults.setAdapter(searchResultAdapter);
        
        // 监听搜索输入框文字变化
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String content = s.toString().trim();
                if (content.length() > 0) {
                    // 执行搜索
                    performSearch(content);
                } else {
                    // 清空搜索结果
                    hideSearchResults();
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // 搜索框的搜索按钮点击事件
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        
        // 遮罩层不设置点击监听，避免拦截搜索结果卡片的点击事件
        // 用户可以通过点击地图或其他方式关闭搜索结果
        // binding.viewSearchOverlay.setOnClickListener(null);
    }

    /**
     * 执行搜索：使用高德地图 Inputtips
     */
    private void performSearch(String keyword) {
        try {
            // 构造查询对象，搜索范围：全中国
            InputtipsQuery inputtipsQuery = new InputtipsQuery(keyword, "全国");
            // 设置城市限制（可选，这里不限制，搜索全中国）
            inputtipsQuery.setCityLimit(false);
            
            Inputtips inputtips = new Inputtips(this, inputtipsQuery);
            
            // 设置回调监听
            inputtips.setInputtipsListener(new Inputtips.InputtipsListener() {
                @Override
                public void onGetInputtips(List<Tip> tipList, int rCode) {
                    if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                        // 搜索成功，更新结果列表
                        if (tipList != null && !tipList.isEmpty()) {
                            searchResultAdapter.updateData(tipList);
                            showSearchResults();
                        } else {
                            // 没有搜索结果
                            searchResultAdapter.clearData();
                            hideSearchResults();
                        }
                    } else {
                        // 搜索失败
                        android.util.Log.e("CreateRaceActivity", "搜索失败，错误码: " + rCode);
                        hideSearchResults();
                    }
                }
            });
            
            // 异步请求搜索
            inputtips.requestInputtipsAsyn();
        } catch (Exception e) {
            android.util.Log.e("CreateRaceActivity", "搜索异常", e);
            hideSearchResults();
        }
    }

    /**
     * 点击搜索结果后的处理：跳转地图（不添加标记）
     */
    private void onSearchResultClick(Tip tip) {
        android.util.Log.d("CreateRaceActivity", "=== onSearchResultClick 被调用 ===");
        android.util.Log.d("CreateRaceActivity", "点击搜索结果: " + (tip != null ? tip.getName() : "null"));
        
        if (tip == null) {
            android.util.Log.e("CreateRaceActivity", "Tip 为 null");
            UIUtil.showToast(this, "搜索结果无效");
            return;
        }
        
        // 立即隐藏搜索结果，避免与地图点击事件冲突
        hideSearchResults();
        
        // 检查是否有经纬度
        com.amap.api.services.core.LatLonPoint point = tip.getPoint();
        android.util.Log.d("CreateRaceActivity", "point 是否为 null: " + (point == null));
        
        if (point != null) {
            double lat = point.getLatitude();
            double lng = point.getLongitude();
            android.util.Log.d("CreateRaceActivity", "目标坐标: " + lat + ", " + lng);
            
            LatLng targetPos = new LatLng(lat, lng);
            
            // 【关键点】只移动镜头，不添加 Marker
            if (aMap != null) {
                android.util.Log.d("CreateRaceActivity", "开始跳转地图，aMap 不为 null");
                // 立即跳转地图，使用 animateCamera 平滑移动
                // 使用 18f 缩放级别，让地图更精准地显示搜索位置（适合查看具体山头、村落等）
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetPos, 18f));
                
                // 清空搜索框并隐藏输入法
                binding.etSearch.setText("");
                hideKeyboard();
                
                // 提示用户：请点击地图设置打卡点（延迟显示，避免与跳转动画冲突）
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    UIUtil.showToast(CreateRaceActivity.this, "已跳转至：" + tip.getName() + "，请点击地图位置创建打卡点");
                }, 300);
            } else {
                android.util.Log.e("CreateRaceActivity", "aMap 为 null，地图未初始化");
                UIUtil.showToast(this, "地图未初始化，请稍后再试");
            }
        } else {
            // 没有坐标信息
            android.util.Log.w("CreateRaceActivity", "Tip 没有坐标信息: " + tip.getName());
            UIUtil.showToast(this, "该地点没有坐标信息，请尝试搜索其他地点");
        }
    }

    /**
     * 显示搜索结果
     */
    private void showSearchResults() {
        binding.cardSearchResults.setVisibility(View.VISIBLE);
        binding.viewSearchOverlay.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏搜索结果
     */
    private void hideSearchResults() {
        binding.cardSearchResults.setVisibility(View.GONE);
        binding.viewSearchOverlay.setVisibility(View.GONE);
    }


    @Override
    protected void initData() {
        // 如果是编辑模式，加载现有赛事数据
        if (editingRaceId != null) {
            loadRaceData(editingRaceId);
        } else {
            // 创建模式：默认显示当前日期和时间（开始时间为当前时间，结束时间为当前时间+2小时）
            Calendar calendar = Calendar.getInstance();
            // 开始时间设为当前时间
            binding.etStartTime.setText(dateTimeFormat.format(calendar.getTime()));
            // 结束时间设为当前时间+2小时
            calendar.add(Calendar.HOUR_OF_DAY, 2);
            binding.etEndTime.setText(dateTimeFormat.format(calendar.getTime()));
        }
    }

    /**
     * 加载赛事数据用于编辑
     */
    private void loadRaceData(String raceId) {
        Race race = raceManager.getRaceById(raceId);
        if (race == null) {
            UIUtil.showToast(this, "赛事不存在");
            finish();
            return;
        }

        // 填充基本信息
        binding.etRaceName.setText(race.getName());
        if (race.getDescription() != null) {
            binding.etDescription.setText(race.getDescription());
        }
        if (race.getStartTime() != null) {
            binding.etStartTime.setText(dateTimeFormat.format(race.getStartTime()));
        }
        if (race.getEndTime() != null) {
            binding.etEndTime.setText(dateTimeFormat.format(race.getEndTime()));
        }

        // 加载打卡点
        if (race.getCheckPoints() != null && !race.getCheckPoints().isEmpty()) {
            checkPoints.clear();
            if (markers != null) {
                markers.clear();
            }
            
            // 复制打卡点到列表中
            for (CheckPoint point : race.getCheckPoints()) {
                CheckPoint copy = new CheckPoint();
                copy.setCheckPointId(point.getCheckPointId());
                copy.setName(point.getName());
                copy.setLatitude(point.getLatitude());
                copy.setLongitude(point.getLongitude());
                copy.setType(point.getType());
                copy.setCheckRadius(point.getCheckRadius());
                copy.setOrderIndex(point.getOrderIndex());
                checkPoints.add(copy);
            }
            
            // 按顺序排序
            checkPoints.sort((p1, p2) -> Integer.compare(p1.getOrderIndex(), p2.getOrderIndex()));
            adapter.notifyDataSetChanged();
            updateCheckpointCount(); // 更新打卡点数量显示
            
            // 编辑模式下，如果有打卡点，列表会自动显示
        }
    }

    private void pickDateTime(TextInputEditText target) {
        // 先尝试解析当前已有时间，作为初始值
        Calendar calendar = Calendar.getInstance();
        CharSequence current = target.getText();
        if (current != null && current.length() > 0) {
            try {
                Date parsed = dateTimeFormat.parse(current.toString());
                if (parsed != null) {
                    calendar.setTime(parsed);
                }
            } catch (Exception ignored) {
            }
        }

        // 如果是创建模式且选择的是开始时间，设置最小日期为今天
        boolean isStartTime = target == binding.etStartTime;
        boolean isCreateMode = editingRaceId == null;
        long minDate = 0;
        if (isCreateMode && isStartTime) {
            // 创建模式下，开始时间不能早于当前时间
            Calendar minCalendar = Calendar.getInstance();
            minCalendar.set(Calendar.HOUR_OF_DAY, 0);
            minCalendar.set(Calendar.MINUTE, 0);
            minCalendar.set(Calendar.SECOND, 0);
            minCalendar.set(Calendar.MILLISECOND, 0);
            minDate = minCalendar.getTimeInMillis();
        }

        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                
                                // 创建模式下，开始时间不能早于当前时间
                                if (isCreateMode && isStartTime) {
                                    Calendar now = Calendar.getInstance();
                                    if (calendar.getTimeInMillis() < now.getTimeInMillis()) {
                                        UIUtil.showToast(CreateRaceActivity.this, "开始时间不能早于当前时间");
                                        return;
                                    }
                                }
                                
                                // 如果是结束时间，需要验证至少比开始时间晚5分钟
                                if (!isStartTime) {
                                    String startTimeText = textOf(binding.etStartTime);
                                    if (!TextUtils.isEmpty(startTimeText)) {
                                        try {
                                            Calendar startCalendar = Calendar.getInstance();
                                            startCalendar.setTime(dateTimeFormat.parse(startTimeText));
                                            long startMillis = startCalendar.getTimeInMillis();
                                            long endMillis = calendar.getTimeInMillis();
                                            long diffMinutes = (endMillis - startMillis) / (1000 * 60);
                                            
                                            if (diffMinutes < 5) {
                                                UIUtil.showToast(CreateRaceActivity.this, "结束时间必须比开始时间晚至少5分钟");
                                                return;
                                            }
                                        } catch (Exception e) {
                                            // 解析失败，忽略验证
                                        }
                                    }
                                }
                                
                                target.setText(dateTimeFormat.format(calendar.getTime()));
                                
                                // 如果设置了开始时间，自动调整结束时间（如果结束时间早于开始时间+5分钟）
                                if (isStartTime) {
                                    String endTimeText = textOf(binding.etEndTime);
                                    if (!TextUtils.isEmpty(endTimeText)) {
                                        try {
                                            Calendar endCalendar = Calendar.getInstance();
                                            endCalendar.setTime(dateTimeFormat.parse(endTimeText));
                                            long startMillis = calendar.getTimeInMillis();
                                            long endMillis = endCalendar.getTimeInMillis();
                                            long diffMinutes = (endMillis - startMillis) / (1000 * 60);
                                            
                                            if (diffMinutes < 5) {
                                                // 自动设置为开始时间+5分钟
                                                Calendar newEndCalendar = (Calendar) calendar.clone();
                                                newEndCalendar.add(Calendar.MINUTE, 5);
                                                binding.etEndTime.setText(dateTimeFormat.format(newEndCalendar.getTime()));
                                            }
                                        } catch (Exception e) {
                                            // 解析失败，自动设置为开始时间+5分钟
                                            Calendar newEndCalendar = (Calendar) calendar.clone();
                                            newEndCalendar.add(Calendar.MINUTE, 5);
                                            binding.etEndTime.setText(dateTimeFormat.format(newEndCalendar.getTime()));
                                        }
                                    }
                                }
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true);
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        
        // 设置最小日期
        if (minDate > 0) {
            datePickerDialog.getDatePicker().setMinDate(minDate);
        }
        
        datePickerDialog.show();
    }

    private void initMap() {
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setZoomControlsEnabled(false);
            aMap.moveCamera(CameraUpdateFactory.zoomTo(16f));
            aMap.setOnMapClickListener(this);
            
            // 初始状态不显示地图提示标签（只有地图放大后才显示）
            
            // 如果是编辑模式，地图初始化后需要重新加载打卡点（因为地图可能还未准备好）
            if (editingRaceId != null && !checkPoints.isEmpty()) {
                // 地图已初始化，重新绘制标记和路线
                for (CheckPoint point : checkPoints) {
                    Marker marker = aMap.addMarker(new MarkerOptions()
                            .position(new LatLng(point.getLatitude(), point.getLongitude()))
                            .title(point.getOrderIndex() + ". " + point.getName() + 
                                   (point.getType() != null ? " (" + point.getType() + ")" : "")));
                    markers.add(marker);
                }
                updateRoutePreview();
            } else {
                // 初始化路线预览
                updateRoutePreview();
            }
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // 点击地图时，如果搜索结果显示，先隐藏搜索结果
        // 注意：搜索结果卡片的点击不应该触发地图点击，因为卡片有更高的 elevation
        // 这里只处理真正点击地图空白区域的情况
        if (binding.cardSearchResults.getVisibility() == View.VISIBLE) {
            hideSearchResults();
            return; // 隐藏搜索结果后，不继续处理地图点击
        }
        
        if (!isBasicInfoCollapsed) {
            // 如果基础信息未折叠，先折叠（第一次点击）
            toggleBasicInfo();
            // toggleBasicInfo() 内部已经设置了 isFirstMapClickAfterExpand = true，并显示提示标签
            return; // 第一次点击只放大，不弹出对话框
        }
        
        // 地图已全屏
        if (isFirstMapClickAfterExpand) {
            // 这是地图全屏后的第一次点击，只调整视野，不弹出对话框
            isFirstMapClickAfterExpand = false;
            if (aMap != null && checkPoints.size() >= 2) {
                adjustMapToFitAllPoints();
            } else if (aMap != null) {
                // 如果没有打卡点，放大到合适的视野
                aMap.animateCamera(CameraUpdateFactory.zoomTo(15f));
            }
        } else {
            // 第二次及以后的点击，显示点击动效并弹出打卡点设置对话框
            showClickAnimation(latLng);
            showCheckpointDialog(latLng);
        }
    }
    
    /**
     * 显示点击地图的涟漪动效（波纹扩散效果）
     * 使用屏幕像素坐标，圆圈大小固定，不随地图缩放变化
     */
    private void showClickAnimation(LatLng latLng) {
        if (aMap == null || latLng == null || binding.rippleView == null) {
            return;
        }

        // 使用 Projection 将经纬度转换为屏幕像素坐标
        Point screenPoint = aMap.getProjection().toScreenLocation(latLng);
        
        // 显示涟漪动效
        binding.rippleView.showRipple(screenPoint.x, screenPoint.y);
    }
    
    /**
     * 调整地图视野以包含所有打卡点
     */
    private void adjustMapToFitAllPoints() {
        if (checkPoints.isEmpty() || aMap == null) {
            return;
        }
        try {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            for (CheckPoint point : checkPoints) {
                boundsBuilder.include(new LatLng(point.getLatitude(), point.getLongitude()));
            }
            LatLngBounds bounds = boundsBuilder.build();
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
        } catch (Exception e) {
            // 如果边界无效，忽略错误
        }
    }

    private void showCheckpointDialog(@Nullable LatLng latLng) {
        CheckpointBottomSheetDialogFragment fragment = CheckpointBottomSheetDialogFragment.newInstance(latLng);
        // 智能推荐类型
        String recommendedType = getRecommendedCheckpointType();
        fragment.setRecommendedType(recommendedType);
        fragment.setOnCheckpointConfirmedListener((name, lat, lng, type, radius) -> {
            addCheckpoint(name, lat, lng, type, radius);
        });
        fragment.show(getSupportFragmentManager(), "CheckpointBottomSheet");
    }

    private void addCheckpoint(String name, double lat, double lng) {
        // 保持向后兼容，默认使用检查点类型和默认半径
        addCheckpoint(name, lat, lng, TYPE_CHECKPOINT, DEFAULT_CHECK_RADIUS);
    }

    private void addCheckpoint(String name, double lat, double lng, String type, double radius) {
        if (checkPoints.size() >= MAX_CHECKPOINTS) {
            UIUtil.showToast(this, "打卡点数量已达上限");
            return;
        }
        if (isDuplicatePoint(name, lat, lng)) {
            UIUtil.showToast(this, "存在同名或坐标过近的打卡点");
            return;
        }
        
        // 检查是否已存在起点或终点
        if (TYPE_START.equals(type)) {
            for (CheckPoint point : checkPoints) {
                if (TYPE_START.equals(point.getType())) {
                    UIUtil.showToast(this, "已存在起点，请先删除后再添加");
                    return;
                }
            }
        }
        if (TYPE_END.equals(type)) {
            for (CheckPoint point : checkPoints) {
                if (TYPE_END.equals(point.getType())) {
                    UIUtil.showToast(this, "已存在终点，请先删除后再添加");
                    return;
                }
            }
        }
        
        CheckPoint point = new CheckPoint();
        String checkPointId = UUID.randomUUID().toString();
        point.setCheckPointId(checkPointId);
        point.setName(name);
        point.setLatitude(lat);
        point.setLongitude(lng);
        point.setType(type);
        point.setCheckRadius(radius);
        
        // 自动生成二维码 payload（如果有 raceId 则使用，否则等保存时再生成）
        if (editingRaceId != null) {
            String qrPayload = QrCodeGenerator.generateCheckPointPayload(editingRaceId, checkPointId);
            point.setQrCodePayload(qrPayload);
        }
        
        // 起点设置为1，终点设置为最后，检查点按添加顺序插入到终点之前
        int insertPosition = 0; // 记录插入位置，用于后续滚动
        if (TYPE_START.equals(type)) {
            point.setOrderIndex(1);
            // 将其他点的顺序后移
            for (CheckPoint p : checkPoints) {
                p.setOrderIndex(p.getOrderIndex() + 1);
            }
            checkPoints.add(0, point);
            adapter.notifyItemInserted(0);
            insertPosition = 0;
        } else if (TYPE_END.equals(type)) {
            point.setOrderIndex(checkPoints.size() + 1);
            checkPoints.add(point);
            insertPosition = checkPoints.size() - 1;
            adapter.notifyItemInserted(insertPosition);
        } else {
            // 检查点：计算合适的顺序（在起点之后，终点之前）
            int insertIndex = checkPoints.size();
            for (int i = 0; i < checkPoints.size(); i++) {
                if (TYPE_END.equals(checkPoints.get(i).getType())) {
                    insertIndex = i;
                    // 将终点及之后的所有点的顺序后移
                    for (int j = i; j < checkPoints.size(); j++) {
                        checkPoints.get(j).setOrderIndex(checkPoints.get(j).getOrderIndex() + 1);
                    }
                    break;
                }
            }
            point.setOrderIndex(insertIndex + 1);
            checkPoints.add(insertIndex, point);
            adapter.notifyItemInserted(insertIndex);
            insertPosition = insertIndex;
        }

        // 在地图上添加标记，显示序号
        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .title(point.getOrderIndex() + ". " + name + " (" + type + ")"));
        markers.add(marker);
        
        updateRoutePreview(); // 更新路线预览
        updateCheckpointCount(); // 更新打卡点数量显示
        autoSaveDraft(); // 自动保存草稿
        
        // 显示添加打卡点成功提示
        UIUtil.showToast(this, "已成功添加打卡点 " + name);
        
        // 延迟滚动，等待 RecyclerView 更新
        final int finalPosition = insertPosition;
        binding.rvCheckpoints.post(() -> {
            if (finalPosition >= 0 && finalPosition < checkPoints.size()) {
                binding.rvCheckpoints.smoothScrollToPosition(finalPosition);
            }
        });
    }
    
    /**
     * 更新打卡点数量显示
     */
    private void updateCheckpointCount() {
        int count = checkPoints.size();
        String mainText = "已添加 " + count + " 个打卡点";
        String hintText = "（点击地图创建）";
        String fullText = mainText + hintText;
        
        // 使用 SpannableString 设置不同样式
        android.text.SpannableString spannableString = new android.text.SpannableString(fullText);
        
        // 设置括号内提示文字的样式：更小字体、更浅颜色
        int hintStart = mainText.length();
        int hintEnd = fullText.length();
        spannableString.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), hintStart, hintEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new android.text.style.RelativeSizeSpan(0.75f), hintStart, hintEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        binding.tvCheckpointCount.setText(spannableString);
        
        // 同时更新 BottomSheet 中的计数
        if (binding.tvCheckpointCountBottomSheet != null) {
            android.text.SpannableString spannableStringBottomSheet = new android.text.SpannableString(fullText);
            spannableStringBottomSheet.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), hintStart, hintEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableStringBottomSheet.setSpan(new android.text.style.RelativeSizeSpan(0.75f), hintStart, hintEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            binding.tvCheckpointCountBottomSheet.setText(spannableStringBottomSheet);
        }
    }

    private boolean isDuplicatePoint(String name, double lat, double lng) {
        for (CheckPoint point : checkPoints) {
            if (point.getName().equalsIgnoreCase(name)) {
                return true;
            }
            if (Math.abs(point.getLatitude() - lat) < DUPLICATE_THRESHOLD
                    && Math.abs(point.getLongitude() - lng) < DUPLICATE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private void removeMarker(int position) {
        if (position >= 0 && position < markers.size()) {
            Marker marker = markers.remove(position);
            marker.remove();
        }
    }

    /**
     * 更新路线预览：根据打卡点顺序绘制路线
     */
    /**
     * 智能推荐打卡点类型
     * 逻辑：
     * - 如果没有打卡点，推荐起点
     * - 如果没有起点，推荐起点
     * - 如果没有终点且已有2个以上打卡点，推荐终点
     * - 其他情况推荐检查点
     */
    private String getRecommendedCheckpointType() {
        boolean hasStart = false;
        boolean hasFinish = false;
        
        for (CheckPoint point : checkPoints) {
            if (TYPE_START.equals(point.getType())) {
                hasStart = true;
            } else if (TYPE_END.equals(point.getType())) {
                hasFinish = true;
            }
        }
        
        // 没有打卡点或没有起点，推荐起点
        if (checkPoints.isEmpty() || !hasStart) {
            return TYPE_START;
        }
        
        // 已有起点，没有终点，且已有2个以上打卡点，推荐终点
        if (!hasFinish && checkPoints.size() >= 2) {
            return TYPE_END;
        }
        
        // 其他情况推荐检查点
        return TYPE_CHECKPOINT;
    }
    
    /**
     * 自动调整打卡点类型
     * 规则：
     * - 第一个点自动设为起点（如果不是）
     * - 最后一个点自动设为终点（如果不是）
     * - 其他点保持为检查点
     */
    private void autoAdjustCheckpointTypes() {
        if (checkPoints.isEmpty()) {
            return;
        }
        
        // 按 orderIndex 排序
        List<CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
        sortedPoints.sort((p1, p2) -> Integer.compare(p1.getOrderIndex(), p2.getOrderIndex()));
        
        // 第一个点设为起点
        if (!TYPE_START.equals(sortedPoints.get(0).getType())) {
            sortedPoints.get(0).setType(TYPE_START);
        }
        
        // 如果有多个点，最后一个设为终点
        if (sortedPoints.size() > 1) {
            CheckPoint lastPoint = sortedPoints.get(sortedPoints.size() - 1);
            if (!TYPE_END.equals(lastPoint.getType())) {
                lastPoint.setType(TYPE_END);
            }
        }
        
        // 中间的点保持为检查点
        for (int i = 1; i < sortedPoints.size() - 1; i++) {
            if (!TYPE_CHECKPOINT.equals(sortedPoints.get(i).getType())) {
                sortedPoints.get(i).setType(TYPE_CHECKPOINT);
            }
        }
        
        // 刷新显示
        adapter.notifyDataSetChanged();
    }

    private void updateRoutePreview() {
        // 移除旧的路线
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }

        // 如果打卡点少于2个，不绘制路线
        if (checkPoints.size() < 2) {
            return;
        }

        // 按照 orderIndex 排序打卡点
        List<CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
        sortedPoints.sort((p1, p2) -> Integer.compare(p1.getOrderIndex(), p2.getOrderIndex()));

        // 构建路线点列表
        List<LatLng> routePoints = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        
        for (CheckPoint point : sortedPoints) {
            LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());
            routePoints.add(latLng);
            boundsBuilder.include(latLng);
        }

        // 绘制路线：明亮黄色实线，宽度12px，更明显
        if (!routePoints.isEmpty()) {
            routePolyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(routePoints)
                    .width(12f) // 更粗的线条
                    .color(ContextCompat.getColor(this, com.example.cross_intelligence.R.color.route_yellow)) // 明亮黄色
                    .setDottedLine(false)); // 实线样式
            
            // 仅在有足够打卡点且地图未初始化视野时调整地图视野
            // 避免在用户手动缩放时频繁调整视野
            if (routePoints.size() >= 3) {
                try {
                    LatLngBounds bounds = boundsBuilder.build();
                    // 使用 newLatLngBounds 会自动调整视野，包含所有点
                    aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
                } catch (Exception e) {
                    // 如果边界无效，忽略错误
                }
            }
        }
    }

    /**
     * 自动保存草稿：将当前编辑内容保存到本地，无需联网
     */
    private void autoSaveDraft() {
        // 只有在有有效数据时才保存
        String name = textOf(binding.etRaceName);
        if (TextUtils.isEmpty(name) && checkPoints.isEmpty()) {
            return; // 没有有效数据，不保存
        }

        // 使用 SharedPreferences 保存草稿数据
        // 保存基本信息
        PreferenceUtil.editor(this)
                .putString("draft_race_name", textOf(binding.etRaceName))
                .putString("draft_race_description", textOf(binding.etDescription))
                .putString("draft_race_start_time", textOf(binding.etStartTime))
                .putString("draft_race_end_time", textOf(binding.etEndTime))
                .putInt("draft_checkpoints_count", checkPoints.size())
                .applyAsync();

        // 打卡点数据通过 Realm 保存（作为临时草稿）
        // 注意：这里只保存，不创建完整的 Race 对象，避免数据混乱
    }

    private void saveRace() {
        android.util.Log.d("CreateRaceActivity", "saveRace() 被调用");
        
        String name = textOf(binding.etRaceName);
        String description = textOf(binding.etDescription);
        String start = textOf(binding.etStartTime);
        String end = textOf(binding.etEndTime);

        android.util.Log.d("CreateRaceActivity", "表单数据 - name: " + name + ", start: " + start + ", end: " + end + ", checkPoints: " + checkPoints.size());

        RaceFormValidator.ValidationResult result =
                RaceFormValidator.validate(name, start, end, checkPoints, editingRaceId != null);
        if (!result.isValid()) {
            String message = result.getMessage();
            android.util.Log.d("CreateRaceActivity", "表单验证失败: " + message);
            if ("请输入赛事名称".equals(message)) {
                binding.tilRaceName.setError(message);
                // 添加左右抖动动画
                shakeView(binding.tilRaceName);
            } else {
                binding.tilRaceName.setError(null);
                UIUtil.showToast(this, message != null ? message : "表单校验失败");
            }
            return;
        }
        binding.tilRaceName.setError(null);

        // 获取当前登录的管理员账号
        String organizerId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(organizerId)) {
            android.util.Log.d("CreateRaceActivity", "无法获取管理员账号");
            UIUtil.showToast(this, "无法获取管理员账号信息，请重新登录");
            return;
        }
        
        android.util.Log.d("CreateRaceActivity", "开始保存赛事，organizerId: " + organizerId);
        
        // 生成临时 raceId（用于生成缩略图文件名）
        final String tempRaceId = editingRaceId != null ? editingRaceId : UUID.randomUUID().toString();
        
        // 先显示保存提示，提升用户体验
        UIUtil.showToast(this, "正在保存赛事...");
        
        // 自动生成地图缩略图（静默保存）
        // 使用超时机制，如果截图流程超过5秒，直接保存赛事
        final boolean[] hasSaved = {false};
        android.util.Log.d("CreateRaceActivity", "========== 开始保存流程 ==========");
        android.util.Log.d("CreateRaceActivity", "tempRaceId: " + tempRaceId);
        android.util.Log.d("CreateRaceActivity", "checkPoints.isEmpty(): " + checkPoints.isEmpty() + ", aMap == null: " + (aMap == null) + ", mapView == null: " + (mapView == null));
        
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!hasSaved[0]) {
                android.util.Log.w("CreateRaceActivity", "========== 截图流程超时（5秒），直接保存赛事（thumbnailPath = null）==========");
                hasSaved[0] = true;
                saveRaceWithThumbnail(null, name, description, start, end, organizerId, tempRaceId);
            }
        }, 5000); // 5秒超时，给地图截图足够的时间
        
        if (!checkPoints.isEmpty()) {
            android.util.Log.d("CreateRaceActivity", "条件满足，开始生成缩略图，打卡点数量: " + checkPoints.size());
            // 直接使用隐藏 MapView 进行后台截图，不依赖可见地图
                        captureMapScreenshot(tempRaceId, name, description, start, end, organizerId, hasSaved);
        } else {
            android.util.Log.w("CreateRaceActivity", "========== 跳过缩略图生成，直接保存赛事 ==========");
            android.util.Log.w("CreateRaceActivity", "原因: checkPoints.isEmpty()=" + checkPoints.isEmpty() + ", aMap==null=" + (aMap == null) + ", mapView==null=" + (mapView == null));
            // 没有打卡点或地图未初始化，直接保存赛事（不包含缩略图）
            if (!hasSaved[0]) {
                hasSaved[0] = true;
                saveRaceWithThumbnail(null, name, description, start, end, organizerId, tempRaceId);
            }
        }
    }
    
    /**
     * 截取地图截图并保存（使用现有的可见 MapView 进行瞬时前台截图）
     */
    private void captureMapScreenshot(String raceId, String name, String description, 
                                      String start, String end, String organizerId, boolean[] hasSaved) {
        android.util.Log.d("CreateRaceActivity", "========== 开始截图（使用现有 MapView）==========");
        android.util.Log.d("CreateRaceActivity", "raceId: " + raceId);
        if (hasSaved[0]) {
            android.util.Log.w("CreateRaceActivity", "已经保存，跳过截图");
            return;
        }
        
        // 确保在主线程
        runOnUiThread(() -> {
            if (aMap == null || mapView == null) {
                android.util.Log.w("CreateRaceActivity", "地图未初始化，跳过截图");
                if (!hasSaved[0]) {
                    hasSaved[0] = true;
                    saveRaceWithThumbnail(null, name, description, start, end, organizerId, raceId);
                }
                return;
            }
            
            // 保存当前地图容器的可见性状态
            final boolean wasCollapsed = isBasicInfoCollapsed;
            final int mapCardVisibility = binding.mapCardContainer.getVisibility();
            final int mapViewVisibility = binding.mapView.getVisibility();
            
            android.util.Log.d("CreateRaceActivity", "保存地图状态 - wasCollapsed: " + wasCollapsed + ", mapCardVisibility: " + mapCardVisibility + ", mapViewVisibility: " + mapViewVisibility);
            
            // 1. 确保地图容器和地图可见（即使被 Loading 遮住）
            binding.mapCardContainer.setVisibility(View.VISIBLE);
            binding.mapView.setVisibility(View.VISIBLE);
            
            // 2. 强制给地图分配尺寸并刷新（确保不是零高度）
            final android.view.ViewGroup.LayoutParams mapCardParams = binding.mapCardContainer.getLayoutParams();
            if (mapCardParams.height <= 0) {
                // 如果高度为0或无效，设置一个固定高度
                int minHeight = (int) (300 * getResources().getDisplayMetrics().density);
                mapCardParams.height = minHeight;
                android.util.Log.d("CreateRaceActivity", "地图容器高度为0，设置为: " + minHeight);
            }
            binding.mapCardContainer.setLayoutParams(mapCardParams);
            binding.mapCardContainer.requestLayout();
            binding.mapView.requestLayout();
            
            // 3. 如果地图在 ScrollView 内，确保滚动到顶部使其可见
            if (binding.mainContentLayout != null) {
                // 尝试查找父 ScrollView 或 NestedScrollView
                android.view.ViewParent parent = binding.mapCardContainer.getParent();
                while (parent != null) {
                    if (parent instanceof androidx.core.widget.NestedScrollView) {
                        android.util.Log.d("CreateRaceActivity", "检测到 NestedScrollView，滚动到顶部");
                        ((androidx.core.widget.NestedScrollView) parent).scrollTo(0, 0);
                        break;
                    } else if (parent instanceof android.widget.ScrollView) {
                        android.util.Log.d("CreateRaceActivity", "检测到 ScrollView，滚动到顶部");
                        ((android.widget.ScrollView) parent).scrollTo(0, 0);
                        break;
                    }
                    parent = parent.getParent();
                }
            }
            
            // 4. 等待布局完成后继续
            binding.mapView.post(() -> {
                // 检查地图是否真的可见且有尺寸
                int mapCardHeight = binding.mapCardContainer.getHeight();
                int mapViewHeight = binding.mapView.getHeight();
                boolean isMapShown = binding.mapView.isShown();
                
                android.util.Log.d("CreateRaceActivity", "地图状态检查 - mapCardHeight: " + mapCardHeight + 
                    ", mapViewHeight: " + mapViewHeight + ", isShown: " + isMapShown);
                
                if (mapViewHeight <= 0 || !isMapShown) {
                    android.util.Log.w("CreateRaceActivity", "地图高度为0或不可见，强制设置高度");
                    // 再次强制设置高度
                    if (mapCardHeight <= 0) {
                        android.view.ViewGroup.LayoutParams params = binding.mapCardContainer.getLayoutParams();
                        params.height = (int) (400 * getResources().getDisplayMetrics().density);
                        binding.mapCardContainer.setLayoutParams(params);
                        binding.mapCardContainer.requestLayout();
                    }
                    // 再等待一下让布局完成
                    binding.mapView.postDelayed(() -> {
                        performScreenshot(raceId, name, description, start, end, organizerId, hasSaved, wasCollapsed, mapCardVisibility, mapViewVisibility);
                    }, 200);
                } else {
                    performScreenshot(raceId, name, description, start, end, organizerId, hasSaved, wasCollapsed, mapCardVisibility, mapViewVisibility);
                }
            });
        });
    }
    
    /**
     * 执行截图操作
     */
    private void performScreenshot(String raceId, String name, String description, 
                                   String start, String end, String organizerId, boolean[] hasSaved,
                                   boolean wasCollapsed, int mapCardVisibility, int mapViewVisibility) {
        // 1. 确保路线已经添加到地图上（如果还没有）
        if (routePolyline == null && checkPoints.size() >= 2) {
            android.util.Log.d("CreateRaceActivity", "路线未添加，先添加路线");
            updateRoutePreview();
        }
        
        // 2. 按顺序排序打卡点
        List<CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
        sortedPoints.sort(Comparator.comparingInt(CheckPoint::getOrderIndex));
        
        // 3. 计算边界（包含所有打卡点）
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (CheckPoint cp : sortedPoints) {
            boundsBuilder.include(new LatLng(cp.getLatitude(), cp.getLongitude()));
        }
        LatLngBounds bounds = boundsBuilder.build();
        
        android.util.Log.d("CreateRaceActivity", "计算边界完成，打卡点数量: " + sortedPoints.size() + "，开始移动相机");
        
        // 4. 确保路线已绘制（重新绘制以确保完整显示）
        if (checkPoints.size() >= 2) {
            // 移除旧路线
            if (routePolyline != null) {
                routePolyline.remove();
                routePolyline = null;
            }
            
            // 重新绘制路线，确保使用正确的颜色和宽度
            List<LatLng> routePoints = new ArrayList<>();
            for (CheckPoint cp : sortedPoints) {
                routePoints.add(new LatLng(cp.getLatitude(), cp.getLongitude()));
            }
            routePolyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(routePoints)
                    .width(12f)
                    .color(ContextCompat.getColor(this, com.example.cross_intelligence.R.color.route_yellow))
                    .setDottedLine(false));
            android.util.Log.d("CreateRaceActivity", "路线已重新绘制，包含 " + routePoints.size() + " 个点");
        }
        
        // 5. 使用 moveCamera 调整视野，增加边距以确保所有打卡点和轨迹线都完整显示
        // 动态计算安全边距：不超过地图短边的 20%，防止溢出
        // 你的日志显示地图高度为 611px，而你之前计算的 padding 达到了 611px (300dp * density)，这导致 padding 占满了整个屏幕，地图无法正确显示
        int mapWidth = binding.mapView.getWidth();
        int mapHeight = binding.mapView.getHeight();
        if (mapWidth <= 0 || mapHeight <= 0) {
            mapWidth = 800; // 默认值
            mapHeight = 600; // 默认值
        }
        
        // 计算安全边距：取宽高的较小值的 20%
        int safePadding = (int) (Math.min(mapWidth, mapHeight) * 0.2);
        
        // 确保最小边距为 50px，最大不超过 200px (避免过大)
        safePadding = Math.max(50, Math.min(safePadding, 200));
        
        android.util.Log.d("CreateRaceActivity", "地图尺寸: " + mapWidth + "x" + mapHeight + ", 计算的安全边距: " + safePadding + " px");
        
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, safePadding);
        
        // 关键修复：使用回调确保动画完成后再截图
        // 不要使用 postDelayed，因为不同设备性能差异很大
        aMap.animateCamera(cameraUpdate, 1000, new AMap.CancelableCallback() {
            @Override
            public void onFinish() {
                android.util.Log.d("CreateRaceActivity", "animateCamera 动画完成，准备截图");
                // 动画完成后，再等待一小会儿确保渲染稳定
                binding.mapView.postDelayed(() -> {
                    performActualScreenshot(raceId, name, description, start, end, organizerId, hasSaved, wasCollapsed, mapCardVisibility, mapViewVisibility);
                }, 300);
            }

            @Override
            public void onCancel() {
                android.util.Log.w("CreateRaceActivity", "animateCamera 动画被取消，强行截图");
                performActualScreenshot(raceId, name, description, start, end, organizerId, hasSaved, wasCollapsed, mapCardVisibility, mapViewVisibility);
            }
        });
        
        // 计算打卡点之间的距离，如果距离很远，使用更大的边距（用于后续判断渲染延迟）
        double maxDistance = calculateMaxDistance(sortedPoints);
    }
    
    /**
     * 计算打卡点之间的最大距离（米）
     */
    private double calculateMaxDistance(List<CheckPoint> sortedPoints) {
        if (sortedPoints.size() < 2) {
            return 0;
        }
        
        double maxDistance = 0;
        for (int i = 0; i < sortedPoints.size() - 1; i++) {
            CheckPoint p1 = sortedPoints.get(i);
            CheckPoint p2 = sortedPoints.get(i + 1);
            
            // 使用 Haversine 公式计算两点之间的距离
            double lat1 = Math.toRadians(p1.getLatitude());
            double lat2 = Math.toRadians(p2.getLatitude());
            double lon1 = Math.toRadians(p1.getLongitude());
            double lon2 = Math.toRadians(p2.getLongitude());
            
            double dLat = lat2 - lat1;
            double dLon = lon2 - lon1;
            
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                      Math.cos(lat1) * Math.cos(lat2) *
                      Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            
            double distance = 6371000 * c; // 地球半径 6371000 米
            maxDistance = Math.max(maxDistance, distance);
        }
        
        return maxDistance;
    }
    
    /**
     * 执行实际的截图操作
     */
    private void performActualScreenshot(String raceId, String name, String description,
                                         String start, String end, String organizerId, boolean[] hasSaved,
                                         boolean wasCollapsed, int mapCardVisibility, int mapViewVisibility) {
        android.util.Log.d("CreateRaceActivity", "开始调用 getMapScreenShot");
        
        // 使用原子布尔值来确保只处理一次回调
        final java.util.concurrent.atomic.AtomicBoolean callbackHandled = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        aMap.getMapScreenShot(new AMap.OnMapScreenShotListener() {
            @Override
            public void onMapScreenShot(Bitmap bitmap) {
                // 如果已经处理过回调，直接返回
                if (callbackHandled.getAndSet(true)) {
                    android.util.Log.w("CreateRaceActivity", "重复触发 onMapScreenShot(Bitmap)，已忽略");
                    // 严重注意：绝对不能在这里 recycle bitmap！
                    // 因为高德 SDK 可能在两次回调中传递的是同一个 Bitmap 实例
                    // 如果我们在第一次回调中正在使用（复制）这个 bitmap，而在这里把它 recycle 了，
                    // 就会导致 copy 操作或者后续操作崩溃！
                    // 让 GC 去处理多余的引用即可。
                    return;
                }

                android.util.Log.d("CreateRaceActivity", "========== 地图截图回调触发 ==========");
                android.util.Log.d("CreateRaceActivity", "bitmap: " + (bitmap != null ? ("非空，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight()) : "空"));
                
                // 截图完成后，恢复地图的可见性状态（如果原本是收起状态）
                if (wasCollapsed && binding.mapCardContainer.getVisibility() == View.VISIBLE) {
                    android.util.Log.d("CreateRaceActivity", "恢复地图收起状态");
                    binding.mapCardContainer.setVisibility(mapCardVisibility);
                    binding.mapView.setVisibility(mapViewVisibility);
                }
                
                // 处理图片并最终保存（确保在主线程执行）
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    handleScreenshotResult(bitmap, raceId, name, description, start, end, organizerId, hasSaved);
                } else {
                    runOnUiThread(() -> {
                        handleScreenshotResult(bitmap, raceId, name, description, start, end, organizerId, hasSaved);
                    });
                }
            }
            
            @Override
            public void onMapScreenShot(Bitmap bitmap, int i) {
                // 某些版本的 SDK 可能会同时触发两个回调，或者优先触发这个带参数的
                // 这里的处理逻辑应该与上面一致，但必须经过原子锁检查
                
                // 如果已经处理过回调，直接返回
                if (callbackHandled.getAndSet(true)) {
                    android.util.Log.w("CreateRaceActivity", "重复触发 onMapScreenShot(Bitmap, int)，已忽略");
                    // 严重注意：绝对不能在这里 recycle bitmap！
                    // 因为高德 SDK 可能在两次回调中传递的是同一个 Bitmap 实例
                    // 如果我们在第一次回调中正在使用（复制）这个 bitmap，而在这里把它 recycle 了，
                    // 就会导致 copy 操作或者后续操作崩溃！
                    // 让 GC 去处理多余的引用即可。
                    return;
                }
                
                android.util.Log.d("CreateRaceActivity", "========== 地图截图回调触发 (带参数) ==========");
                // 手动调用处理逻辑
                
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    handleScreenshotResult(bitmap, raceId, name, description, start, end, organizerId, hasSaved);
                } else {
                    runOnUiThread(() -> {
                        handleScreenshotResult(bitmap, raceId, name, description, start, end, organizerId, hasSaved);
                    });
                }
            }
        });
    }
    
    
    /**
     * 统一处理截图结果
     */
    private void handleScreenshotResult(Bitmap bitmap, String raceId, String name, String description, 
                                        String start, String end, String organizerId, boolean[] hasSaved) {
        // 1. 立即检查并加锁，必须在主线程第一时间执行
        if (hasSaved[0] || bitmap == null || bitmap.isRecycled()) {
            android.util.Log.w("CreateRaceActivity", "已经处理过或图片无效，跳过");
            return;
        }
        
        // 2. 核心修改：在启动线程前就标记为已保存，防止第二个回调进入
        hasSaved[0] = true;
        
        android.util.Log.d("CreateRaceActivity", "开始保存缩略图文件...");
        
        // 关键修复：必须在主线程创建 Bitmap 的深拷贝！
        // 因为高德 SDK 可能会在 onMapScreenShot 方法返回后立即回收传入的 bitmap 实例。
        // 如果我们在子线程中直接使用原 bitmap，就会遭遇 "recycled bitmap" 错误。
        // 创建副本可以彻底切断与 SDK 内部 Bitmap 生命周期的关联。
        final Bitmap bitmapCopy;
        try {
            if (bitmap.isRecycled()) {
                 android.util.Log.e("CreateRaceActivity", "Bitmap recycled before copy.");
                 saveRaceWithThumbnail(null, name, description, start, end, organizerId, raceId);
                 return;
            }
            bitmapCopy = bitmap.copy(bitmap.getConfig(), true);
        } catch (Exception e) {
            android.util.Log.e("CreateRaceActivity", "Bitmap 复制失败", e);
            // 复制失败则直接尝试不带图保存
            saveRaceWithThumbnail(null, name, description, start, end, organizerId, raceId);
            return;
        }

        // 3. 异步保存（使用副本）
        new Thread(() -> {
            try {
                // 在子线程再次确认图片状态
                if (bitmapCopy.isRecycled()) return;

                String thumbnailPath = MapThumbnailUtil.saveThumbnailFromBitmap(
                        CreateRaceActivity.this, raceId, bitmapCopy);
                
                android.util.Log.d("CreateRaceActivity", "缩略图保存成功: " + thumbnailPath);
                
                // 保存完成后，我们自己创建的副本可以回收了
                if (!bitmapCopy.isRecycled()) {
                    bitmapCopy.recycle();
                }

                // 4. 回到主线程操作 Realm
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    saveRaceWithThumbnail(thumbnailPath, name, description, start, end, 
                            organizerId, raceId);
                });
            } catch (Exception e) {
                android.util.Log.e("CreateRaceActivity", "保存过程出错", e);
                // 出错时尝试不带图保存，保证业务不中断
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    saveRaceWithThumbnail(null, name, description, start, end, organizerId, raceId);
                });
            }
        }).start();
    }
            
    /**
     * 备用方案：直接对 MapView 进行 View 截图
     */
    private Bitmap captureMapViewAsBitmap() {
        try {
            android.util.Log.d("CreateRaceActivity", "使用备用方案：View 截图");
            if (mapView == null) {
                android.util.Log.w("CreateRaceActivity", "mapView 为 null");
                return null;
            }
            
            int width = mapView.getWidth();
            int height = mapView.getHeight();
            android.util.Log.d("CreateRaceActivity", "MapView 原始尺寸: " + width + "x" + height);
            
            // 如果尺寸无效，使用固定尺寸
            if (width <= 0 || height <= 0) {
                android.util.Log.w("CreateRaceActivity", "MapView 尺寸无效: " + width + "x" + height + "，使用固定尺寸 800x600");
                width = 800;
                height = 600;
            }
            
            android.util.Log.d("CreateRaceActivity", "准备创建 Bitmap，尺寸: " + width + "x" + height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (bitmap == null) {
                android.util.Log.e("CreateRaceActivity", "创建 Bitmap 失败");
                return null;
            }
            
            android.util.Log.d("CreateRaceActivity", "Bitmap 创建成功，开始绘制");
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            // 如果 MapView 尺寸为 0，需要先设置一个临时尺寸进行绘制
            if (mapView.getWidth() <= 0 || mapView.getHeight() <= 0) {
                android.util.Log.d("CreateRaceActivity", "MapView 尺寸为 0，使用固定尺寸进行绘制");
                // 保存原始布局参数
                int oldLeft = mapView.getLeft();
                int oldTop = mapView.getTop();
                int oldRight = mapView.getRight();
                int oldBottom = mapView.getBottom();
                
                // 临时设置尺寸
                mapView.layout(0, 0, width, height);
                mapView.draw(canvas);
                
                // 恢复原始布局（虽然可能也是 0，但保持一致性）
                mapView.layout(oldLeft, oldTop, oldRight, oldBottom);
            } else {
                mapView.draw(canvas);
            }
            
            android.util.Log.d("CreateRaceActivity", "View 截图成功，尺寸: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("CreateRaceActivity", "View 截图失败", e);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 调整地图视野以包含所有打卡点
     */
    private void adjustMapViewToFitCheckpoints(Runnable onComplete) {
        android.util.Log.d("CreateRaceActivity", "adjustMapViewToFitCheckpoints() 被调用");
        
        if (checkPoints.isEmpty() || aMap == null) {
            android.util.Log.d("CreateRaceActivity", "打卡点为空或地图未初始化，直接执行回调");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        // 构建包含所有打卡点的边界
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (CheckPoint point : checkPoints) {
            boundsBuilder.include(new LatLng(point.getLatitude(), point.getLongitude()));
        }
        LatLngBounds bounds = boundsBuilder.build();
        
        android.util.Log.d("CreateRaceActivity", "开始调整地图视野");
        
        // 边距不应超过短边的 20%，防止坐标计算溢出导致“乱飞”
        int safePadding = (int) (Math.min(binding.mapView.getWidth(), binding.mapView.getHeight()) * 0.2);
        
        // 1. 设置渲染完成监听（最保险）
        aMap.setOnMapLoadedListener(() -> {
            aMap.setOnMapLoadedListener(null); // 执行一次即销毁
            if (onComplete != null) onComplete.run();
        });

        // 2. 执行移动
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, safePadding);
        aMap.moveCamera(cameraUpdate);
    }
    
    /**
     * 保存赛事（包含缩略图路径）
     */
    private void saveRaceWithThumbnail(String thumbnailPath, String name, String description, 
                                       String start, String end, String organizerId, String raceId) {
        android.util.Log.d("CreateRaceActivity", "========== saveRaceWithThumbnail() 被调用 ==========");
        android.util.Log.d("CreateRaceActivity", "raceId: " + raceId);
        android.util.Log.d("CreateRaceActivity", "thumbnailPath: " + thumbnailPath);
        android.util.Log.d("CreateRaceActivity", "name: " + name);
        
        // 解析时间
        Date startDate = null;
        Date endDate = null;
        try {
            if (!TextUtils.isEmpty(start)) {
                startDate = dateTimeFormat.parse(start);
            }
            if (!TextUtils.isEmpty(end)) {
                endDate = dateTimeFormat.parse(end);
            }
        } catch (Exception e) {
            android.util.Log.e("CreateRaceActivity", "时间解析失败", e);
            runOnUiThread(() -> UIUtil.showToast(this, "时间格式错误"));
            return;
        }
        
        android.util.Log.d("CreateRaceActivity", "时间解析完成，startDate: " + startDate + ", endDate: " + endDate);
        
        // 提取 CheckPoint 数据
        List<RaceManager.CheckPointData> checkpointDataList = new ArrayList<>();
        for (CheckPoint point : checkPoints) {
            RaceManager.CheckPointData data = new RaceManager.CheckPointData();
            data.checkPointId = point.getCheckPointId();
            data.name = point.getName();
            data.latitude = point.getLatitude();
            data.longitude = point.getLongitude();
            data.type = point.getType();
            data.checkRadius = point.getCheckRadius();
            data.orderIndex = point.getOrderIndex();
            
            // 生成二维码 payload（如果还没有的话）
            if (point.getQrCodePayload() == null || point.getQrCodePayload().isEmpty()) {
                data.qrCodePayload = QrCodeGenerator.generateCheckPointPayload(raceId, point.getCheckPointId());
            } else {
                data.qrCodePayload = point.getQrCodePayload();
            }
            
            checkpointDataList.add(data);
        }
        
        // 保存或更新赛事（使用异步回调）
        RaceManager.SaveCallback callback = new RaceManager.SaveCallback() {
            @Override
            public void onSuccess() {
                // 在主线程更新UI
                runOnUiThread(() -> {
                    clearDraft(); // 清除草稿
                    
                    // 显示成功提示
                    String message = editingRaceId != null ? "赛事更新成功！" : "赛事保存成功！";
                    UIUtil.showToast(CreateRaceActivity.this, message);
                    
                    // 延迟跳转，让用户看到提示信息
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // 返回管理员主页
                        Intent intent = new Intent(CreateRaceActivity.this, AdminMainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    }, 1000); // 延迟1秒，让用户看到Toast提示
                });
            }

            @Override
            public void onError(@NonNull Throwable error) {
                // 在主线程显示错误信息
                runOnUiThread(() -> {
                    error.printStackTrace();
                    String errorMsg = error.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = error.getClass().getSimpleName();
                    }
                    UIUtil.showToast(CreateRaceActivity.this, "保存失败：" + errorMsg);
                });
            }
        };
        
        android.util.Log.d("CreateRaceActivity", "准备调用 RaceManager，editingRaceId: " + editingRaceId);
        android.util.Log.d("CreateRaceActivity", "传递给 RaceManager 的 thumbnailPath: " + thumbnailPath);
        
        if (editingRaceId != null) {
            // 编辑模式：更新现有赛事
            android.util.Log.d("CreateRaceActivity", "调用 updateRace()");
            raceManager.updateRace(editingRaceId, name, description, startDate, endDate, 
                    checkpointDataList, thumbnailPath, callback);
        } else {
            // 创建模式：使用指定的 raceId 创建新赛事
            android.util.Log.d("CreateRaceActivity", "调用 createRaceWithId()");
            raceManager.createRaceWithId(raceId, name, description, startDate, endDate, 
                    checkpointDataList, organizerId, thumbnailPath, callback);
        }
        android.util.Log.d("CreateRaceActivity", "========== saveRaceWithThumbnail() 调用完成 ==========");
    }

    private void reindexCheckpoints() {
        for (int i = 0; i < checkPoints.size(); i++) {
            checkPoints.get(i).setOrderIndex(i + 1);
        }
    }

    /**
     * 显示打卡点二维码对话框
     */
    private void showQrCodeDialog(CheckPoint checkPoint) {
        // 获取 raceId：编辑模式使用 editingRaceId，创建模式使用临时 raceId
        String raceId = editingRaceId != null ? editingRaceId : 
            (checkPoint.getRaceId() != null ? checkPoint.getRaceId() : 
            java.util.UUID.randomUUID().toString());
        
        // 如果打卡点还没有 raceId，设置它
        if (checkPoint.getRaceId() == null || checkPoint.getRaceId().isEmpty()) {
            checkPoint.setRaceId(raceId);
        }
        
        // 生成二维码
        Bitmap qrBitmap = QrCodeGenerator.generateCheckPointQrCode(
                raceId,
                checkPoint.getCheckPointId()
        );

        if (qrBitmap == null) {
            UIUtil.showToast(this, "生成二维码失败");
            return;
        }

        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null);
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

        // 关闭按钮
        MaterialButton btnClose = dialogView.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * 保存二维码到相册
     */
    private void saveQrCode(Bitmap bitmap, String checkPointName) {
        String fileName = QrCodeUtil.generateQrCodeFileName(checkPointName);
        boolean success = QrCodeUtil.saveQrCodeToGallery(this, bitmap, fileName);
        if (success) {
            UIUtil.showToast(this, "二维码已保存到相册");
        } else {
            UIUtil.showToast(this, "保存失败，请检查存储权限");
        }
    }

    private String textOf(@NonNull TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    /**
     * 为文本输入框添加自动保存监听器
     */
    private void addTextWatcher(TextInputEditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                autoSaveDraft();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // 启动定位
        if (locationManager != null) {
            locationManager.setHighPrecision(true);
            locationManager.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        // 停止定位
        if (locationManager != null) {
            locationManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        // 清理涟漪动效资源
        if (binding != null && binding.rippleView != null) {
            binding.rippleView.cleanup();
        }
        super.onDestroy();
        // 页面销毁时自动保存草稿
        autoSaveDraft();
        // 清理路线预览
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
        // 销毁定位管理器
        if (locationManager != null) {
            locationManager.destroy();
            locationManager = null;
        }
        mapView.onDestroy();
    }

    /**
     * 清除草稿数据
     */
    private void clearDraft() {
        PreferenceUtil.editor(this)
                .remove("draft_race_name")
                .remove("draft_race_description")
                .remove("draft_race_start_time")
                .remove("draft_race_end_time")
                .remove("draft_checkpoints_count")
                .applyAsync();
    }

    // ========== 定位回调接口实现 ==========

    @Override
    public void onLocationUpdate(double lat, double lng, float accuracy) {
        // 当定位更新时，将地图中心移动到当前位置
        // 仅在首次定位时移动，避免干扰用户手动操作地图
        runOnUiThread(() -> {
            if (aMap != null && isFirstLocation) {
                // 首次定位时，平滑移动到当前位置并设置合适的缩放级别
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 16f));
                isFirstLocation = false; // 标记已进行首次定位
            }
        });
    }

    @Override
    public void onLocationError(int errorCode, String errorInfo) {
        // 定位错误时显示提示（可选，不阻塞用户操作）
        runOnUiThread(() -> {
            // 静默处理，不显示错误提示，避免干扰用户创建赛事
            // 如果需要，可以在这里添加日志记录
        });
    }

    /**
     * 左右抖动动画：用于输入校验错误提示
     */
    private void shakeView(android.view.View view) {
        android.view.animation.TranslateAnimation shake = new android.view.animation.TranslateAnimation(0, 10, 0, 0);
        shake.setDuration(50);
        shake.setRepeatCount(5);
        shake.setRepeatMode(android.view.animation.Animation.REVERSE);
        view.startAnimation(shake);
    }

    /**
     * 切换基础信息的显示/隐藏状态
     * 使用 TransitionManager 和动态约束修改实现平滑折叠效果
     */
    private void toggleBasicInfo() {
        isBasicInfoCollapsed = !isBasicInfoCollapsed;
        
        // 使用 TransitionManager 实现平滑动画
        TransitionManager.beginDelayedTransition(binding.mainContentLayout);
        
        if (isBasicInfoCollapsed) {
            // 折叠：隐藏基础信息卡片
            binding.cardBasicInfo.setVisibility(android.view.View.GONE);
            
            // 隐藏 Toolbar
            binding.toolbar.setVisibility(android.view.View.GONE);
            
            // 隐藏保存按钮
            binding.btnSaveRace.setVisibility(android.view.View.GONE);
            
            // 隐藏正常状态的打卡点卡片
            binding.cardCheckpoints.setVisibility(android.view.View.GONE);
            
            // 显示搜索栏（地图全屏时）
            binding.topSearchCard.setVisibility(android.view.View.VISIBLE);
            
            // 显示 BottomSheet
            binding.bottomSheetCard.setVisibility(android.view.View.VISIBLE);
            
            // 移除 mainContentLayout 的 padding，让地图真正全屏
            binding.mainContentLayout.setPadding(0, 0, 0, 0);
            
            // 移除地图容器的边框和圆角，让它真正全屏
            binding.mapCardContainer.setCardElevation(0);
            // 使用 CardView 的方法设置圆角
            binding.mapCardContainer.setRadius(0);
            
            // 确保地图容器可见且始终在底层
            binding.mapCardContainer.setVisibility(android.view.View.VISIBLE);
            binding.mapView.setVisibility(android.view.View.VISIBLE);
            
            // 修改地图容器的约束，让它填满整个空间
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams mapParams = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.mapCardContainer.getLayoutParams();
            mapParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET; // 清除底部约束
            mapParams.topMargin = 0;
            mapParams.leftMargin = 0;
            mapParams.rightMargin = 0;
            binding.mapCardContainer.setLayoutParams(mapParams);
            
            // 确保地图容器在底层（elevation 最低）
            binding.mapCardContainer.setElevation(0f);
            binding.mapView.setElevation(0f);
            
            // 强制请求布局
            binding.mapCardContainer.requestLayout();
            binding.mainContentLayout.requestLayout();
            
            // 确保地图视图正确显示
            binding.mapView.post(() -> {
                binding.mapView.setVisibility(android.view.View.VISIBLE);
                binding.mapCardContainer.setVisibility(android.view.View.VISIBLE);
            });
            
            // 确保打卡点卡片显示在地图上方（固定悬浮）
            binding.bottomSheetCard.bringToFront();
            binding.bottomSheetCard.setElevation(15f);
            
            // 更新按钮文本为"手动添加打卡点"
            binding.btnManualAddCheckpoint.setText("手动添加打卡点");
            binding.btnManualAddCheckpoint.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_add));
            
            // 重置第一次点击标志（只有通过点击地图进入全屏时才需要，通过按钮进入时直接可以点击地图设置打卡点）
            // 这里不设置 isFirstMapClickAfterExpand = true，因为用户是通过按钮主动进入地图模式的，应该可以直接设置打卡点
            isFirstMapClickAfterExpand = false;
            
            // 确保地图已初始化
            if (aMap == null) {
                initMap();
            }
            
            // 延迟一下再调整地图视野，确保布局已完成
            binding.mainContentLayout.postDelayed(() -> {
                // 再次确保地图视图可见
                binding.mapView.setVisibility(android.view.View.VISIBLE);
                binding.mapCardContainer.setVisibility(android.view.View.VISIBLE);
                
                // 强制刷新地图视图
                binding.mapView.invalidate();
                binding.mapCardContainer.invalidate();
                binding.mapView.requestLayout();
                
                // 再次延迟，确保布局完成后再操作地图
                binding.mapView.postDelayed(() -> {
                    if (aMap != null) {
                        try {
                            // 调整地图视野，显示所有打卡点（如果有）
                            if (checkPoints.size() >= 2) {
                                adjustMapToFitAllPoints();
                            } else {
                                // 如果没有打卡点，放大到合适的视野
                                aMap.animateCamera(CameraUpdateFactory.zoomTo(15f));
                            }
                        } catch (Exception e) {
                            // 忽略异常，至少确保地图可见
                        }
                    }
                }, 100);
            }, 50);
        } else {
            // 展开：显示基础信息卡片
            binding.cardBasicInfo.setVisibility(android.view.View.VISIBLE);
            
            // 显示 Toolbar
            binding.toolbar.setVisibility(android.view.View.VISIBLE);
            
            // 显示保存按钮
            binding.btnSaveRace.setVisibility(android.view.View.VISIBLE);
            
            // 显示正常状态的打卡点卡片（退出地图后显示）
            binding.cardCheckpoints.setVisibility(android.view.View.VISIBLE);
            
            // 隐藏搜索栏（退出地图全屏时）
            binding.topSearchCard.setVisibility(android.view.View.GONE);
            hideSearchResults(); // 同时隐藏搜索结果
            
            // 隐藏悬浮打卡点卡片
            binding.bottomSheetCard.setVisibility(android.view.View.GONE);
            
            // 提示标签始终显示，不需要控制显示/隐藏
            
            // 恢复 mainContentLayout 的 padding
            int padding16dp = (int) (16 * getResources().getDisplayMetrics().density);
            binding.mainContentLayout.setPadding(padding16dp, padding16dp, padding16dp, padding16dp);
            
            // 恢复地图容器的边框和圆角
            binding.mapCardContainer.setCardElevation(4);
            binding.mapCardContainer.setRadius(16 * getResources().getDisplayMetrics().density);
            
            // 确保地图容器可见
            binding.mapCardContainer.setVisibility(android.view.View.VISIBLE);
            binding.mapView.setVisibility(android.view.View.VISIBLE);
            
            // 恢复地图容器的约束
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams mapParams = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.mapCardContainer.getLayoutParams();
            mapParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.bottomToTop = binding.cardCheckpoints.getId();
            mapParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET; // 清除底部约束
            int margin16dp = (int) (16 * getResources().getDisplayMetrics().density);
            mapParams.topMargin = margin16dp;
            mapParams.leftMargin = 0;
            mapParams.rightMargin = 0;
            binding.mapCardContainer.setLayoutParams(mapParams);
            binding.mapCardContainer.requestLayout();
            
            // 强制刷新地图视图
            binding.mainContentLayout.post(() -> {
                binding.mapView.invalidate();
                binding.mapCardContainer.invalidate();
            });
            
            // 恢复打卡点卡片的约束
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams cpParams = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.cardCheckpoints.getLayoutParams();
            cpParams.topToBottom = binding.mapCardContainer.getId();
            cpParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
            cpParams.topMargin = margin16dp;
            binding.cardCheckpoints.setLayoutParams(cpParams);
            
            // 更新按钮文本为"地图"
            binding.btnManualAddCheckpoint.setText("地图");
            binding.btnManualAddCheckpoint.setIcon(null);
        }
    }
    
}


