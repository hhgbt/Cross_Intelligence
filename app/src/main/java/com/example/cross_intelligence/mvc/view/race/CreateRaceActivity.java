package com.example.cross_intelligence.mvc.view.race;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.example.cross_intelligence.databinding.ActivityCreateRaceBinding;
import com.example.cross_intelligence.databinding.DialogCheckpointBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.util.UIUtil;
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
public class CreateRaceActivity extends BaseActivity implements AMap.OnMapClickListener {

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
    private final SimpleDateFormat dateTimeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private final Map<String, LatLng> presetCities = new HashMap<>();
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
        initView();
        initData();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initView() {
        adapter = new CheckpointAdapter(checkPoints, position -> {
            removeMarker(position);
            checkPoints.remove(position);
            reindexCheckpoints();
            adapter.notifyItemRemoved(position);
        });
        binding.rvCheckpoints.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpoints.setAdapter(adapter);

        binding.btnAddManual.setOnClickListener(v -> showCheckpointDialog(null));
        binding.btnSaveRace.setOnClickListener(v -> saveRace());

        binding.etStartTime.setOnClickListener(v -> pickDateTime(binding.etStartTime));
        binding.etEndTime.setOnClickListener(v -> pickDateTime(binding.etEndTime));

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
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
            UIUtil.showToast(this, "暂不支持该城市搜索，请尝试北京/上海/杭州/广州/深圳");
            return;
        }
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14f));
    }

    @Override
    protected void initData() {
        // 预填充一个示例时间，方便直接调整
        Calendar calendar = Calendar.getInstance();
        calendar.set(2025, Calendar.JANUARY, 1, 9, 0);
        binding.etStartTime.setText(dateTimeFormat.format(calendar.getTime()));
        calendar.set(2025, Calendar.JANUARY, 1, 11, 0);
        binding.etEndTime.setText(dateTimeFormat.format(calendar.getTime()));

        // 初始化常用城市中心点，用于快速跳转
        presetCities.clear();
        presetCities.put("北京", new LatLng(39.9042, 116.4074));
        presetCities.put("beijing", new LatLng(39.9042, 116.4074));
        presetCities.put("上海", new LatLng(31.2304, 121.4737));
        presetCities.put("shanghai", new LatLng(31.2304, 121.4737));
        presetCities.put("杭州", new LatLng(30.2741, 120.1551));
        presetCities.put("hangzhou", new LatLng(30.2741, 120.1551));
        presetCities.put("广州", new LatLng(23.1291, 113.2644));
        presetCities.put("guangzhou", new LatLng(23.1291, 113.2644));
        presetCities.put("深圳", new LatLng(22.5431, 114.0579));
        presetCities.put("shenzhen", new LatLng(22.5431, 114.0579));
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

        raceManager.createRace(name, description, result.getStart(), result.getEnd(), new ArrayList<>(checkPoints));
        UIUtil.showToast(this, "赛事已保存");
        finish();
    }

    private void reindexCheckpoints() {
        for (int i = 0; i < checkPoints.size(); i++) {
            checkPoints.get(i).setOrderIndex(i + 1);
        }
    }

    private String textOf(@NonNull TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

}


