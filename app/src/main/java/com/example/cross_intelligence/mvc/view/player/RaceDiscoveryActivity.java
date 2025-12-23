package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cross_intelligence.databinding.ActivityRaceDiscoveryBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.race.RaceDetailActivity;

import java.util.List;

/**
 * 赛事大厅：选手浏览和报名赛事
 */
public class RaceDiscoveryActivity extends BaseActivity implements RaceDiscoveryAdapter.OnRaceActionListener {

    private ActivityRaceDiscoveryBinding binding;
    private RaceDiscoveryAdapter adapter;
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
        binding = ActivityRaceDiscoveryBinding.inflate(getLayoutInflater());
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
        adapter = new RaceDiscoveryAdapter();
        adapter.setOnRaceActionListener(this);
        binding.rvRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRaces.setAdapter(adapter);
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
        
        // 传递报名控制器和用户ID给适配器
        adapter.setSignupInfo(signupController, currentUserId);
        
        loadRaces();
    }

    /**
     * 加载所有赛事
     */
    private void loadRaces() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyHint.setVisibility(View.GONE);
        
        raceManager.getAllRaces(new RaceManager.RaceListCallback() {
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
        });
    }

    @Override
    public void onSignupClick(Race race) {
        // 检查赛事是否已结束
        if (isRaceEnded(race)) {
            UIUtil.showToast(this, "该赛事已结束，无法报名");
            return;
        }
        
        // 检查是否已报名
        if (signupController.isUserSignedUp(currentUserId, race.getRaceId())) {
            UIUtil.showToast(this, "已报名");
            return;
        }
        
        // 显示报名确认对话框
        new AlertDialog.Builder(this)
                .setTitle("确认报名")
                .setMessage("是否报名参加「" + race.getName() + "」？")
                .setPositiveButton("确定", (dialog, which) -> {
                    performSignup(race);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /**
     * 检查赛事是否已结束
     */
    private boolean isRaceEnded(Race race) {
        if (race.getEndTime() == null) {
            return false;
        }
        java.util.Date now = new java.util.Date();
        return race.getEndTime().before(now);
    }

    @Override
    public void onRaceClick(Race race) {
        // 跳转到赛事详情页
        Intent intent = new Intent(this, RaceDetailActivity.class);
        intent.putExtra("raceId", race.getRaceId());
        startActivity(intent);
    }

    /**
     * 执行报名操作
     */
    private void performSignup(Race race) {
        boolean success = signupController.signupRace(currentUserId, race.getRaceId());
        if (success) {
            UIUtil.showToast(this, "已报名");
            // 刷新列表以更新报名状态和排序
            loadRaces();
        } else {
            UIUtil.showToast(this, "报名失败，请稍后重试");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新列表以更新报名状态
        if (adapter != null && adapter.getItemCount() > 0) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (signupController != null) {
            signupController.close();
        }
    }
}


