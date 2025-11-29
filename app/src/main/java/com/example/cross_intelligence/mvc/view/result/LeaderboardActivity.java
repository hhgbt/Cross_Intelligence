package com.example.cross_intelligence.mvc.view.result;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cross_intelligence.databinding.ActivityLeaderboardBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.ResultExportUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends BaseActivity implements ResultAdapter.OnResultClickListener {

    public static final String EXTRA_RACE_ID = "extra_race_id";

    private ActivityLeaderboardBinding binding;
    private final List<Result> resultList = new ArrayList<>();
    private ResultAdapter adapter;
    private ResultManager resultManager;
    private String raceId;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLeaderboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        adapter = new ResultAdapter(resultList, this);
        binding.rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLeaderboard.setAdapter(adapter);
        binding.btnExport.setOnClickListener(v -> exportAll());
        binding.progressLeaderboard.setVisibility(View.VISIBLE);
    }

    @Override
    protected void initData() {
        resultManager = new ResultManager();
        raceId = getIntent().getStringExtra(EXTRA_RACE_ID);
        if (TextUtils.isEmpty(raceId)) {
            UIUtil.showToast(this, "缺少赛事ID");
            finish();
            return;
        }
        loadResults();
    }

    private void loadResults() {
        int previousSize = resultList.size();
        resultList.clear();
        List<Result> results = resultManager.loadResults(raceId);
        resultList.addAll(resultManager.rankResults(results));
        if (previousSize > 0) {
            adapter.notifyItemRangeRemoved(0, previousSize);
        }
        if (!resultList.isEmpty()) {
            adapter.notifyItemRangeInserted(0, resultList.size());
        }
        binding.progressLeaderboard.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(resultList.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvLeaderboard.setVisibility(resultList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void exportAll() {
        if (resultList.isEmpty()) {
            UIUtil.showToast(this, "暂无成绩");
            return;
        }
        try {
            Uri uri = ResultExportUtil.exportToFile(this, resultList, "leaderboard_" + raceId + ".csv");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出排行榜"));
        } catch (IOException e) {
            UIUtil.showToast(this, "导出失败：" + e.getMessage());
        }
    }

    @Override
    public void onResultClick(@NonNull Result result) {
        Intent intent = new Intent(this, ResultDetailActivity.class);
        intent.putExtra(ResultDetailActivity.EXTRA_RESULT_ID, result.getResultId());
        startActivity(intent);
    }
}

