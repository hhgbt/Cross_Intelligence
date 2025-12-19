package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cross_intelligence.databinding.ActivityMyRacesBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.RaceSignup;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.checkin.CheckInActivity;
import com.example.cross_intelligence.mvc.view.race.RaceDetailActivity;

import java.util.ArrayList;
import java.util.List;

import com.example.cross_intelligence.mvc.controller.RaceSignupController;

/**
 * 我的赛事：显示已报名的赛事列表
 * 遵循解耦原则：通过 Controller 层操作数据，不直接访问数据库
 */
public class MyRacesActivity extends BaseActivity implements MyRacesAdapter.OnRaceActionListener {

    private ActivityMyRacesBinding binding;
    private MyRacesAdapter adapter;
    private RaceManager raceManager;
    private RaceSignupController signupController;
    private String currentUserId;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        adapter = new MyRacesAdapter();
        adapter.setOnRaceActionListener(this);
        binding.rvMyRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMyRaces.setAdapter(adapter);
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
        
        loadMyRaces();
    }

    /**
     * 加载已报名的赛事
     * 解耦优化：通过 RaceSignupController 查询，不直接访问 Realm
     */
    private void loadMyRaces() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyHint.setVisibility(View.GONE);
        
        // 【解耦优化】通过 Controller 层查询用户已报名的赛事
        signupController.getRacesForUser(currentUserId, new RaceSignupController.UserRacesCallback() {
            @Override
            public void onLoaded(@NonNull List<Race> races) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (races.isEmpty()) {
                        binding.tvEmptyHint.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmptyHint.setVisibility(View.GONE);
                        adapter.setRaces(races);
                    }
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvEmptyHint.setVisibility(View.VISIBLE);
                    UIUtil.showToast(MyRacesActivity.this, "加载失败: " + e.getMessage());
                });
            }
        });
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

