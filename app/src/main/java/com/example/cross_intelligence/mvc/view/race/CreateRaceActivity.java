package com.example.cross_intelligence.mvc.view.race;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.example.cross_intelligence.databinding.ActivityCreateRaceBinding;
import com.example.cross_intelligence.databinding.DialogCheckpointBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.location.MapLocationManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.admin.AdminMainActivity;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 赛事创建页面：集成高德地图选择打卡点，并完成数据校验与保存。
 */
public class CreateRaceActivity extends BaseActivity implements 
        AMap.OnMapClickListener, 
        MapLocationManager.LocationCallback {

    private static final int MAX_CHECKPOINTS = 40;
    private static final double DUPLICATE_THRESHOLD = 0.00005;
    private static final String TYPE_START = "起点";
    private static final String TYPE_CHECKPOINT = "检查点";
    private static final String TYPE_END = "终点";
    private static final double DEFAULT_CHECK_RADIUS = 50.0; // 默认打卡半径50米

    private ActivityCreateRaceBinding binding;
    private MapView mapView;
    private AMap aMap;
    private final RaceManager raceManager = new RaceManager();
    private final List<CheckPoint> checkPoints = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();
    private CheckpointAdapter adapter;
    private Polyline routePolyline; // 路线预览折线
    private final SimpleDateFormat dateTimeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private final Map<String, LatLng> presetCities = new HashMap<>();
    private String editingRaceId; // 编辑模式下的赛事ID，为 null 表示创建模式
    private MapLocationManager locationManager; // 定位管理器
    private boolean isFirstLocation = true; // 标记是否首次定位
    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateRaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapView = binding.mapView;
        mapView.onCreate(savedInstanceState);
        
        // 检查是否为编辑模式
        editingRaceId = getIntent().getStringExtra("raceId");
        
        initView();
        initData();
        
        // 初始化定位管理器
        locationManager = new MapLocationManager(this, this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initView() {
        adapter = new CheckpointAdapter(checkPoints, position -> {
            removeMarker(position);
            checkPoints.remove(position);
            reindexCheckpoints();
            adapter.notifyItemRemoved(position);
            updateRoutePreview(); // 更新路线预览
            autoSaveDraft(); // 自动保存草稿
        });
        binding.rvCheckpoints.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpoints.setAdapter(adapter);

        binding.btnAddManual.setOnClickListener(v -> showCheckpointDialog(null));
        binding.btnSaveRace.setOnClickListener(v -> saveRace());
        
        // 根据模式设置按钮文本和标题
        if (editingRaceId != null) {
            binding.btnSaveRace.setText("保存修改");
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("编辑赛事");
            }
        }

        binding.etStartTime.setOnClickListener(v -> pickDateTime(binding.etStartTime));
        binding.etEndTime.setOnClickListener(v -> pickDateTime(binding.etEndTime));

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        // 添加文本变化监听，实现自动保存
        addTextWatcher(binding.etRaceName);
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
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return false;
        });
    }

    private void performSearch() {
        String keyword = binding.etSearch.getText() != null
                ? binding.etSearch.getText().toString().trim() : "";
        if (TextUtils.isEmpty(keyword)) {
            UIUtil.showToast(this, "请输入要搜索的城市或地点");
            return;
        }
        String lower = keyword.toLowerCase(Locale.CHINA);
        LatLng target = null;
        if (presetCities.containsKey(keyword)) {
            target = presetCities.get(keyword);
        } else if (presetCities.containsKey(lower)) {
            target = presetCities.get(lower);
        } else {
            for (Map.Entry<String, LatLng> entry : presetCities.entrySet()) {
                if (keyword.contains(entry.getKey())) {
                    target = entry.getValue();
                    break;
                }
            }
        }
        if (target == null) {
            UIUtil.showToast(this, "暂不支持该地点搜索，请尝试：昆明/大理/丽江/玉溪/曲靖/保山/昭通/普洱/临沧/楚雄/蒙自/文山/景洪/芒市/泸水/香格里拉");
            return;
        }
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14f));
    }

    @Override
    protected void initData() {
        // 如果是编辑模式，加载现有赛事数据
        if (editingRaceId != null) {
            loadRaceData(editingRaceId);
        } else {
            // 创建模式：预填充一个示例时间，方便直接调整
            Calendar calendar = Calendar.getInstance();
            calendar.set(2025, Calendar.JANUARY, 1, 9, 0);
            binding.etStartTime.setText(dateTimeFormat.format(calendar.getTime()));
            calendar.set(2025, Calendar.JANUARY, 1, 11, 0);
            binding.etEndTime.setText(dateTimeFormat.format(calendar.getTime()));
        }

        // 初始化云南省内主要城市和区县中心点，用于快速跳转
        presetCities.clear();
        // 昆明市
        presetCities.put("昆明", new LatLng(25.0389, 102.7183));
        presetCities.put("昆明市", new LatLng(25.0389, 102.7183));
        presetCities.put("kunming", new LatLng(25.0389, 102.7183));
        // 大理市
        presetCities.put("大理", new LatLng(25.6065, 100.2676));
        presetCities.put("大理市", new LatLng(25.6065, 100.2676));
        presetCities.put("dali", new LatLng(25.6065, 100.2676));
        // 丽江市
        presetCities.put("丽江", new LatLng(26.8550, 100.2277));
        presetCities.put("丽江市", new LatLng(26.8550, 100.2277));
        presetCities.put("lijiang", new LatLng(26.8550, 100.2277));
        // 玉溪市
        presetCities.put("玉溪", new LatLng(24.3473, 102.5439));
        presetCities.put("玉溪市", new LatLng(24.3473, 102.5439));
        presetCities.put("yuxi", new LatLng(24.3473, 102.5439));
        // 曲靖市
        presetCities.put("曲靖", new LatLng(25.4899, 103.7962));
        presetCities.put("曲靖市", new LatLng(25.4899, 103.7962));
        presetCities.put("qujing", new LatLng(25.4899, 103.7962));
        // 保山市
        presetCities.put("保山", new LatLng(25.1118, 99.1618));
        presetCities.put("保山市", new LatLng(25.1118, 99.1618));
        presetCities.put("baoshan", new LatLng(25.1118, 99.1618));
        // 昭通市
        presetCities.put("昭通", new LatLng(27.3382, 103.7175));
        presetCities.put("昭通市", new LatLng(27.3382, 103.7175));
        presetCities.put("zhaotong", new LatLng(27.3382, 103.7175));
        // 普洱市
        presetCities.put("普洱", new LatLng(22.7873, 100.9786));
        presetCities.put("普洱市", new LatLng(22.7873, 100.9786));
        presetCities.put("puer", new LatLng(22.7873, 100.9786));
        // 临沧市
        presetCities.put("临沧", new LatLng(23.8772, 100.0878));
        presetCities.put("临沧市", new LatLng(23.8772, 100.0878));
        presetCities.put("lincang", new LatLng(23.8772, 100.0878));
        // 楚雄市
        presetCities.put("楚雄", new LatLng(25.0320, 101.5460));
        presetCities.put("楚雄市", new LatLng(25.0320, 101.5460));
        presetCities.put("chuxiong", new LatLng(25.0320, 101.5460));
        // 红河州（蒙自市）
        presetCities.put("蒙自", new LatLng(23.3631, 103.3849));
        presetCities.put("蒙自市", new LatLng(23.3631, 103.3849));
        presetCities.put("mengzi", new LatLng(23.3631, 103.3849));
        // 文山州（文山市）
        presetCities.put("文山", new LatLng(23.3690, 104.2443));
        presetCities.put("文山市", new LatLng(23.3690, 104.2443));
        presetCities.put("wenshan", new LatLng(23.3690, 104.2443));
        // 西双版纳（景洪市）
        presetCities.put("景洪", new LatLng(22.0094, 100.7979));
        presetCities.put("景洪市", new LatLng(22.0094, 100.7979));
        presetCities.put("jinghong", new LatLng(22.0094, 100.7979));
        presetCities.put("西双版纳", new LatLng(22.0094, 100.7979));
        // 德宏州（芒市）
        presetCities.put("芒市", new LatLng(24.4337, 98.5856));
        presetCities.put("mangshi", new LatLng(24.4337, 98.5856));
        // 怒江州（泸水市）
        presetCities.put("泸水", new LatLng(25.8516, 98.8543));
        presetCities.put("泸水市", new LatLng(25.8516, 98.8543));
        presetCities.put("lushui", new LatLng(25.8516, 98.8543));
        // 迪庆州（香格里拉市）
        presetCities.put("香格里拉", new LatLng(27.8297, 99.7026));
        presetCities.put("香格里拉市", new LatLng(27.8297, 99.7026));
        presetCities.put("xianggelila", new LatLng(27.8297, 99.7026));
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
            
            // 地图标记将在 initMap() 中绘制
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

        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                target.setText(dateTimeFormat.format(calendar.getTime()));
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true);
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void initMap() {
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setZoomControlsEnabled(false);
            aMap.moveCamera(CameraUpdateFactory.zoomTo(16f));
            aMap.setOnMapClickListener(this);
            
            // 如果是编辑模式，地图初始化后需要重新加载打卡点（因为地图可能还未准备好）
            if (editingRaceId != null && !checkPoints.isEmpty()) {
                // 地图已初始化，重新绘制标记和路线
                for (CheckPoint point : checkPoints) {
                    Marker marker = aMap.addMarker(new MarkerOptions()
                            .position(new LatLng(point.getLatitude(), point.getLongitude()))
                            .title(point.getName() + (point.getType() != null ? "(" + point.getType() + ")" : "")));
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
        UIUtil.showToast(this, getString(com.example.cross_intelligence.R.string.map_click_format,
                latLng.latitude, latLng.longitude));
        showCheckpointDialog(latLng);
    }

    private void showCheckpointDialog(@Nullable LatLng latLng) {
        DialogCheckpointBinding dialogBinding = DialogCheckpointBinding.inflate(LayoutInflater.from(this));
        if (latLng != null) {
            dialogBinding.etLatitude.setText(String.valueOf(latLng.latitude));
            dialogBinding.etLongitude.setText(String.valueOf(latLng.longitude));
        }
        
        // 设置打卡点类型下拉选择
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_dropdown_item_1line,
                new String[]{TYPE_START, TYPE_CHECKPOINT, TYPE_END});
        dialogBinding.actType.setAdapter(typeAdapter);
        dialogBinding.actType.setOnClickListener(v -> dialogBinding.actType.showDropDown());
        dialogBinding.actType.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                dialogBinding.actType.showDropDown();
            }
        });
        // 如果没有打卡点，默认为起点；否则默认为检查点
        if (checkPoints.isEmpty()) {
            dialogBinding.actType.setText(TYPE_START, false);
        } else {
            dialogBinding.actType.setText(TYPE_CHECKPOINT, false);
        }
        
        new AlertDialog.Builder(this)
                .setTitle("新增打卡点")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("确定", (dialog, which) -> {
                    String name = dialogBinding.etName.getText() != null ? dialogBinding.etName.getText().toString().trim() : "";
                    String latStr = dialogBinding.etLatitude.getText() != null ? dialogBinding.etLatitude.getText().toString().trim() : "";
                    String lngStr = dialogBinding.etLongitude.getText() != null ? dialogBinding.etLongitude.getText().toString().trim() : "";
                    String type = dialogBinding.actType.getText() != null ? dialogBinding.actType.getText().toString().trim() : TYPE_CHECKPOINT;
                    String radiusStr = dialogBinding.etCheckRadius.getText() != null ? dialogBinding.etCheckRadius.getText().toString().trim() : "";
                    
                    if (TextUtils.isEmpty(name)) {
                        UIUtil.showToast(this, "请输入打卡点名称");
                        return;
                    }
                    if (TextUtils.isEmpty(type) || (!TYPE_START.equals(type) && !TYPE_CHECKPOINT.equals(type) && !TYPE_END.equals(type))) {
                        UIUtil.showToast(this, "请选择打卡点类型");
                        return;
                    }
                    double radius = DEFAULT_CHECK_RADIUS;
                    if (!TextUtils.isEmpty(radiusStr)) {
                        try {
                            radius = Double.parseDouble(radiusStr);
                            if (radius <= 0) {
                                UIUtil.showToast(this, "打卡半径必须大于0");
                                return;
                            }
                        } catch (NumberFormatException e) {
                            UIUtil.showToast(this, "打卡半径格式错误，使用默认值50米");
                            radius = DEFAULT_CHECK_RADIUS;
                        }
                    }
                    try {
                        double lat = Double.parseDouble(latStr);
                        double lng = Double.parseDouble(lngStr);
                        addCheckpoint(name, lat, lng, type, radius);
                    } catch (NumberFormatException e) {
                        UIUtil.showToast(this, "坐标格式错误");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
        point.setCheckPointId(UUID.randomUUID().toString());
        point.setName(name);
        point.setLatitude(lat);
        point.setLongitude(lng);
        point.setType(type);
        point.setCheckRadius(radius);
        
        // 起点设置为1，终点设置为最后，检查点按添加顺序插入到终点之前
        if (TYPE_START.equals(type)) {
            point.setOrderIndex(1);
            // 将其他点的顺序后移
            for (CheckPoint p : checkPoints) {
                p.setOrderIndex(p.getOrderIndex() + 1);
            }
            checkPoints.add(0, point);
            adapter.notifyItemInserted(0);
        } else if (TYPE_END.equals(type)) {
            point.setOrderIndex(checkPoints.size() + 1);
            checkPoints.add(point);
            adapter.notifyItemInserted(checkPoints.size() - 1);
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
        }

        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .title(name + "(" + type + ")"));
        markers.add(marker);
        
        updateRoutePreview(); // 更新路线预览
        autoSaveDraft(); // 自动保存草稿
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

        // 绘制路线
        if (!routePoints.isEmpty()) {
            routePolyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(routePoints)
                    .width(6f)
                    .color(0xFF4CAF50) // 绿色路线
                    .setDottedLine(false));
            
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
        String name = textOf(binding.etRaceName);
        String description = textOf(binding.etDescription);
        String start = textOf(binding.etStartTime);
        String end = textOf(binding.etEndTime);

        RaceFormValidator.ValidationResult result =
                RaceFormValidator.validate(name, start, end, checkPoints);
        if (!result.isValid()) {
            String message = result.getMessage();
            if ("请输入赛事名称".equals(message)) {
                binding.tilRaceName.setError(message);
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
            UIUtil.showToast(this, "无法获取管理员账号信息，请重新登录");
            return;
        }
        
        // 显示保存中的提示
        UIUtil.showToast(this, "正在保存...");
        
        // 保存或更新赛事（使用异步回调）
        RaceManager.SaveCallback callback = new RaceManager.SaveCallback() {
            @Override
            public void onSuccess() {
                // 在主线程更新UI
                runOnUiThread(() -> {
                    clearDraft(); // 清除草稿
                    
                    // 显示成功弹窗
                    String message = editingRaceId != null ? "赛事更新成功！" : "赛事保存成功！";
                    new AlertDialog.Builder(CreateRaceActivity.this)
                            .setTitle("保存成功")
                            .setMessage(message)
                            .setPositiveButton("确定", (dialog, which) -> {
                                // 返回管理员主页
                                Intent intent = new Intent(CreateRaceActivity.this, AdminMainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
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

        // 提取 CheckPoint 数据（在 UI 线程提取所有属性，避免传递 Realm 对象引用）
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
            checkpointDataList.add(data);
        }
        
        if (editingRaceId != null) {
            // 编辑模式：更新现有赛事
            raceManager.updateRace(editingRaceId, name, description, result.getStart(), result.getEnd(), checkpointDataList, callback);
        } else {
            // 创建模式：创建新赛事
            raceManager.createRace(name, description, result.getStart(), result.getEnd(), checkpointDataList, organizerId, callback);
        }
    }

    private void reindexCheckpoints() {
        for (int i = 0; i < checkPoints.size(); i++) {
            checkPoints.get(i).setOrderIndex(i + 1);
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

}


