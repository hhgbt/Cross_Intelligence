package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityPlayerMainBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.checkin.CheckInActivity;

/**
 * 选手主页：提供选手常用功能入口
 */
public class PlayerMainActivity extends BaseActivity {

    private ActivityPlayerMainBinding binding;

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
                decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
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
        // 加载统计数据
        loadStatistics();
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
            String currentUserId = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
            if (android.text.TextUtils.isEmpty(currentUserId)) {
                android.util.Log.w("PlayerMainActivity", "Current user ID is empty");
                return 0;
            }
            
            io.realm.Realm realm = io.realm.Realm.getDefaultInstance();
            try {
                // 统计当前选手的报名记录
                return (int) realm.where(com.example.cross_intelligence.mvc.model.RaceSignup.class)
                        .equalTo("userId", currentUserId)
                        .count();
            } finally {
                realm.close();
            }
        } catch (Exception e) {
            android.util.Log.e("PlayerMainActivity", "Failed to load registered race count", e);
            return 0;
        }
    }

    /**
     * 获取已完成赛事数量（完成所有打卡并获得成绩）
     */
    private int getFinishedRaceCount() {
        try {
            // 获取当前登录的选手账号
            String currentUserId = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
            if (android.text.TextUtils.isEmpty(currentUserId)) {
                android.util.Log.w("PlayerMainActivity", "Current user ID is empty");
                return 0;
            }
            
            io.realm.Realm realm = io.realm.Realm.getDefaultInstance();
            try {
                // 统计当前选手状态为 FINISHED 的成绩记录
                return (int) realm.where(com.example.cross_intelligence.mvc.model.Result.class)
                        .equalTo("userId", currentUserId)
                        .equalTo("status", com.example.cross_intelligence.mvc.model.Result.Status.FINISHED.name())
                        .count();
            } finally {
                realm.close();
            }
        } catch (Exception e) {
            android.util.Log.e("PlayerMainActivity", "Failed to load finished race count", e);
            return 0;
        }
    }
}

