package com.example.cross_intelligence.mvc.view.race;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.model.LatLng;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.DialogCheckpointBinding;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/**
 * 打卡点设置底部滑动面板
 */
public class CheckpointBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnCheckpointConfirmedListener {
        void onCheckpointConfirmed(String name, double lat, double lng, String type, double radius);
    }

    private DialogCheckpointBinding binding;
    private OnCheckpointConfirmedListener listener;
    private LatLng latLng;
    private String recommendedType; // 推荐的打卡点类型

    // 打卡点类型常量
    private static final String TYPE_START = CheckPoint.TYPE_START;
    private static final String TYPE_CHECKPOINT = CheckPoint.TYPE_CHECKPOINT;
    private static final String TYPE_END = CheckPoint.TYPE_FINISH;
    private static final double DEFAULT_CHECK_RADIUS = 50.0;
    private static final double DEFAULT_FINISH_RADIUS = 10.0; // 终点默认半径10米

    public static CheckpointBottomSheetDialogFragment newInstance(@Nullable LatLng latLng) {
        CheckpointBottomSheetDialogFragment fragment = new CheckpointBottomSheetDialogFragment();
        Bundle args = new Bundle();
        if (latLng != null) {
            args.putDouble("latitude", latLng.latitude);
            args.putDouble("longitude", latLng.longitude);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnCheckpointConfirmedListener(OnCheckpointConfirmedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogCheckpointBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 获取传入的坐标
        Bundle args = getArguments();
        if (args != null && args.containsKey("latitude") && args.containsKey("longitude")) {
            double lat = args.getDouble("latitude");
            double lng = args.getDouble("longitude");
            latLng = new LatLng(lat, lng);
            
            // 自动填充经纬度输入框
            binding.etLatitude.setText(String.valueOf(lat));
            binding.etLongitude.setText(String.valueOf(lng));
        }


        // 设置确定按钮
        binding.btnConfirm.setOnClickListener(v -> confirmCheckpoint());
        
        // 设置类型选择 Chip 的点击事件
        setupTypeChips();
        
        // 如果有推荐类型，设置它
        if (recommendedType != null) {
            setRecommendedType(recommendedType);
        }
    }

    private void setupTypeChips() {
        // 默认选择"起点"
        binding.chipStart.setChecked(true);
        updateChipTextColors();

        // 设置 Chip 点击监听，动态更新文字颜色
        binding.chipGroupType.setOnCheckedChangeListener((group, checkedId) -> {
            updateChipTextColors();
        });
    }

    /**
     * 更新所有 Chip 的文字颜色
     * 选中：白色文字，未选中：橙色文字
     */
    private void updateChipTextColors() {
        if (binding == null || getContext() == null) return;
        
        int orangeColor = getContext().getColor(R.color.trail_orange);
        int whiteColor = getContext().getColor(android.R.color.white);
        
        binding.chipStart.setTextColor(binding.chipStart.isChecked() ? whiteColor : orangeColor);
        binding.chipCheckpoint.setTextColor(binding.chipCheckpoint.isChecked() ? whiteColor : orangeColor);
        binding.chipEnd.setTextColor(binding.chipEnd.isChecked() ? whiteColor : orangeColor);
    }

    /**
     * 设置推荐的打卡点类型（从 Activity 传入）
     */
    public void setRecommendedType(String type) {
        this.recommendedType = type;
        if (binding != null) {
            if (TYPE_START.equals(type)) {
                binding.chipStart.setChecked(true);
            } else if (TYPE_END.equals(type)) {
                binding.chipEnd.setChecked(true);
            } else {
                binding.chipCheckpoint.setChecked(true);
            }
            updateChipTextColors();
        }
    }


    private void confirmCheckpoint() {
        String name = binding.etName.getText() != null ? 
            binding.etName.getText().toString().trim() : "";
        String latStr = binding.etLatitude.getText() != null ? 
            binding.etLatitude.getText().toString().trim() : "";
        String lngStr = binding.etLongitude.getText() != null ? 
            binding.etLongitude.getText().toString().trim() : "";
        String radiusStr = binding.etCheckRadius.getText() != null ? 
            binding.etCheckRadius.getText().toString().trim() : "";

        // 获取选中的类型
        String type = getSelectedType();
        if (TextUtils.isEmpty(type)) {
            Toast.makeText(getContext(), "请选择打卡点类型", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(getContext(), "请输入打卡点名称", Toast.LENGTH_SHORT).show();
            return;
        }

        // 根据类型设置默认半径：终点10米，其他50米
        double defaultRadius = TYPE_END.equals(type) ? DEFAULT_FINISH_RADIUS : DEFAULT_CHECK_RADIUS;
        double radius = defaultRadius;
        if (!TextUtils.isEmpty(radiusStr)) {
            try {
                radius = Double.parseDouble(radiusStr);
                if (radius <= 0) {
                    Toast.makeText(getContext(), "打卡半径必须大于0", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                String defaultMsg = TYPE_END.equals(type) ? "打卡半径格式错误，使用默认值10米" : "打卡半径格式错误，使用默认值50米";
                Toast.makeText(getContext(), defaultMsg, Toast.LENGTH_SHORT).show();
                radius = defaultRadius;
            }
        } else {
            // 如果未输入半径，使用默认值
            radius = defaultRadius;
        }

        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            
            if (listener != null) {
                listener.onCheckpointConfirmed(name, lat, lng, type, radius);
            }
            dismiss();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "坐标格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取选中的打卡点类型
     */
    private String getSelectedType() {
        if (binding == null) {
            return TYPE_CHECKPOINT;
        }
        int checkedId = binding.chipGroupType.getCheckedChipId();
        if (checkedId == binding.chipStart.getId()) {
            return TYPE_START;
        } else if (checkedId == binding.chipCheckpoint.getId()) {
            return TYPE_CHECKPOINT;
        } else if (checkedId == binding.chipEnd.getId()) {
            return TYPE_END;
        }
        return TYPE_CHECKPOINT; // 默认返回检查点
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}

