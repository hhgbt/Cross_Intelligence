package com.example.cross_intelligence.mvc.view.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityRaceListBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.race.CreateRaceActivity;
import com.example.cross_intelligence.mvc.view.race.RaceDetailActivity;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 管理员赛事列表页面：显示该管理员创建的所有赛事
 * 分为两个板块：进行中和已结束
 */
public class RaceListActivity extends BaseActivity {

    private ActivityRaceListBinding binding;
    private RaceManager raceManager;
    private RaceAdapter notStartedAdapter;
    private RaceAdapter ongoingAdapter;
    private RaceAdapter endedAdapter;
    private final List<Race> notStartedRaces = new ArrayList<>();
    private final List<Race> ongoingRaces = new ArrayList<>();
    private final List<Race> endedRaces = new ArrayList<>();
    private int currentTab = 0; // 0: 未开始, 1: 进行中, 2: 已结束
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        
        binding = ActivityRaceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        raceManager = new RaceManager();
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

        // 初始化未开始赛事列表
        notStartedAdapter = new RaceAdapter(notStartedRaces, dateTimeFormat, race -> {
            Intent intent = new Intent(RaceListActivity.this, RaceDetailActivity.class);
            intent.putExtra("raceId", race.getRaceId());
            startActivity(intent);
        }, race -> {
            Intent intent = new Intent(RaceListActivity.this, CreateRaceActivity.class);
            intent.putExtra("raceId", race.getRaceId());
            intent.putExtra("mode", "edit");
            startActivity(intent);
        }, race -> {
            showDeleteConfirmDialog(race);
        });
        binding.rvNotStartedRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotStartedRaces.setAdapter(notStartedAdapter);

        // 初始化进行中赛事列表
        ongoingAdapter = new RaceAdapter(ongoingRaces, dateTimeFormat, race -> {
            // 点击赛事，跳转到详情页
            Intent intent = new Intent(RaceListActivity.this, RaceDetailActivity.class);
            intent.putExtra("raceId", race.getRaceId());
            startActivity(intent);
        }, race -> {
            // 编辑赛事
            Intent intent = new Intent(RaceListActivity.this, CreateRaceActivity.class);
            intent.putExtra("raceId", race.getRaceId());
            intent.putExtra("mode", "edit");
            startActivity(intent);
        }, race -> {
            // 删除赛事
            showDeleteConfirmDialog(race);
        });
        binding.rvOngoingRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOngoingRaces.setAdapter(ongoingAdapter);

        // 初始化已结束赛事列表
        endedAdapter = new RaceAdapter(endedRaces, dateTimeFormat, null, null, race -> {
            // 删除赛事（已结束的也可以删除）
            showDeleteConfirmDialog(race);
        });
        binding.rvEndedRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEndedRaces.setAdapter(endedAdapter);

        // 设置Tab切换
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("未开始"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("进行中"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("已结束"));
        
        // 默认选中"进行中"分类（索引1）
        TabLayout.Tab defaultTab = binding.tabLayout.getTabAt(1);
        if (defaultTab != null) {
            defaultTab.select();
            currentTab = 1;
        }

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

        // 设置下拉刷新
        binding.swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.forest_green, getTheme()));
        binding.swipeRefresh.setOnRefreshListener(() -> {
            // 【新增】拉取云端数据
            String organizerId = PreferenceUtil.getString(this, "account", "");
            if (!TextUtils.isEmpty(organizerId)) {
                raceManager.fetchRacesFromCloud(organizerId);
            }
            // 同时加载本地数据（UI 刷新）
            loadRaces();
            // 延迟一点停止刷新动画，给同步留点视觉时间
            binding.swipeRefresh.postDelayed(() -> binding.swipeRefresh.setRefreshing(false), 1500);
        });

        binding.progressMyResults.setVisibility(View.VISIBLE);
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

        // 【新增】进入页面时自动同步一次云端数据
        raceManager.fetchRacesFromCloud(organizerId);
        
        loadRaces();
    }

    /**
     * 加载赛事列表并分类
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
                int notStartedSize = notStartedRaces.size();
                int ongoingSize = ongoingRaces.size();
                int endedSize = endedRaces.size();
                notStartedRaces.clear();
                ongoingRaces.clear();
                endedRaces.clear();
                if (notStartedSize > 0) {
                    notStartedAdapter.notifyItemRangeRemoved(0, notStartedSize);
                }
                if (ongoingSize > 0) {
                    ongoingAdapter.notifyItemRangeRemoved(0, ongoingSize);
                }
                if (endedSize > 0) {
                    endedAdapter.notifyItemRangeRemoved(0, endedSize);
                }

                // 分类赛事
                Date now = new Date();
                for (Race race : races) {
                    if (isRaceEnded(race, now)) {
                        endedRaces.add(race);
                    } else if (isRaceNotStarted(race, now)) {
                        notStartedRaces.add(race);
                    } else {
                        ongoingRaces.add(race);
                    }
                }

                // 按开始时间倒序排序
                Comparator<Race> timeComparator = (r1, r2) -> {
                    if (r1.getStartTime() != null && r2.getStartTime() != null) {
                        return r2.getStartTime().compareTo(r1.getStartTime());
                    }
                    return 0;
                };
                Collections.sort(notStartedRaces, timeComparator);
                Collections.sort(ongoingRaces, timeComparator);
                Collections.sort(endedRaces, timeComparator);

                // 通知适配器数据已更新
                notStartedAdapter.notifyDataSetChanged();
                ongoingAdapter.notifyDataSetChanged();
                endedAdapter.notifyDataSetChanged();

                // 更新UI
                updateUI();

                binding.progressMyResults.setVisibility(View.GONE);
            });
        });
    }

    /**
     * 检查赛事是否已结束
     */
    private boolean isRaceEnded(Race race, Date now) {
        if (race.getEndTime() == null) {
            return false;
        }
        return race.getEndTime().before(now);
    }

    /**
     * 检查赛事是否未开始
     */
    private boolean isRaceNotStarted(Race race, Date now) {
        if (race.getStartTime() == null) {
            return false;
        }
        return race.getStartTime().after(now);
    }

    /**
     * 更新UI显示
     */
    private void updateUI() {
        int totalCount = notStartedRaces.size() + ongoingRaces.size() + endedRaces.size();
        
        // 显示或隐藏空状态页面
        if (totalCount == 0) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
        }

        // 更新Tab显示
        updateTabVisibility();
    }

    /**
     * 根据当前Tab更新显示内容
     */
    private void updateTabVisibility() {
        // 先隐藏所有
        binding.rvNotStartedRaces.setVisibility(View.GONE);
        binding.rvOngoingRaces.setVisibility(View.GONE);
        binding.rvEndedRaces.setVisibility(View.GONE);
        binding.tvNotStartedEmpty.setVisibility(View.GONE);
        binding.tvOngoingEmpty.setVisibility(View.GONE);
        binding.tvEndedEmpty.setVisibility(View.GONE);

        if (currentTab == 0) {
            // 显示未开始
            if (notStartedRaces.isEmpty()) {
                binding.tvNotStartedEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvNotStartedRaces.setVisibility(View.VISIBLE);
            }
        } else if (currentTab == 1) {
            // 显示进行中
            if (ongoingRaces.isEmpty()) {
                binding.tvOngoingEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvOngoingRaces.setVisibility(View.VISIBLE);
            }
        } else {
            // 显示已结束
            if (endedRaces.isEmpty()) {
                binding.tvEndedEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvEndedRaces.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时刷新列表
        loadRaces();
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(Race race) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除赛事 \"" + race.getName() + "\" 吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 使用回调确保删除完成后立即刷新UI
                    raceManager.deleteRace(race.getRaceId(), new RaceManager.DeleteCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                UIUtil.showToast(RaceListActivity.this, "赛事已删除");
                                // 删除成功后立即刷新列表
                                loadRaces();
                            });
                        }

                        @Override
                        public void onError(@NonNull Throwable error) {
                            runOnUiThread(() -> {
                                UIUtil.showToast(RaceListActivity.this, "删除失败：" + error.getMessage());
                            });
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 赛事点击监听接口
     */
    interface OnRaceClickListener {
        void onRaceClick(@NonNull Race race);
    }

    /**
     * 赛事编辑监听接口
     */
    interface OnRaceEditListener {
        void onRaceEdit(@NonNull Race race);
    }

    /**
     * 赛事删除监听接口
     */
    interface OnRaceDeleteListener {
        void onRaceDelete(@NonNull Race race);
    }

    /**
     * 赛事列表适配器
     */
    private class RaceAdapter extends RecyclerView.Adapter<RaceAdapter.RaceViewHolder> {

        private final List<Race> data;
        private final SimpleDateFormat dateTimeFormat;
        private final OnRaceClickListener clickListener;
        private final OnRaceEditListener editListener;
        private final OnRaceDeleteListener deleteListener;
        private final com.example.cross_intelligence.mvc.controller.RaceSignupController signupController;

        RaceAdapter(List<Race> data, SimpleDateFormat dateTimeFormat, 
                   OnRaceClickListener clickListener,
                   OnRaceEditListener editListener, 
                   OnRaceDeleteListener deleteListener) {
            this.data = data;
            this.dateTimeFormat = dateTimeFormat;
            this.clickListener = clickListener;
            this.editListener = editListener;
            this.deleteListener = deleteListener;
            this.signupController = new com.example.cross_intelligence.mvc.controller.RaceSignupController();
        }

        @NonNull
        @Override
        public RaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_race_list, parent, false);
            return new RaceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RaceViewHolder holder, int position) {
            Race race = data.get(position);
            
            // 检查赛事是否已结束
            boolean isEnded = isRaceEnded(race);
            
            // 设置赛事名称
            holder.tvRaceName.setText(race.getName());
            
            // 设置状态标签
            boolean isNotStarted = isRaceNotStarted(race);
            if (isEnded) {
                holder.tvStatusTag.setText("已结束");
                holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ended);
            } else if (isNotStarted) {
                holder.tvStatusTag.setText("未开始");
                holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_not_started);
            } else {
                holder.tvStatusTag.setText("进行中");
                holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ongoing);
            }

            // 设置时间段（整行显示）
            if (race.getStartTime() != null && race.getEndTime() != null) {
                String timeRange = dateTimeFormat.format(race.getStartTime()) + " - " 
                        + dateTimeFormat.format(race.getEndTime());
                holder.tvRaceTimeRange.setText(timeRange);
            } else {
                holder.tvRaceTimeRange.setText("待定");
            }

            // 设置路线（起点→终点）
            String routeText = getRouteText(race);
            holder.tvRaceRoute.setText(routeText);

            // 设置报名人数
            int signupCount = signupController.getSignedUpCount(race.getRaceId());
            holder.tvSignupCount.setText("已有 " + signupCount + " 人报名");

            // 如果赛事已结束，设置灰色样式并禁用点击
            if (isEnded) {
                holder.itemView.setAlpha(0.5f); // 变灰
                holder.itemView.setEnabled(false); // 禁用点击
                holder.itemView.setOnClickListener(null);
                // 已结束的赛事不能编辑，只能删除
                holder.btnEdit.setVisibility(View.GONE);
            } else {
                holder.itemView.setAlpha(1.0f); // 正常显示
                holder.itemView.setEnabled(true); // 启用点击
                holder.itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onRaceClick(race);
                    }
                });
                holder.btnEdit.setVisibility(View.VISIBLE);
            }

            // 编辑按钮点击事件
            holder.btnEdit.setOnClickListener(v -> {
                if (editListener != null && !isEnded) {
                    editListener.onRaceEdit(race);
                }
            });

            // 删除按钮点击事件
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onRaceDelete(race);
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

        /**
         * 获取路线文本（起点→终点）
         */
        private String getRouteText(Race race) {
            if (race.getCheckPoints() == null || race.getCheckPoints().isEmpty()) {
                return "暂无打卡点";
            }
            
            String startPoint = null;
            String endPoint = null;
            
            for (CheckPoint point : race.getCheckPoints()) {
                if (CheckPoint.TYPE_START.equals(point.getType())) {
                    startPoint = point.getName();
                } else if (CheckPoint.TYPE_FINISH.equals(point.getType())) {
                    endPoint = point.getName();
                }
            }
            
            if (startPoint != null && endPoint != null) {
                return startPoint + " → " + endPoint;
            } else if (startPoint != null) {
                return startPoint + " → ...";
            } else if (endPoint != null) {
                return "... → " + endPoint;
            } else {
                return "检查点路线";
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class RaceViewHolder extends RecyclerView.ViewHolder {
            final TextView tvRaceName;
            final TextView tvStatusTag;
            final TextView tvRaceTimeRange;
            final TextView tvRaceRoute;
            final TextView tvSignupCount;
            final ImageButton btnEdit;
            final ImageButton btnDelete;

            RaceViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRaceName = itemView.findViewById(R.id.tvRaceName);
                tvStatusTag = itemView.findViewById(R.id.tvStatusTag);
                tvRaceTimeRange = itemView.findViewById(R.id.tvRaceTimeRange);
                tvRaceRoute = itemView.findViewById(R.id.tvRaceRoute);
                tvSignupCount = itemView.findViewById(R.id.tvSignupCount);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
