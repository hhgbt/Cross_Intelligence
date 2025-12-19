package com.example.cross_intelligence.mvc.view.admin;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityAdminMainBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.race.CreateRaceActivity;
import com.example.cross_intelligence.mvc.view.admin.RaceListActivity;

/**
 * 管理员主页：提供管理员常用功能入口
 */
public class AdminMainActivity extends BaseActivity {

    private ActivityAdminMainBinding binding;

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding inflate
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置沉浸式状态栏
        setupImmersiveStatusBar();
        
        binding = ActivityAdminMainBinding.inflate(getLayoutInflater());
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
        // 创建赛事按钮
        binding.cardCreateRace.setOnClickListener(v -> {
            Intent intent = new Intent(AdminMainActivity.this, CreateRaceActivity.class);
            startActivity(intent);
        });

        // 查看赛事按钮
        binding.cardViewRaces.setOnClickListener(v -> {
            Intent intent = new Intent(AdminMainActivity.this, RaceListActivity.class);
            startActivity(intent);
        });

        // 选手成绩按钮
        binding.cardPlayerRanking.setOnClickListener(v -> {
            // TODO: 后续实现选手成绩功能
            UIUtil.showToast(this, "选手成绩功能开发中，敬请期待");
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
        
        // 赛事数量
        int raceCount = getRaceCount();
        binding.tvRaceCount.setText(String.valueOf(raceCount));
        
        // 打卡点数量
        int checkpointCount = getCheckpointCount();
        binding.tvCheckpointCount.setText(String.valueOf(checkpointCount));
    }

    /**
     * 加载并显示账号名称
     */
    private void loadAccountName() {
        String accountName = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
        if (!android.text.TextUtils.isEmpty(accountName)) {
            binding.tvWelcomeTitle.setText(accountName + "，你好！");
        } else {
            binding.tvWelcomeTitle.setText("管理员，你好！");
        }
    }

    /**
     * 获取赛事数量（仅统计当前管理员创建的赛事）
     */
    private int getRaceCount() {
        try {
            // 获取当前登录的管理员账号
            String currentAdminId = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
            if (android.text.TextUtils.isEmpty(currentAdminId)) {
                android.util.Log.w("AdminMainActivity", "Current admin ID is empty");
                return 0;
            }
            
            io.realm.Realm realm = io.realm.Realm.getDefaultInstance();
            try {
                // 只统计当前管理员创建的赛事
                return (int) realm.where(com.example.cross_intelligence.mvc.model.Race.class)
                        .equalTo("organizerId", currentAdminId)
                        .count();
            } finally {
                realm.close();
            }
        } catch (Exception e) {
            android.util.Log.e("AdminMainActivity", "Failed to load race count", e);
            return 0;
        }
    }

    /**
     * 获取打卡点数量（仅统计当前管理员赛事下的打卡点）
     */
    private int getCheckpointCount() {
        try {
            // 获取当前登录的管理员账号
            String currentAdminId = com.example.cross_intelligence.mvc.util.PreferenceUtil.getString(this, "account", "");
            if (android.text.TextUtils.isEmpty(currentAdminId)) {
                android.util.Log.w("AdminMainActivity", "Current admin ID is empty");
                return 0;
            }
            
            io.realm.Realm realm = io.realm.Realm.getDefaultInstance();
            try {
                // 获取当前管理员创建的所有赛事
                io.realm.RealmResults<com.example.cross_intelligence.mvc.model.Race> myRaces = 
                    realm.where(com.example.cross_intelligence.mvc.model.Race.class)
                        .equalTo("organizerId", currentAdminId)
                        .findAll();
                
                // 统计所有赛事下的打卡点总数
                int totalCheckpoints = 0;
                for (com.example.cross_intelligence.mvc.model.Race race : myRaces) {
                    if (race.getCheckPoints() != null) {
                        totalCheckpoints += race.getCheckPoints().size();
                    }
                }
                
                return totalCheckpoints;
            } finally {
                realm.close();
            }
        } catch (Exception e) {
            android.util.Log.e("AdminMainActivity", "Failed to load checkpoint count", e);
            return 0;
        }
    }
}

