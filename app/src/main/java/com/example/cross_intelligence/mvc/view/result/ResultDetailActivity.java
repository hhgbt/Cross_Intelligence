package com.example.cross_intelligence.mvc.view.result;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityResultDetailBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.controller.TrackManager;
import com.example.cross_intelligence.mvc.location.RaceMapController;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.TrackPoint;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.ResultExportUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ResultDetailActivity extends BaseActivity {

    public static final String EXTRA_RESULT_ID = "extra_result_id";

    private ActivityResultDetailBinding binding;
    private Result currentResult;
    private RaceMapController mapController;  // 【新增】地图控制器
    private TrackManager trackManager;        // 【新增】轨迹管理器

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 【新增】初始化地图
        mapController = new RaceMapController(binding.mapViewTrack);
        mapController.onCreate(savedInstanceState);
        
        initView();
        initData();
    }

    @Override
    protected void initView() {
        binding.btnShare.setOnClickListener(v -> shareResult());
        binding.btnExportSingle.setOnClickListener(v -> exportResult());
    }

    @Override
    protected void initData() {
        ResultManager resultManager = new ResultManager();
        trackManager = new TrackManager();  // 【新增】初始化轨迹管理器
        
        String resultId = getIntent().getStringExtra(EXTRA_RESULT_ID);
        if (TextUtils.isEmpty(resultId)) {
            UIUtil.showToast(this, "缺少成绩ID");
            finish();
            return;
        }
        currentResult = resultManager.loadResultById(resultId);
        if (currentResult == null) {
            UIUtil.showToast(this, "成绩不存在");
            finish();
            return;
        }
        renderResult(currentResult);
        
        // 【新增】加载并显示轨迹
        loadAndDisplayTrack();
    }
    
    /**
     * 【新增】加载并显示轨迹
     */
    private void loadAndDisplayTrack() {
        if (currentResult == null) {
            return;
        }
        
        String raceId = currentResult.getRaceId();
        String userId = currentResult.getUserId();
        
        // 查询轨迹数据
        List<TrackPoint> trackPoints = trackManager.queryTrack(raceId, userId);
        
        if (trackPoints == null || trackPoints.isEmpty()) {
            // 没有轨迹数据
            binding.tvTrackStats.setText("暂无轨迹数据");
            binding.mapViewTrack.setVisibility(View.GONE);
            binding.tvTrackTitle.setVisibility(View.GONE);
            UIUtil.showToast(this, "该场比赛没有记录轨迹");
            return;
        }
        
        // 显示轨迹统计信息
        double totalDistance = calculateTotalDistance(trackPoints);
        binding.tvTrackStats.setText(String.format(
                "轨迹点数：%d 个  |  总距离：%.2f 公里",
                trackPoints.size(),
                totalDistance / 1000.0
        ));
        
        // 在地图上绘制轨迹
        for (TrackPoint point : trackPoints) {
            mapController.addTrackPoint(point.getLatitude(), point.getLongitude());
        }
        
        // 移动相机到第一个轨迹点
        if (!trackPoints.isEmpty()) {
            TrackPoint firstPoint = trackPoints.get(0);
            mapController.moveCamera(firstPoint.getLatitude(), firstPoint.getLongitude());
        }
        
        UIUtil.showToast(this, "已加载 " + trackPoints.size() + " 个轨迹点");
    }
    
    /**
     * 【新增】计算轨迹总距离
     */
    private double calculateTotalDistance(List<TrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        for (int i = 1; i < trackPoints.size(); i++) {
            TrackPoint prev = trackPoints.get(i - 1);
            TrackPoint curr = trackPoints.get(i);
            double distance = DistanceUtil.distanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
            totalDistance += distance;
        }
        
        return totalDistance;
    }

    private void renderResult(Result result) {
        binding.tvUser.setText(getString(R.string.result_user_format, result.getUserId()));
        binding.tvRank.setText(result.getRank() > 0
                ? getString(R.string.result_rank_full_format, result.getRank())
                : getString(R.string.result_rank_dnf));
        binding.tvElapsed.setText(getString(R.string.result_elapsed_format, result.getElapsedSeconds()));
        binding.tvPenalty.setText(getString(R.string.result_penalty_format, result.getPenaltySeconds()));
        binding.tvTotal.setText(getString(R.string.result_total_format, result.getTotalSeconds()));
        binding.tvStatus.setText(getString(R.string.result_status_format, result.getStatus()));
    }

    private void shareResult() {
        if (currentResult == null) return;
        String shareText = "选手 " + currentResult.getUserId() + " 用时 "
                + currentResult.getTotalSeconds() + " 秒，状态：" + currentResult.getStatus();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "分享成绩"));
    }

    private void exportResult() {
        if (currentResult == null) return;
        try {
            Uri uri = ResultExportUtil.exportToFile(this,
                    Collections.singletonList(currentResult),
                    "result_" + currentResult.getUserId() + ".csv");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出成绩"));
        } catch (IOException e) {
            UIUtil.showToast(this, "导出失败：" + e.getMessage());
        }
    }
    
    // 【新增】地图生命周期管理
    @Override
    protected void onResume() {
        super.onResume();
        if (mapController != null) {
            mapController.onResume();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mapController != null) {
            mapController.onPause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapController != null) {
            mapController.onDestroy();
        }
    }
    
    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapController != null) {
            mapController.onSaveInstanceState(outState);
        }
    }
}


