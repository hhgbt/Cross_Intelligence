package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityPlayerMainBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.checkin.CheckInActivity;

/**
 * 选手主页：提供选手常用功能入口
 */
public class PlayerMainActivity extends BaseActivity {

    private ActivityPlayerMainBinding binding;
    private RaceSignupController signupController;
    private ResultManager resultManager;

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding inflate
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置沉浸式状态栏
        setupImmersiveStatusBar();
        
        binding = ActivityPlayerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }
    
    /**
     * 设置沉浸式状态栏
     */
    private void setupImmersiveStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            
            // 设置状态栏颜色为背景色
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.white));
            
            // Android 6.0+ 支持浅色状态栏（深色图标）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                // 使用新的 WindowInsetsController API (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.getInsetsController().setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // 兼容旧版本 (Android 6.0 - 10)
                    decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
                }
            }
        }
    }

    @Override
    protected void initView() {
        // 报名赛事按钮 - 跳转到赛事大厅
        binding.cardRegisterRace.setOnClickListener(v -> {
            Intent intent = new Intent(PlayerMainActivity.this, RaceDiscoveryActivity.class);
            startActivity(intent);
        });

        // 我的赛事按钮 - 跳转到我的赛事页面
        binding.cardMyRaces.setOnClickListener(v -> {
            Intent intent = new Intent(PlayerMainActivity.this, MyRacesActivity.class);
            startActivity(intent);
        });

        // 我的成绩按钮 - 跳转到我的成绩页面
        binding.cardMyResults.setOnClickListener(v -> {
            Intent intent = new Intent(PlayerMainActivity.this, com.example.cross_intelligence.mvc.view.result.MyResultsActivity.class);
            startActivity(intent);
        });
        
    }

    @Override
    protected void initData() {
        signupController = new RaceSignupController();
        resultManager = new ResultManager();
        // 加载统计数据
        loadStatistics();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时刷新统计数据
        loadStatistics();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (signupController != null) {
            signupController.close();
        }
    }

    /**
     * 加载统计数据
     */
    private void loadStatistics() {
        // 加载账号名称
        loadAccountName();
        
        // 已报名赛事数量
        int registeredCount = getRegisteredRaceCount();
        binding.tvRegisteredRaceCount.setText(String.valueOf(registeredCount));
        
        // 已完成赛事数量
        int finishedCount = getFinishedRaceCount();
        binding.tvFinishedRaceCount.setText(String.valueOf(finishedCount));
    }

    /**
     * 加载并显示账号名称
     */
    private void loadAccountName() {
        String accountName = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
        if (!android.text.TextUtils.isEmpty(accountName)) {
            binding.tvWelcomeTitle.setText(accountName + "，你好！");
        } else {
            binding.tvWelcomeTitle.setText("选手，你好！");
        }
    }

    /**
     * 获取已报名赛事数量
     */
    private int getRegisteredRaceCount() {
        try {
            // 获取当前登录的选手账号
            String currentUserId = PreferenceUtil.getString(this, "account", "");
            if (android.text.TextUtils.isEmpty(currentUserId)) {
                android.util.Log.w("PlayerMainActivity", "Current user ID is empty");
                return 0;
            }
            
            // 使用 RaceSignupController 查询已报名赛事ID列表，返回数量
            if (signupController != null) {
                java.util.List<String> signedUpRaceIds = signupController.getUserSignedUpRaceIds(currentUserId);
                return signedUpRaceIds != null ? signedUpRaceIds.size() : 0;
            }
            return 0;
        } catch (Exception e) {
            android.util.Log.e("PlayerMainActivity", "Failed to load registered race count", e);
            return 0;
        }
    }

    /**
     * 获取已完成赛事数量（完成所有打卡并获得成绩，状态不是DNF）
     */
    private int getFinishedRaceCount() {
        try {
            // 获取当前登录的选手账号
            String currentUserId = PreferenceUtil.getString(this, "account", "");
            if (android.text.TextUtils.isEmpty(currentUserId)) {
                android.util.Log.w("PlayerMainActivity", "Current user ID is empty");
                return 0;
            }
            
            // 使用 ResultManager 查询已完成成绩（排除DNF状态）
            if (resultManager != null) {
                java.util.List<Result> results = resultManager.loadResultsByUserId(currentUserId);
                int count = 0;
                for (Result result : results) {
                    // 已完成：状态不是DNF
                    if (result.getStatus() != null && result.getStatus() != Result.Status.DNF) {
                        count++;
                    }
                }
                return count;
            }
            return 0;
        } catch (Exception e) {
            android.util.Log.e("PlayerMainActivity", "Failed to load finished race count", e);
            return 0;
        }
    }
}

