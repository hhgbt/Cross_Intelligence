package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cross_intelligence.databinding.ActivityMyRacesBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.RaceSignup;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.checkin.CheckInActivity;
import com.example.cross_intelligence.mvc.view.race.RaceDetailActivity;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.Result;

import io.realm.Realm;

/**
 * 我的赛事：显示已报名的赛事列表
 * 遵循解耦原则：通过 Controller 层操作数据，不直接访问数据库
 */
public class MyRacesActivity extends BaseActivity implements MyRacesAdapter.OnRaceActionListener {

    private ActivityMyRacesBinding binding;
    private MyRacesAdapter ongoingAdapter;
    private MyRacesAdapter completedAdapter;
    private MyRacesAdapter endedAdapter;
    private final List<Race> ongoingRaces = new ArrayList<>();
    private final List<Race> completedRaces = new ArrayList<>();
    private final List<Race> endedRaces = new ArrayList<>();
    private RaceManager raceManager;
    private RaceSignupController signupController;
    private ResultManager resultManager;
    private String currentUserId;
    private int currentTab = 0; // 0: 进行中, 1: 已完成, 2: 未完成

    @Override
    protected int getLayoutId() {
        return 0;
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
        
        binding = ActivityMyRacesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 设置 Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        initView();
        initData();
    }

    @Override
    protected void initView() {
        resultManager = new ResultManager();
        
        // 初始化进行中赛事列表
        ongoingAdapter = new MyRacesAdapter();
        ongoingAdapter.setOnRaceActionListener(this);
        binding.rvOngoingRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOngoingRaces.setAdapter(ongoingAdapter);

        // 初始化已完成赛事列表
        completedAdapter = new MyRacesAdapter();
        completedAdapter.setOnRaceActionListener(this);
        binding.rvCompletedRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCompletedRaces.setAdapter(completedAdapter);

        // 初始化未完成赛事列表
        endedAdapter = new MyRacesAdapter();
        endedAdapter.setOnRaceActionListener(this);
        binding.rvEndedRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEndedRaces.setAdapter(endedAdapter);

        // 设置Tab切换
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("进行中"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("已完成"));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("未完成"));
        
        // 默认选中"进行中"分类（索引0）
        TabLayout.Tab defaultTab = binding.tabLayout.getTabAt(0);
        if (defaultTab != null) {
            defaultTab.select();
            currentTab = 0;
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
        binding.swipeRefresh.setColorSchemeColors(getResources().getColor(com.example.cross_intelligence.R.color.forest_green, getTheme()));
        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadMyRaces();
            binding.swipeRefresh.setRefreshing(false);
        });
    }

    @Override
    protected void initData() {
        raceManager = new RaceManager();
        signupController = new RaceSignupController();
        
        // 获取当前登录用户ID
        currentUserId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(currentUserId)) {
            UIUtil.showToast(this, "请先登录");
            finish();
            return;
        }
        
        // 设置适配器的用户ID
        ongoingAdapter.setCurrentUserId(currentUserId);
        completedAdapter.setCurrentUserId(currentUserId);
        endedAdapter.setCurrentUserId(currentUserId);
        
        loadMyRaces();
    }

    /**
     * 加载已报名的赛事
     * 解耦优化：通过 RaceSignupController 查询，不直接访问 Realm
     */
    private void loadMyRaces() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.layoutEmptyState.setVisibility(View.GONE);
        
        // 【解耦优化】通过 Controller 层查询用户已报名的赛事
        signupController.getRacesForUser(currentUserId, new RaceSignupController.UserRacesCallback() {
            @Override
            public void onLoaded(@NonNull List<Race> races) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    
                    // 清空现有数据
                    int ongoingSize = ongoingRaces.size();
                    int completedSize = completedRaces.size();
                    int endedSize = endedRaces.size();
                    ongoingRaces.clear();
                    completedRaces.clear();
                    endedRaces.clear();
                    if (ongoingSize > 0) {
                        ongoingAdapter.notifyItemRangeRemoved(0, ongoingSize);
                    }
                    if (completedSize > 0) {
                        completedAdapter.notifyItemRangeRemoved(0, completedSize);
                    }
                    if (endedSize > 0) {
                        endedAdapter.notifyItemRangeRemoved(0, endedSize);
                    }
                    
                    if (races.isEmpty()) {
                        binding.layoutEmptyState.setVisibility(View.VISIBLE);
                        updateTabVisibility();
                        return;
                    }
                    
                    binding.layoutEmptyState.setVisibility(View.GONE);
                    
                    // 分类赛事（过滤掉未开始的赛事）
                    Date now = new Date();
                    for (Race race : races) {
                        int status = getRaceStatus(race, now);
                        switch (status) {
                            case 1: // 进行中
                                ongoingRaces.add(race);
                                break;
                            case 2: // 已完成
                                completedRaces.add(race);
                                break;
                            case 3: // 未完成
                                endedRaces.add(race);
                                break;
                            // 状态0（未开始）被过滤掉，不添加到任何列表
                        }
                    }
                    
                    // 按开始时间倒序排序
                    Comparator<Race> timeComparator = (r1, r2) -> {
                        if (r1.getStartTime() != null && r2.getStartTime() != null) {
                            return r2.getStartTime().compareTo(r1.getStartTime());
                        }
                        return 0;
                    };
                    Collections.sort(ongoingRaces, timeComparator);
                    Collections.sort(completedRaces, timeComparator);
                    Collections.sort(endedRaces, timeComparator);
                    
                    // 通知适配器数据已更新
                    if (ongoingRaces.size() > 0) {
                        ongoingAdapter.setRaces(ongoingRaces);
                    }
                    if (completedRaces.size() > 0) {
                        completedAdapter.setRaces(completedRaces);
                    }
                    if (endedRaces.size() > 0) {
                        endedAdapter.setRaces(endedRaces);
                    }
                    
                    updateTabVisibility();
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    UIUtil.showToast(MyRacesActivity.this, "加载失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 判断赛事状态
     * @return 1: 进行中, 2: 已完成, 3: 未完成
     * 注意：未开始的赛事（状态0）已被过滤，不会出现在"我的赛事"列表中
     */
    private int getRaceStatus(Race race, Date now) {
        boolean isCompleted = isRaceCompleted(race);
        
        if (race.getStartTime() == null || race.getEndTime() == null) {
            return isCompleted ? 2 : 1; // 已完成或进行中
        }
        
        if (now.before(race.getStartTime())) {
            // 赛事未开始（这种情况理论上不应该出现，因为未开始的赛事不可报名）
            return 0; // 未开始（会被过滤掉）
        } else if (now.after(race.getEndTime())) {
            // 赛事已结束
            return isCompleted ? 2 : 3; // 已完成或未完成
        } else {
            // 赛事进行中
            return isCompleted ? 2 : 1; // 已完成或进行中
        }
    }

    /**
     * 检查用户是否完成赛事
     */
    private boolean isRaceCompleted(Race race) {
        Realm realm = Realm.getDefaultInstance();
        try {
            Result result = realm.where(Result.class)
                    .equalTo("raceId", race.getRaceId())
                    .equalTo("userId", currentUserId)
                    .findFirst();
            
            return result != null && result.getStatus() != Result.Status.DNF;
        } finally {
            realm.close();
        }
    }

    /**
     * 根据当前Tab更新显示内容
     */
    private void updateTabVisibility() {
        // 先隐藏所有
        binding.rvOngoingRaces.setVisibility(View.GONE);
        binding.rvCompletedRaces.setVisibility(View.GONE);
        binding.rvEndedRaces.setVisibility(View.GONE);
        binding.tvOngoingEmpty.setVisibility(View.GONE);
        binding.tvCompletedEmpty.setVisibility(View.GONE);
        binding.tvEndedEmpty.setVisibility(View.GONE);

        if (currentTab == 0) {
            // 显示进行中
            if (ongoingRaces.isEmpty()) {
                binding.tvOngoingEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvOngoingRaces.setVisibility(View.VISIBLE);
            }
        } else if (currentTab == 1) {
            // 显示已完成
            if (completedRaces.isEmpty()) {
                binding.tvCompletedEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvCompletedRaces.setVisibility(View.VISIBLE);
            }
        } else {
            // 显示未完成
            if (endedRaces.isEmpty()) {
                binding.tvEndedEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvEndedRaces.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onCheckInClick(Race race) {
        // 跳转到打卡页面，传递赛事ID
        Intent intent = new Intent(this, CheckInActivity.class);
        intent.putExtra("raceId", race.getRaceId());
        startActivity(intent);
    }

    @Override
    public void onRaceClick(Race race) {
        // 跳转到赛事详情页
        Intent intent = new Intent(this, RaceDetailActivity.class);
        intent.putExtra("raceId", race.getRaceId());
        startActivity(intent);
    }
}

