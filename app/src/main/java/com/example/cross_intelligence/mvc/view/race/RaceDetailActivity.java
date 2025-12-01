package com.example.cross_intelligence.mvc.view.race;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.databinding.ActivityRaceDetailBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 赛事详情页面：显示赛事的完整信息
 */
public class RaceDetailActivity extends BaseActivity {

    private ActivityRaceDetailBinding binding;
    private RaceManager raceManager;
    private CheckpointAdapter adapter;
    private List<CheckPoint> checkPoints = new ArrayList<>();
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRaceDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        raceManager = new RaceManager();
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置工具栏返回按钮
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 初始化打卡点列表（只读模式，不显示删除按钮）
        adapter = new CheckpointAdapter(checkPoints, null);
        binding.rvCheckpoints.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCheckpoints.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        String raceId = getIntent().getStringExtra("raceId");
        if (raceId == null) {
            UIUtil.showToast(this, "赛事ID不存在");
            finish();
            return;
        }

        Race race = raceManager.getRaceById(raceId);
        if (race == null) {
            UIUtil.showToast(this, "赛事不存在");
            finish();
            return;
        }

        // 显示基本信息
        binding.tvRaceName.setText(race.getName());
        String description = race.getDescription();
        if (description != null && !description.isEmpty()) {
            binding.tvDescription.setText(description);
        } else {
            binding.tvDescription.setText("暂无描述");
        }

        // 显示时间范围
        String timeRange = formatTimeRange(race);
        binding.tvTimeRange.setText(timeRange);

        // 加载打卡点
        if (race.getCheckPoints() != null) {
            checkPoints.clear();
            checkPoints.addAll(race.getCheckPoints());
            // 按顺序排序
            checkPoints.sort(Comparator.comparingInt(CheckPoint::getOrderIndex));
            adapter.notifyDataSetChanged();
        }
    }

    private String formatTimeRange(Race race) {
        if (race.getStartTime() != null && race.getEndTime() != null) {
            return dateTimeFormat.format(race.getStartTime()) + " - " + dateTimeFormat.format(race.getEndTime());
        } else if (race.getStartTime() != null) {
            return dateTimeFormat.format(race.getStartTime()) + " - 未设置";
        } else {
            return "时间未设置";
        }
    }
}

