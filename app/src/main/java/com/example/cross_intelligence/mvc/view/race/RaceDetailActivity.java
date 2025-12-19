package com.example.cross_intelligence.mvc.view.race;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityRaceDetailBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.QrCodeGenerator;
import com.example.cross_intelligence.mvc.util.QrCodeUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.google.android.material.button.MaterialButton;

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
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private static final int REQUEST_WRITE_STORAGE = 1001;
    private Bitmap pendingSaveBitmap;
    private String pendingSaveFileName;

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRaceDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        raceManager = new RaceManager();
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置工具栏返回按钮
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 初始化打卡点列表（只读模式，不显示删除按钮）
        adapter = new CheckpointAdapter(checkPoints, null);
        adapter.setOnItemQrClickListener(this::showQrCodeDialog);
        binding.rvCheckpoints.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpoints.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        String raceId = getIntent().getStringExtra("raceId");
        if (raceId == null) {
            UIUtil.showToast(this, "赛事ID不存在");
            finish();
            return;
        }

        Race race = raceManager.getRaceById(raceId);
        if (race == null) {
            UIUtil.showToast(this, "赛事不存在");
            finish();
            return;
        }

        // 显示基本信息
        binding.tvRaceName.setText(race.getName());
        String description = race.getDescription();
        if (description != null && !description.isEmpty()) {
            binding.tvDescription.setText(description);
        } else {
            binding.tvDescription.setText("暂无描述");
        }

        // 显示时间范围
        String timeRange = formatTimeRange(race);
        binding.tvTimeRange.setText(timeRange);

        // 加载打卡点
        if (race.getCheckPoints() != null) {
            checkPoints.clear();
            checkPoints.addAll(race.getCheckPoints());
            // 按顺序排序
            checkPoints.sort(Comparator.comparingInt(CheckPoint::getOrderIndex));
            adapter.notifyDataSetChanged();
        }
    }

    private String formatTimeRange(Race race) {
        if (race.getStartTime() != null && race.getEndTime() != null) {
            return dateTimeFormat.format(race.getStartTime()) + " - " + dateTimeFormat.format(race.getEndTime());
        } else if (race.getStartTime() != null) {
            return dateTimeFormat.format(race.getStartTime()) + " - 未设置";
        } else {
            return "时间未设置";
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
}

