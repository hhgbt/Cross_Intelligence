package com.example.cross_intelligence.mvc.view.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityAdminRaceResultsBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.controller.UserManager;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.RaceSignup;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.mvc.util.ResultExportUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.result.ResultDetailActivity;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 管理员查看某个赛事的所有选手成绩页面
 * 包含导出功能和红色感叹号按钮（查看未完成比赛的选手）
 */
public class AdminRaceResultsActivity extends BaseActivity {
    
    /**
     * 成绩点击监听接口
     */
    interface OnResultClickListener {
        void onResultClick(@NonNull Result result);
    }
    
    // 实现接口方法
    private final OnResultClickListener resultClickListener = this::onResultClick;

    public static final String EXTRA_RACE_ID = "extra_race_id";
    public static final String EXTRA_RACE_NAME = "extra_race_name";

    private ActivityAdminRaceResultsBinding binding;
    private final List<Result> resultList = new ArrayList<>();
    private ResultAdapter adapter;
    private ResultManager resultManager;
    private RaceSignupController signupController;
    private UserManager userManager;
    private String raceId;
    private String raceName;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminRaceResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置工具栏返回按钮
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ResultAdapter(resultList, resultClickListener, userManager);
        binding.rvResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvResults.setAdapter(adapter);

        // 设置列表加载动画（自下而上交替入场）
        try {
            LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
                    this, R.anim.layout_animation_fall_down);
            binding.rvResults.setLayoutAnimation(animation);
        } catch (Exception e) {
            // 如果动画文件不存在，忽略错误
        }

        // Extended FAB - 导出按钮
        binding.fabExport.setOnClickListener(v -> exportAll());

        binding.progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected void initData() {
        resultManager = new ResultManager();
        signupController = new RaceSignupController();
        userManager = new UserManager();

        raceId = getIntent().getStringExtra(EXTRA_RACE_ID);
        raceName = getIntent().getStringExtra(EXTRA_RACE_NAME);

        if (TextUtils.isEmpty(raceId)) {
            UIUtil.showToast(this, "缺少赛事ID");
            finish();
            return;
        }

        // 设置标题
        if (!TextUtils.isEmpty(raceName)) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(raceName);
            }
        }

        loadResults();
    }

    /**
     * 加载成绩列表
     */
    private void loadResults() {
        // 检查赛事是否已结束，并为未完成选手自动创建空成绩记录
        resultManager.ensureUnfinishedResultsCreated(raceId);
        
        int previousSize = resultList.size();
        resultList.clear();
        List<Result> results = resultManager.loadResults(raceId);
        resultList.addAll(resultManager.rankResults(results, raceId));
        
        if (previousSize > 0) {
            adapter.notifyItemRangeRemoved(0, previousSize);
        }
        if (!resultList.isEmpty()) {
            adapter.notifyItemRangeInserted(0, resultList.size());
        }
        
        // 更新数据概览
        updateDashboard();
        
        binding.progressBar.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(resultList.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvResults.setVisibility(resultList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * 更新数据概览区
     */
    private void updateDashboard() {
        // 总人数
        int totalCount = signupController != null ? signupController.getSignedUpCount(raceId) : 0;
        binding.tvTotalCount.setText(String.valueOf(totalCount));

        // 平均用时
        String avgTime = calculateAverageTime();
        binding.tvAverageTime.setText(avgTime);

        // 异常数（DNF或未完成）
        int abnormalCount = countAbnormalResults();
        binding.tvAbnormalCount.setText(String.valueOf(abnormalCount));
        
        // 设置异常数点击事件
        binding.llAbnormalCount.setOnClickListener(v -> showUnfinishedPlayers());
    }

    /**
     * 计算平均用时
     */
    private String calculateAverageTime() {
        if (resultList.isEmpty()) {
            return "00:00:00";
        }
        
        long totalSeconds = 0;
        int finishedCount = 0;
        
        for (Result result : resultList) {
            if (result.getStatus() != null && result.getStatus() != Result.Status.DNF && result.getTotalSeconds() > 0) {
                totalSeconds += result.getTotalSeconds();
                finishedCount++;
            }
        }
        
        if (finishedCount == 0) {
            return "00:00:00";
        }
        
        long avgSeconds = totalSeconds / finishedCount;
        return formatTime(avgSeconds);
    }

    /**
     * 格式化时间（秒转 HH:mm:ss）
     */
    static String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * 统计异常结果数（DNF或未完成）
     */
    private int countAbnormalResults() {
        int count = 0;
        for (Result result : resultList) {
            if (result.getStatus() == null || result.getStatus() == Result.Status.DNF || result.getTotalSeconds() == 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 导出所有成绩
     */
    private void exportAll() {
        if (resultList.isEmpty()) {
            UIUtil.showToast(this, "暂无成绩");
            return;
        }
        try {
            String fileName = "race_results_" + raceId + ".csv";
            Uri uri = ResultExportUtil.exportToFile(this, resultList, fileName);
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
     * 显示未完成比赛的选手名单（从异常数卡片点击触发）
     */
    private void showUnfinishedPlayers() {
        // 获取所有异常结果（DNF或未完成）
        List<String> abnormalUserIds = new ArrayList<>();
        for (Result result : resultList) {
            if (result.getStatus() == null || result.getStatus() == Result.Status.DNF || result.getTotalSeconds() == 0) {
                abnormalUserIds.add(result.getUserId());
            }
        }

        // 如果没有异常选手，显示提示
        if (abnormalUserIds.isEmpty()) {
            UIUtil.showToast(this, "所有选手均完成比赛！");
            return;
        }

        // 显示异常选手名单对话框
        showUnfinishedPlayersDialog(abnormalUserIds);
    }

    /**
     * 显示未完成选手名单对话框
     */
    private void showUnfinishedPlayersDialog(List<String> unfinishedUserIds) {
        StringBuilder message = new StringBuilder();
        message.append("以下选手未完成比赛（共").append(unfinishedUserIds.size()).append("人）：\n\n");
        
        for (int i = 0; i < unfinishedUserIds.size(); i++) {
            message.append(i + 1).append(". ").append(unfinishedUserIds.get(i));
            if (i < unfinishedUserIds.size() - 1) {
                message.append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("未完成比赛选手")
                .setMessage(message.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * 实现 OnResultClickListener 接口
     */
    public void onResultClick(@NonNull Result result) {
        // 管理员点击成绩项，跳转到选手成绩详情页（隐藏导出与分享按钮）
        Intent intent = new Intent(this, ResultDetailActivity.class);
        intent.putExtra(ResultDetailActivity.EXTRA_RESULT_ID, result.getResultId());
        intent.putExtra(ResultDetailActivity.EXTRA_IS_ADMIN_VIEW, true);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (signupController != null) {
            signupController.close();
        }
    }

    /**
     * 成绩列表适配器
     */
    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ResultViewHolder> {

        private final List<Result> data;
        private final OnResultClickListener listener;
        private final UserManager userManager;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        ResultAdapter(List<Result> data, OnResultClickListener listener, UserManager userManager) {
            this.data = data;
            this.listener = listener;
            this.userManager = userManager;
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_race_result, parent, false);
            return new ResultViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
            Result result = data.get(position);
            
            // 设置排名徽章
            int rank = result.getRank();
            if (rank > 0) {
                holder.tvRankBadge.setText(String.valueOf(rank));
                // 前三名使用特殊颜色
                if (rank == 1) {
                    holder.tvRankBadge.setBackgroundResource(R.drawable.bg_rank_badge_gold);
                    holder.tvRankBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
                } else if (rank == 2) {
                    holder.tvRankBadge.setBackgroundResource(R.drawable.bg_rank_badge_silver);
                    holder.tvRankBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
                } else if (rank == 3) {
                    holder.tvRankBadge.setBackgroundResource(R.drawable.bg_rank_badge_bronze);
                    holder.tvRankBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
                } else {
                    holder.tvRankBadge.setBackgroundResource(R.drawable.bg_rank_badge);
                    holder.tvRankBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray));
                    holder.tvRankBadge.setTextSize(14);
                }
            } else {
                holder.tvRankBadge.setText("DNF");
                holder.tvRankBadge.setBackgroundResource(R.drawable.bg_rank_badge);
                holder.tvRankBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray));
                holder.tvRankBadge.setTextSize(12);
            }

            // 获取选手姓名
            String playerName = result.getUserId();
            if (userManager != null) {
                userManager.fetchUser(result.getUserId(), user -> {
                    if (user != null && user.getName() != null) {
                        holder.tvPlayerName.setText(user.getName());
                    } else {
                        holder.tvPlayerName.setText(result.getUserId());
                    }
                });
            } else {
                holder.tvPlayerName.setText(result.getUserId());
            }

            // 序号（号码布）根据报名时间先后分配：序号：xx
            int sequenceNumber = signupController != null
                    ? signupController.getUserSequenceNumber(raceId, result.getUserId())
                    : 0;
            if (sequenceNumber > 0) {
                holder.tvBibNumber.setText("序号：" + sequenceNumber);
            } else {
                holder.tvBibNumber.setText("序号：--");
            }

            // 完赛总时长
            if (result.getTotalSeconds() > 0 && (result.getStatus() == null || result.getStatus() != Result.Status.DNF)) {
                holder.tvTime.setText("用时：" + AdminRaceResultsActivity.formatTime(result.getTotalSeconds()));
            } else {
                holder.tvTime.setText("--:--:--");
            }

            // 设置背景色：完赛白色，DNF/异常极浅橙色
            com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) holder.itemView;
            if (result.getStatus() == null || result.getStatus() == Result.Status.DNF || result.getTotalSeconds() == 0) {
                // DNF/异常：极浅橙色
                cardView.setCardBackgroundColor(0xFFFFF3E0);
            } else {
                // 完赛：白色
                cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onResultClick(result);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ResultViewHolder extends RecyclerView.ViewHolder {
            final TextView tvRankBadge;
            final TextView tvPlayerName;
            final TextView tvBibNumber;
            final TextView tvTime;

            ResultViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRankBadge = itemView.findViewById(R.id.tvRankBadge);
                tvPlayerName = itemView.findViewById(R.id.tvPlayerName);
                tvBibNumber = itemView.findViewById(R.id.tvBibNumber);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }
    }
}

