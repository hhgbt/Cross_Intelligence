package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Bundle;

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
        binding = ActivityPlayerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 报名赛事按钮
        binding.cardRegisterRace.setOnClickListener(v -> {
            // TODO: 后续实现报名赛事功能
            UIUtil.showToast(this, "报名赛事功能开发中，敬请期待");
        });

        // 我的赛事按钮
        binding.cardMyRaces.setOnClickListener(v -> {
            // 跳转到定位地图页面（打卡页面）
            Intent intent = new Intent(PlayerMainActivity.this, CheckInActivity.class);
            startActivity(intent);
        });

        // 我的成绩按钮
        binding.cardMyResults.setOnClickListener(v -> {
            // TODO: 后续实现我的成绩功能
            UIUtil.showToast(this, "我的成绩功能开发中，敬请期待");
        });
    }

    @Override
    protected void initData() {
        // 可以在这里加载选手相关信息
    }
}

