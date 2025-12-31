package com.example.cross_intelligence.mvc.view.result;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cross_intelligence.databinding.ActivityMyResultsBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.CheckInManager;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.controller.TrackManager;
import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.TrackPoint;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的成绩页面 - 显示当前用户的所有比赛成绩
 * 分为两个板块：已完成成绩和异常成绩（未完成）
 */
public class MyResultsActivity extends BaseActivity implements MyResultAdapter.OnResultClickListener {

    private ActivityMyResultsBinding binding;
    private final List<Result> completedResults = new ArrayList<>();
    private final List<Result> abnormalResults = new ArrayList<>();
    private MyResultAdapter completedAdapter;
    private MyResultAdapter abnormalAdapter;
    private ResultManager resultManager;
    private RaceManager raceManager;
    private TrackManager trackManager;
    private CheckInManager checkInManager;
    private String currentUserId;
    private int currentTab = 0; // 0: 已完成, 1: 未完成

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
        
        binding = ActivityMyResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置 Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // 设置返回按钮点击事件
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        raceManager = new RaceManager();
        
        // 设置已完成成绩列表
        completedAdapter = new MyResultAdapter(completedResults, this);
        binding.rvCompletedResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCompletedResults.setAdapter(completedAdapter);
        
        // 设置异常成绩列表
        abnormalAdapter = new MyResultAdapter(abnormalResults, this);
        binding.rvAbnormalResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAbnormalResults.setAdapter(abnormalAdapter);
        
        // 设置Tab切换
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("已完成"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("未完成"));
        
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateTabVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        
        // 刷新按钮
        binding.btnRefresh.setOnClickListener(v -> loadMyResults());
        
        // 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(getResources().getColor(com.example.cross_intelligence.R.color.forest_green, getTheme()));
        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadMyResults();
            binding.swipeRefresh.setRefreshing(false);
        });
        
        binding.progressMyResults.setVisibility(View.VISIBLE);
    }

    @Override
    protected void initData() {
        resultManager = new ResultManager();
        trackManager = new TrackManager();
        checkInManager = new CheckInManager();
        
        // 获取当前用户ID
        currentUserId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(currentUserId)) {
            UIUtil.showToast(this, "请先登录");
            finish();
            return;
        }
        
        loadMyResults();
    }

    /**
     * 加载我的成绩列表
     * 将成绩分为两个板块：已完成成绩和异常成绩（未完成）
     */
    private void loadMyResults() {
        binding.progressMyResults.setVisibility(View.VISIBLE);
        
        // 清空现有数据
        completedResults.clear();
        abnormalResults.clear();
        
        // 查询该用户的所有成绩
        List<Result> results = resultManager.loadResultsByUserId(currentUserId);
        
        // 确保所有已结束的赛事都为未完成选手创建了空成绩记录
        // 获取用户已报名的所有赛事ID，检查并创建未完成的成绩记录
        com.example.cross_intelligence.mvc.controller.RaceSignupController signupController = 
                new com.example.cross_intelligence.mvc.controller.RaceSignupController();
        List<String> signedUpRaceIds = signupController.getUserSignedUpRaceIds(currentUserId);
        for (String raceId : signedUpRaceIds) {
            resultManager.ensureUnfinishedResultsCreated(raceId);
            // 为每个赛事重新计算排名（只在赛事未结束时更新排名）
            List<Result> raceResults = resultManager.loadResults(raceId);
            resultManager.rankResults(raceResults, raceId);
        }
        signupController.close();
        
        // 重新查询成绩（可能刚刚创建了新的空成绩记录，且排名已更新）
        results = resultManager.loadResultsByUserId(currentUserId);
        
        // 分类成绩：已完成和异常成绩（DNF）
        for (Result result : results) {
            if (result.getStatus() == Result.Status.DNF) {
                // 异常成绩：未完成
                abnormalResults.add(result);
            } else {
                // 已完成成绩：FINISHED 或 FINISHED_WITH_PENALTY
                completedResults.add(result);
            }
        }
        
        // 通知适配器数据已更新
        completedAdapter.notifyDataSetChanged();
        abnormalAdapter.notifyDataSetChanged();
        
        // 更新UI（统计信息和Tab显示）
        updateUI();
        
        binding.progressMyResults.setVisibility(View.GONE);
        
        int totalCount = completedResults.size() + abnormalResults.size();
        if (totalCount > 0) {
            UIUtil.showToast(this, "已加载 " + totalCount + " 条成绩（已完成：" + completedResults.size() + "，未完成：" + abnormalResults.size() + "）");
        } else {
            UIUtil.showToast(this, "暂无成绩记录");
        }
    }
    
    /**
     * 更新UI显示
     * 始终显示两个板块，如果没有成绩则显示空状态提示
     */
    private void updateUI() {
        // 更新统计信息
        updateStatistics();
        
        // 更新Tab显示
        updateTabVisibility();
    }
    
    /**
     * 更新统计信息（累计里程、完赛场次）
     */
    private void updateStatistics() {
        int completedCount = completedResults.size();
        binding.tvCompletedCount.setText(String.valueOf(completedCount));
        
        // 计算累计里程：累加所有已完成赛事的真实里程
        double totalDistanceKm = 0.0;
        for (Result result : completedResults) {
            double raceDistance = calculateRaceDistance(result);
            totalDistanceKm += raceDistance;
        }
        // 累计里程精确到小数点后三位
        binding.tvTotalDistance.setText(String.format("%.3f", totalDistanceKm));
    }
    
    /**
     * 计算单个赛事的真实里程
     * 优先使用轨迹数据，如果没有轨迹数据则使用打卡记录计算
     */
    private double calculateRaceDistance(Result result) {
        String raceId = result.getRaceId();
        String userId = result.getUserId();
        
        // 1. 优先使用轨迹数据计算
        List<TrackPoint> trackPoints = trackManager.queryTrack(raceId, userId);
        if (trackPoints != null && !trackPoints.isEmpty()) {
            double distance = calculateTotalDistanceFromTrack(trackPoints);
            if (distance > 0) {
                return distance / 1000.0; // 转换为公里
            }
        }
        
        // 2. 如果没有轨迹数据，尝试从打卡记录计算
        List<CheckInRecord> checkInRecords = checkInManager.queryCheckInRecords(raceId, userId);
        if (checkInRecords != null && checkInRecords.size() > 1) {
            double distance = calculateDistanceFromCheckInRecords(checkInRecords);
            if (distance > 0) {
                return distance / 1000.0; // 转换为公里
            }
        }
        
        // 3. 如果都没有，尝试从赛事的检查点计算（作为最后备选）
        Race race = raceManager.getRaceById(raceId);
        if (race != null && race.getCheckPoints() != null && race.getCheckPoints().size() > 1) {
            double distance = calculateDistanceFromCheckPoints(race.getCheckPoints());
            if (distance > 0) {
                return distance / 1000.0; // 转换为公里
            }
        }
        
        return 0.0;
    }
    
    /**
     * 从轨迹点计算总距离
     */
    private double calculateTotalDistanceFromTrack(List<TrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.size() < 2) {
            return 0.0;
        }
        
        // 按时间排序
        List<TrackPoint> sortedPoints = new ArrayList<>(trackPoints);
        sortedPoints.sort((p1, p2) -> {
            if (p1.getTimestamp() == null && p2.getTimestamp() == null) return 0;
            if (p1.getTimestamp() == null) return 1;
            if (p2.getTimestamp() == null) return -1;
            return p1.getTimestamp().compareTo(p2.getTimestamp());
        });
        
        double totalDistance = 0.0;
        TrackPoint lastValidPoint = null;
        
        for (TrackPoint curr : sortedPoints) {
            // 跳过无效的点
            if (curr.getLatitude() == 0.0 && curr.getLongitude() == 0.0) {
                continue;
            }
            if (Math.abs(curr.getLatitude()) > 90 || Math.abs(curr.getLongitude()) > 180) {
                continue;
            }
            
            if (lastValidPoint != null) {
                double distance = DistanceUtil.distanceMeters(
                        lastValidPoint.getLatitude(), lastValidPoint.getLongitude(),
                        curr.getLatitude(), curr.getLongitude()
                );
                
                // 计算时间差（秒）
                long timeDiff = 0;
                if (lastValidPoint.getTimestamp() != null && curr.getTimestamp() != null) {
                    timeDiff = (curr.getTimestamp().getTime() - lastValidPoint.getTimestamp().getTime()) / 1000;
                }
                
                if (distance <= 0) {
                    continue;
                }
                
                // 如果时间差为0或负数，但距离很小，可能是GPS精度问题，计入
                if (timeDiff <= 0) {
                    if (distance < 50) {
                        totalDistance += distance;
                        lastValidPoint = curr;
                    }
                    continue;
                }
                
                // 计算速度（公里/小时）
                double speed = distance / timeDiff;
                double speedKmh = speed * 3.6;
                
                // 过滤异常值：速度过快或距离过大可能是GPS漂移
                boolean isAbnormal = false;
                if (distance >= 50) {
                    if (speedKmh > 40.0 || distance > 3000) {
                        isAbnormal = true;
                    }
                } else {
                    if (speedKmh > 60.0) {
                        isAbnormal = true;
                    }
                }
                
                if (!isAbnormal) {
                    totalDistance += distance;
                    lastValidPoint = curr;
                } else if (distance < 30) {
                    // 异常但距离很小，仍然计入
                    totalDistance += distance;
                    lastValidPoint = curr;
                }
            } else {
                lastValidPoint = curr;
            }
        }
        
        return totalDistance;
    }
    
    /**
     * 从打卡记录计算距离
     */
    private double calculateDistanceFromCheckInRecords(List<CheckInRecord> checkInRecords) {
        if (checkInRecords == null || checkInRecords.size() < 2) {
            return 0.0;
        }
        
        // 按时间排序
        List<CheckInRecord> sortedRecords = new ArrayList<>(checkInRecords);
        sortedRecords.sort((r1, r2) -> {
            if (r1.getTimestamp() == null && r2.getTimestamp() == null) return 0;
            if (r1.getTimestamp() == null) return 1;
            if (r2.getTimestamp() == null) return -1;
            return r1.getTimestamp().compareTo(r2.getTimestamp());
        });
        
        double totalDistance = 0.0;
        for (int i = 1; i < sortedRecords.size(); i++) {
            CheckInRecord prev = sortedRecords.get(i - 1);
            CheckInRecord curr = sortedRecords.get(i);
            double distance = DistanceUtil.distanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
            totalDistance += distance;
        }
        
        return totalDistance;
    }
    
    /**
     * 从检查点计算距离（备选方案）
     */
    private double calculateDistanceFromCheckPoints(List<com.example.cross_intelligence.mvc.model.CheckPoint> checkPoints) {
        if (checkPoints == null || checkPoints.size() < 2) {
            return 0.0;
        }
        
        // 按顺序排序
        List<com.example.cross_intelligence.mvc.model.CheckPoint> sortedPoints = new ArrayList<>(checkPoints);
        sortedPoints.sort((p1, p2) -> Integer.compare(p1.getOrderIndex(), p2.getOrderIndex()));
        
        double totalDistance = 0.0;
        for (int i = 1; i < sortedPoints.size(); i++) {
            com.example.cross_intelligence.mvc.model.CheckPoint prev = sortedPoints.get(i - 1);
            com.example.cross_intelligence.mvc.model.CheckPoint curr = sortedPoints.get(i);
            double distance = DistanceUtil.distanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
            totalDistance += distance;
        }
        
        return totalDistance;
    }
    
    /**
     * 根据当前Tab更新显示内容
     */
    private void updateTabVisibility() {
        int totalCount = completedResults.size() + abnormalResults.size();
        
        // 显示或隐藏空状态页面
        if (totalCount == 0) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
        }
        
        if (currentTab == 0) {
            // 显示已完成
            if (completedResults.isEmpty()) {
                binding.rvCompletedResults.setVisibility(View.GONE);
                // 只有在完全没有比赛记录时才显示"暂无比赛记录"
                if (abnormalResults.isEmpty()) {
                    binding.layoutEmptyState.setVisibility(View.VISIBLE);
                } else {
                    binding.layoutEmptyState.setVisibility(View.GONE);
                }
                binding.tvCompletedEmpty.setVisibility(View.GONE);
            } else {
                binding.rvCompletedResults.setVisibility(View.VISIBLE);
                binding.tvCompletedEmpty.setVisibility(View.GONE);
                binding.layoutEmptyState.setVisibility(View.GONE);
            }
            binding.rvAbnormalResults.setVisibility(View.GONE);
            binding.tvAbnormalEmpty.setVisibility(View.GONE);
        } else {
            // 显示未完成
            if (abnormalResults.isEmpty()) {
                binding.rvAbnormalResults.setVisibility(View.GONE);
                binding.tvAbnormalEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvAbnormalResults.setVisibility(View.VISIBLE);
                binding.tvAbnormalEmpty.setVisibility(View.GONE);
            }
            binding.rvCompletedResults.setVisibility(View.GONE);
            binding.tvCompletedEmpty.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResultClick(Result result) {
        // 点击成绩项，跳转到详情页面
        Intent intent = new Intent(this, ResultDetailActivity.class);
        intent.putExtra(ResultDetailActivity.EXTRA_RESULT_ID, result.getResultId());
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}


