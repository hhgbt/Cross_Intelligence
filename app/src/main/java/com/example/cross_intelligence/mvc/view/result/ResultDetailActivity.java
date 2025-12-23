package com.example.cross_intelligence.mvc.view.result;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityResultDetailBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.CheckInManager;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.controller.TrackManager;
import com.example.cross_intelligence.mvc.location.RaceMapController;
import com.example.cross_intelligence.mvc.model.CheckInRecord;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.TrackPoint;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.example.cross_intelligence.mvc.util.ResultExportUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ResultDetailActivity extends BaseActivity {

    public static final String EXTRA_RESULT_ID = "extra_result_id";
    public static final String EXTRA_IS_ADMIN_VIEW = "extra_is_admin_view";

    private ActivityResultDetailBinding binding;
    private Result currentResult;
    private RaceMapController mapController;
    private TrackManager trackManager;
    private RaceManager raceManager;
    private CheckInManager checkInManager;
    private RaceSignupController signupController;
    private int currentSequenceNumber = 0;
    private String currentRaceName = "";
    private boolean isAdminView = false;
    private List<TrackPoint> trackPoints = new ArrayList<>();

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        isAdminView = getIntent().getBooleanExtra(EXTRA_IS_ADMIN_VIEW, false);
        
        // 【新增】初始化地图
        mapController = new RaceMapController(binding.mapViewTrack);
        mapController.onCreate(savedInstanceState);
        
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 返回按钮
        binding.btnBack.setOnClickListener(v -> finish());
        
        // 管理员查看详情时隐藏导出与分享按钮
        if (isAdminView) {
            binding.bottomActionBar.setVisibility(View.GONE);
        } else {
            binding.btnShare.setOnClickListener(v -> shareResult());
            binding.btnExportSingle.setOnClickListener(v -> exportResult());
        }
    }

    @Override
    protected void initData() {
        ResultManager resultManager = new ResultManager();
        trackManager = new TrackManager();
        raceManager = new RaceManager();
        checkInManager = new CheckInManager();
        signupController = new RaceSignupController();
        
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
        
        // 检查赛事是否已结束，并为未完成选手自动创建空成绩记录
        resultManager.ensureUnfinishedResultsCreated(currentResult.getRaceId());
        
        // 重新加载成绩（可能刚刚创建了新的空成绩）
        currentResult = resultManager.loadResultById(resultId);
        
        renderResult(currentResult);
        
        // 加载并显示轨迹和相关数据
        loadAndDisplayTrack();
    }
    
    /**
     * 加载并显示轨迹和相关数据
     */
    private void loadAndDisplayTrack() {
        if (currentResult == null) {
            return;
        }
        
        String raceId = currentResult.getRaceId();
        String userId = currentResult.getUserId();
        
        // 加载赛事信息
        Race race = raceManager.getRaceById(raceId);
        if (race != null) {
            // 显示赛事名称
            currentRaceName = race.getName() != null ? race.getName() : "未知赛事";
            binding.tvRaceName.setText(currentRaceName);
        } else {
            currentRaceName = "未知赛事";
            binding.tvRaceName.setText(currentRaceName);
        }
        
        // 查询打卡记录（选手实际打卡位置）
        List<CheckInRecord> checkInRecords = checkInManager.queryCheckInRecords(raceId, userId);
        
        // 查询轨迹数据
        trackPoints = trackManager.queryTrack(raceId, userId);
        
        double totalDistance = 0.0;
        boolean isDNF = currentResult.getStatus() == Result.Status.DNF;
        
        if (trackPoints != null && !trackPoints.isEmpty()) {
            // 有轨迹数据，计算总距离
            totalDistance = calculateTotalDistance(trackPoints);
            
            // 在地图上绘制轨迹，并将打卡位置连接到轨迹中
            // 使用选手实际打卡位置，不显示管理员创建的打卡点
            mapController.drawTrack(trackPoints, checkInRecords);
            
            // 添加选手实际打卡位置标记
            if (checkInRecords != null && !checkInRecords.isEmpty()) {
                mapController.addCheckInRecords(checkInRecords);
            }
        } else if (!isDNF && checkInRecords != null && checkInRecords.size() > 1) {
            // 没有轨迹数据但已完成比赛，尝试从打卡记录计算距离（作为备选方案）
            totalDistance = calculateDistanceFromCheckInRecords(checkInRecords);
            
            // 只显示打卡位置标记
            if (!checkInRecords.isEmpty()) {
                mapController.addCheckInRecords(checkInRecords);
            }
        }
        // 对于未完成成绩且无轨迹数据，totalDistance保持为0
        
        // 转换为公里
        double totalDistanceKm = totalDistance / 1000.0;
        
        // 更新核心数据大字报
        updateKeyStats(totalDistanceKm);
    }
    
    /**
     * 从检查点计算总距离（作为轨迹数据的备选方案）
     */
    private double calculateDistanceFromCheckPoints(List<CheckPoint> checkPoints) {
        if (checkPoints == null || checkPoints.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        for (int i = 1; i < checkPoints.size(); i++) {
            CheckPoint prev = checkPoints.get(i - 1);
            CheckPoint curr = checkPoints.get(i);
            double distance = DistanceUtil.distanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
            totalDistance += distance;
        }
        
        return totalDistance;
    }
    
    /**
     * 从打卡记录计算总距离（作为轨迹数据的备选方案）
     */
    private double calculateDistanceFromCheckInRecords(List<CheckInRecord> checkInRecords) {
        if (checkInRecords == null || checkInRecords.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        for (int i = 1; i < checkInRecords.size(); i++) {
            CheckInRecord prev = checkInRecords.get(i - 1);
            CheckInRecord curr = checkInRecords.get(i);
            double distance = DistanceUtil.distanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
            totalDistance += distance;
        }
        
        return totalDistance;
    }
    
    /**
     * 更新核心数据大字报：总用时、里程、平均配速
     */
    private void updateKeyStats(double totalDistanceKm) {
        // 总用时 - 直接静态显示，不使用动画
        long totalSeconds = currentResult.getTotalSeconds();
        binding.tvTotal.setText(formatTime(totalSeconds));
        
        // 总里程（精确到小数点后三位）
        binding.tvDistance.setText(String.format("%.3f km", totalDistanceKm));
        
        // 平均配速（分钟/公里）
        // 检查是否是异常成绩（未完成）
        boolean isDNF = currentResult.getStatus() == Result.Status.DNF;
        
        if (isDNF) {
            // 异常成绩显示 0'00''
            binding.tvPace.setText("0'00\"");
        } else if (totalDistanceKm > 0 && totalSeconds > 0) {
            // 正常成绩计算配速
            double paceMinutes = (totalSeconds / 60.0) / totalDistanceKm;
            int minutes = (int) paceMinutes;
            int seconds = (int) ((paceMinutes - minutes) * 60);
            binding.tvPace.setText(String.format("%d'%02d\"", minutes, seconds));
        } else {
            // 其他情况显示默认值
            binding.tvPace.setText("--'/km");
        }
    }
    
    /**
     * 格式化时间为 HH:mm:ss 或 mm:ss
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
    
    /**
     * 计算轨迹总距离（优化算法，提高准确性）
     * 使用速度验证和异常值过滤，确保计算出的距离准确可靠
     */
    private double calculateTotalDistance(List<TrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.size() < 2) {
            return 0.0;
        }
        
        // 按时间排序，确保轨迹点按时间顺序处理（虽然queryTrack已排序，但为了保险再次排序）
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
            // 跳过无效的点（经纬度为0或异常值）
            if (curr.getLatitude() == 0.0 && curr.getLongitude() == 0.0) {
                continue;
            }
            
            // 检查经纬度是否在合理范围内
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
                
                // 如果距离为0，跳过
                if (distance <= 0) {
                    continue;
                }
                
                // 如果时间差为0或负数，但距离很小（小于50米），可能是GPS精度问题，计入
                if (timeDiff <= 0) {
                    if (distance < 50) {
                        totalDistance += distance;
                        lastValidPoint = curr;
                    }
                    continue;
                }
                
                // 计算速度（米/秒）
                double speed = distance / timeDiff;
                // 转换为公里/小时
                double speedKmh = speed * 3.6;
                
                // 改进的异常值过滤逻辑：
                // 1. 速度超过40公里/小时且距离超过100米，可能是GPS漂移（越野赛正常速度通常在5-15公里/小时）
                // 2. 单次移动距离超过3公里，可能是GPS漂移
                // 3. 对于小距离（<50米），即使速度稍快也计入（可能是GPS精度问题）
                boolean isAbnormal = false;
                
                if (distance >= 50) {
                    // 对于较大距离，严格检查速度
                    if (speedKmh > 40.0 || distance > 3000) {
                        isAbnormal = true;
                    }
                } else {
                    // 对于小距离，允许稍快的速度（可能是GPS精度波动）
                    if (speedKmh > 60.0) {
                        isAbnormal = true;
                    }
                }
                
                if (!isAbnormal) {
                    totalDistance += distance;
                    lastValidPoint = curr;
                } else {
                    // 如果异常但距离很小，仍然计入（可能是GPS精度问题）
                    if (distance < 30) {
                        totalDistance += distance;
                        lastValidPoint = curr;
                    }
                    // 否则跳过这个点，可能是GPS漂移
                }
            } else {
                // 第一个有效点
                lastValidPoint = curr;
            }
        }
        
        return totalDistance;
    }

    private void renderResult(Result result) {
        // 用户信息
        binding.tvUser.setText(getString(R.string.result_user_format, result.getUserId()));
        
        // 根据报名顺序计算序号，并显示在选手ID下方
        currentSequenceNumber = 0;
        if (signupController != null && result.getRaceId() != null && result.getUserId() != null) {
            currentSequenceNumber = signupController.getUserSequenceNumber(result.getRaceId(), result.getUserId());
        }
        if (binding.tvSequence != null) {
            if (currentSequenceNumber > 0) {
                binding.tvSequence.setText("序号：" + currentSequenceNumber + "号");
                binding.tvSequence.setVisibility(View.VISIBLE);
            } else {
                binding.tvSequence.setText("序号：--");
                binding.tvSequence.setVisibility(View.VISIBLE);
            }
        }
        binding.tvRank.setText(result.getRank() > 0
                ? getString(R.string.result_rank_full_format, result.getRank())
                : getString(R.string.result_rank_dnf));
        
        // 详细数据卡片
        binding.tvElapsed.setText(getString(R.string.result_elapsed_format, result.getElapsedSeconds()));
        
        // 状态显示为中文（这里显示的是选手个人的完赛状态）
        String statusText = getStatusText(result.getStatus());
        binding.tvStatus.setText("状态：" + statusText);
        
        // 显示完赛徽章（如果状态是已完赛）
        showFinisherBadge(result);
        
        // 核心数据大字报在updateKeyStats中更新
    }
    
    /**
     * 获取状态的中文文本
     */
    private String getStatusText(Result.Status status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case FINISHED:
                return "已完成";
            case FINISHED_WITH_PENALTY:
                return "已完成"; // 删除罚时显示
            case DNF:
                return "未完成";
            default:
                return "未知";
        }
    }
    
    /**
     * 显示完赛徽章动画
     * 根据赛事结束时间动态显示状态：
     * - 赛事未结束且选手已完成：绿色"已完成"按钮
     * - 赛事已结束：红色"已结束"按钮（无论选手是否完赛）
     */
    private void showFinisherBadge(Result result) {
        if (currentResult == null) {
            binding.badgeContainer.setVisibility(View.GONE);
            return;
        }
        
        // 获取赛事信息
        String raceId = currentResult.getRaceId();
        Race race = raceManager.getRaceById(raceId);
        
        if (race == null || race.getEndTime() == null) {
            binding.badgeContainer.setVisibility(View.GONE);
            return;
        }
        
        // 检查赛事是否已结束
        Date now = new Date();
        boolean isRaceEnded = race.getEndTime().before(now);
        
        // 检查选手是否完赛
        Result.Status status = result.getStatus();
        boolean isPlayerFinished = (status == Result.Status.FINISHED || status == Result.Status.FINISHED_WITH_PENALTY); // 保留兼容性
        
        // 根据赛事状态和选手状态决定显示什么
        if (isRaceEnded) {
            // 赛事已结束：显示红色"已结束"按钮
            binding.badgeContainer.setVisibility(View.VISIBLE);
            binding.tvFinisherBadge.setText("已结束");
            // 设置红色背景
            binding.cardBadge.setCardBackgroundColor(0xE6F44336); // 红色
        } else if (isPlayerFinished) {
            // 赛事未结束但选手已完成：显示绿色"已完成"按钮
            binding.badgeContainer.setVisibility(View.VISIBLE);
            binding.tvFinisherBadge.setText("已完成");
            // 设置绿色背景
            binding.cardBadge.setCardBackgroundColor(0xE64CAF50); // 绿色
        } else {
            // 赛事未结束且选手未完成：不显示徽章
            binding.badgeContainer.setVisibility(View.GONE);
            return;
        }
        
        // 缩放动画：从0放大到1，带弹性效果
        binding.badgeContainer.setScaleX(0f);
        binding.badgeContainer.setScaleY(0f);
        binding.badgeContainer.setAlpha(0f);
        
        binding.badgeContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // 动画结束后可以添加其他效果
                    }
                })
                .start();
    }

    private void shareResult() {
        if (currentResult == null) return;
        // 分享文本格式：“赛事名称  排名：2  李明（1号） 用时：03:44:32”
        String raceName = currentRaceName != null ? currentRaceName : "";
        int rank = currentResult.getRank();
        String rankText = rank > 0 ? "排名：" + rank : "排名：DNF";
        String userId = currentResult.getUserId() != null ? currentResult.getUserId() : "";
        String sequencePart = currentSequenceNumber > 0 ? "（" + currentSequenceNumber + "号）" : "";
        String timeText = formatTimeHMS(currentResult.getTotalSeconds());

        String shareText = raceName + "  " + rankText + "  " + userId + sequencePart + " 用时：" + timeText;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "分享成绩"));
    }

    private void exportResult() {
        if (currentResult == null) return;
        try {
            // 选手端导出单人成绩 CSV：
            // 取消“罚时”列，只保留总用时（HH:mm:ss），并增加“赛事名称”“序号”列
            // 文件命名为“赛事名称_序号_选手ID.csv”
            String raceName = currentRaceName != null ? currentRaceName : "";
            String safeRaceName = raceName.replaceAll("[\\\\/:*?\"<>|]", "_");
            int sequence = currentSequenceNumber;
            String sequencePart = sequence > 0 ? String.valueOf(sequence) : "0";
            String userId = currentResult.getUserId() != null ? currentResult.getUserId() : "";
            String fileName = safeRaceName + "_" + sequencePart + "_" + userId + ".csv";

            StringBuilder builder = new StringBuilder();
            builder.append("赛事名称,序号,排名,选手ID,总用时,状态\n");
            String rankText = currentResult.getRank() > 0 ? String.valueOf(currentResult.getRank()) : "DNF";
            String timeText = formatTimeHMS(currentResult.getTotalSeconds());
            builder.append(raceName).append(",")
                    .append(sequencePart).append(",")
                    .append(rankText).append(",")
                    .append(userId).append(",")
                    .append(timeText).append(",")
                    .append(currentResult.getStatus() != null ? currentResult.getStatus().name() : "")
                    .append("\n");

            String csv = builder.toString();
            java.io.File dir = new java.io.File(getFilesDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建导出目录: " + dir.getAbsolutePath());
            }
            java.io.File file = new java.io.File(dir, fileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出成绩"));
        } catch (IOException e) {
            UIUtil.showToast(this, "导出失败：" + e.getMessage());
        }
    }
    
    /**
     * 始终格式化为 HH:mm:ss，用于分享与 CSV 导出
     */
    private String formatTimeHMS(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
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
        if (signupController != null) {
            signupController.close();
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


