package com.example.cross_intelligence.mvc.view.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityAdminPlayerResultsBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 管理员选手成绩页面：显示该管理员创建的所有赛事
 * 点击赛事可查看该赛事下所有选手的比赛成绩
 */
public class AdminPlayerResultsActivity extends BaseActivity {

    private ActivityAdminPlayerResultsBinding binding;
    private RaceManager raceManager;
    private RaceSignupController signupController;
    private ResultManager resultManager;
    private RaceAdapter adapter;
    private final List<Race> raceList = new ArrayList<>();

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminPlayerResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        raceManager = new RaceManager();
        signupController = new RaceSignupController();
        resultManager = new ResultManager();
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

        // 初始化赛事列表
        adapter = new RaceAdapter(raceList, signupController, resultManager, race -> {
            // 点击赛事，跳转到该赛事的成绩页面
            Intent intent = new Intent(AdminPlayerResultsActivity.this, AdminRaceResultsActivity.class);
            intent.putExtra(AdminRaceResultsActivity.EXTRA_RACE_ID, race.getRaceId());
            intent.putExtra(AdminRaceResultsActivity.EXTRA_RACE_NAME, race.getName());
            startActivity(intent);
        });
        binding.rvRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRaces.setAdapter(adapter);

        // 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.forest_green, getTheme()));
        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadRaces();
            binding.swipeRefresh.setRefreshing(false);
        });

        binding.progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected void initData() {
        // 获取当前登录的管理员账号
        String organizerId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(organizerId)) {
            UIUtil.showToast(this, "请先登录");
            finish();
            return;
        }

        loadRaces();
    }

    /**
     * 加载赛事列表
     */
    private void loadRaces() {
        String organizerId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(organizerId)) {
            return;
        }

        // 查询该管理员创建的所有赛事
        raceManager.queryRacesByOrganizer(organizerId, races -> {
            runOnUiThread(() -> {
                // 清空现有数据
                int previousSize = raceList.size();
                raceList.clear();
                if (previousSize > 0) {
                    adapter.notifyItemRangeRemoved(0, previousSize);
                }

                // 过滤掉未开始的赛事
                Date now = new Date();
                for (Race race : races) {
                    // 如果赛事没有开始时间，或者开始时间已过，则显示
                    if (race.getStartTime() == null || !race.getStartTime().after(now)) {
                        raceList.add(race);
                    }
                }

                // 按开始时间倒序排序
                Comparator<Race> timeComparator = (r1, r2) -> {
                    if (r1.getStartTime() != null && r2.getStartTime() != null) {
                        return r2.getStartTime().compareTo(r1.getStartTime());
                    }
                    return 0;
                };
                Collections.sort(raceList, timeComparator);

                // 通知适配器数据已更新
                if (raceList.size() > 0) {
                    adapter.notifyItemRangeInserted(0, raceList.size());
                }

                // 更新UI
                updateUI();

                binding.progressBar.setVisibility(View.GONE);
            });
        });
    }

    /**
     * 更新UI显示
     */
    private void updateUI() {
        // 显示或隐藏空状态页面
        if (raceList.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.rvRaces.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.rvRaces.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时刷新列表
        loadRaces();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (signupController != null) {
            signupController.close();
        }
    }

    /**
     * 赛事列表适配器
     */
    private static class RaceAdapter extends RecyclerView.Adapter<RaceAdapter.RaceViewHolder> {

        private final List<Race> data;
        private final RaceSignupController signupController;
        private final ResultManager resultManager;
        private final OnRaceClickListener clickListener;

        interface OnRaceClickListener {
            void onRaceClick(@NonNull Race race);
        }

        RaceAdapter(List<Race> data, RaceSignupController signupController, 
                   ResultManager resultManager, OnRaceClickListener clickListener) {
            this.data = data;
            this.signupController = signupController;
            this.resultManager = resultManager;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public RaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_player_results, parent, false);
            return new RaceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RaceViewHolder holder, int position) {
            Race race = data.get(position);
            
            // 设置赛事名称
            holder.tvRaceName.setText(race.getName());
            
            // 设置状态标签
            boolean isEnded = isRaceEnded(race);
            boolean isNotStarted = isRaceNotStarted(race);
            if (isEnded) {
                holder.tvStatusTag.setText("已结束");
                holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ended);
                holder.tvStatusTag.setVisibility(View.VISIBLE);
            } else if (isNotStarted) {
                holder.tvStatusTag.setText("未开始");
                holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_not_started);
                holder.tvStatusTag.setVisibility(View.VISIBLE);
            } else {
                holder.tvStatusTag.setText("进行中");
                holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ongoing);
                holder.tvStatusTag.setVisibility(View.VISIBLE);
            }
            
            // 获取报名人数
            int signedUpCount = signupController != null ? signupController.getSignedUpCount(race.getRaceId()) : 0;
            
            // 获取完赛人数（状态不是 DNF 的成绩）
            int finishedCount = 0;
            if (resultManager != null) {
                List<Result> results = resultManager.loadResults(race.getRaceId());
                for (Result result : results) {
                    if (result.getStatus() != null && result.getStatus() != Result.Status.DNF) {
                        finishedCount++;
                    }
                }
            }
            
            // 设置统计信息
            holder.tvStatistics.setText(String.format("已报名：%d人 / 已完赛：%d人", signedUpCount, finishedCount));

            // 设置点击事件（整个卡片都可以点击）
            holder.itemView.setOnClickListener(v -> {
                if (this.clickListener != null) {
                    this.clickListener.onRaceClick(race);
                }
            });
        }
        
        /**
         * 检查赛事是否已结束
         */
        private boolean isRaceEnded(Race race) {
            if (race.getEndTime() == null) {
                return false;
            }
            Date now = new Date();
            return race.getEndTime().before(now);
        }

        /**
         * 检查赛事是否未开始
         */
        private boolean isRaceNotStarted(Race race) {
            if (race.getStartTime() == null) {
                return false;
            }
            Date now = new Date();
            return race.getStartTime().after(now);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class RaceViewHolder extends RecyclerView.ViewHolder {
            final TextView tvRaceName;
            final TextView tvStatusTag;
            final TextView tvStatistics;

            RaceViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRaceName = itemView.findViewById(R.id.tvRaceName);
                tvStatusTag = itemView.findViewById(R.id.tvStatusTag);
                tvStatistics = itemView.findViewById(R.id.tvStatistics);
            }
        }
    }
}

