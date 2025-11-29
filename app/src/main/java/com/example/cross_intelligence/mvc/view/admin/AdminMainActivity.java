package com.example.cross_intelligence.mvc.view.admin;

import android.content.Intent;
import android.os.Bundle;

import com.example.cross_intelligence.databinding.ActivityAdminMainBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.race.CreateRaceActivity;

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
        binding = ActivityAdminMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
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
            // TODO: 后续实现查看赛事功能
            UIUtil.showToast(this, "查看赛事功能开发中，敬请期待");
        });

        // 选手排名按钮
        binding.cardPlayerRanking.setOnClickListener(v -> {
            // TODO: 后续实现选手排名功能
            UIUtil.showToast(this, "选手排名功能开发中，敬请期待");
        });
    }

    @Override
    protected void initData() {
        // 可以在这里加载管理员相关信息
    }
}

