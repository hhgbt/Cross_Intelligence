package com.example.cross_intelligence.mvc.view.player;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityRaceDiscoveryBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.race.RaceDetailActivity;

import java.util.List;
import java.util.ArrayList;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * 赛事大厅：选手浏览和报名赛事
 */
public class RaceDiscoveryActivity extends BaseActivity implements RaceDiscoveryAdapter.OnRaceActionListener {

    private ActivityRaceDiscoveryBinding binding;
    private RaceDiscoveryAdapter adapter;
    private RaceManager raceManager;
    private RaceSignupController signupController;
    private String currentUserId;
    
    // Realm 相关
    private Realm realm;
    private RealmResults<Race> raceResults;
    private RealmChangeListener<RealmResults<Race>> raceChangeListener;

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
        
        // 设置下拉刷新
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.forest_green);
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshRacesFromCloud);
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
        
        // 初始化 Realm 并设置监听
        setupRealmListener();
        
        // 首次进入自动从云端拉取
        binding.swipeRefreshLayout.setRefreshing(true);
        refreshRacesFromCloud();
    }
    
    /**
     * 设置 Realm 监听器，实现实时更新
     */
    private void setupRealmListener() {
        realm = Realm.getDefaultInstance();
        
        // 查询所有赛事，按创建时间倒序
        raceResults = realm.where(Race.class)
                .findAllAsync(); // 异步查询
                
        // 设置监听器
        raceChangeListener = results -> {
            updateList(results);
        };
        
        raceResults.addChangeListener(raceChangeListener);
    }
    
    /**
     * 更新列表显示
     */
    private void updateList(RealmResults<Race> races) {
        if (races.isEmpty()) {
            binding.tvEmptyHint.setVisibility(View.VISIBLE);
            adapter.setRaces(new ArrayList<>());
        } else {
            binding.tvEmptyHint.setVisibility(View.GONE);
            // 复制一份数据给 Adapter（脱离 Realm 线程限制）
            // 显式指定泛型，避免歧义
            List<Race> raceList = realm.copyFromRealm((Iterable<Race>) races);
            adapter.setRaces(raceList);
        }
        // 隐藏加载条
        binding.progressBar.setVisibility(View.GONE);
    }

    /**
     * 从云端拉取最新数据
     */
    private void refreshRacesFromCloud() {
        // 调用 RaceManager 的云端拉取方法
        // 注意：这里我们传入 null，表示拉取所有管理员创建的赛事
        raceManager.fetchRacesFromCloud(null);
        
        // 由于 fetchRacesFromCloud 是异步且没有回调的，我们延迟一会停止刷新动画
        // 实际的数据更新会通过 RealmChangeListener 自动触发
        binding.getRoot().postDelayed(() -> {
            binding.swipeRefreshLayout.setRefreshing(false);
        }, 2000);
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
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
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
        
        // 关闭 Realm 相关资源
        if (raceResults != null && raceChangeListener != null) {
            raceResults.removeChangeListener(raceChangeListener);
        }
        if (realm != null && !realm.isClosed()) {
            realm.close();
        }
    }
}


