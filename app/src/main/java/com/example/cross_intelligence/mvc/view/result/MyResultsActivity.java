package com.example.cross_intelligence.mvc.view.result;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.cross_intelligence.databinding.ActivityMyResultsBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的成绩页面 - 显示当前用户的所有比赛成绩
 */
public class MyResultsActivity extends BaseActivity implements ResultAdapter.OnResultClickListener {

    private ActivityMyResultsBinding binding;
    private final List<Result> resultList = new ArrayList<>();
    private ResultAdapter adapter;
    private ResultManager resultManager;
    private String currentUserId;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("我的成绩");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // 设置列表
        adapter = new ResultAdapter(resultList, this);
        binding.rvMyResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMyResults.setAdapter(adapter);
        
        // 刷新按钮
        binding.btnRefresh.setOnClickListener(v -> loadMyResults());
        
        binding.progressMyResults.setVisibility(View.VISIBLE);
    }

    @Override
    protected void initData() {
        resultManager = new ResultManager();
        
        // 获取当前用户ID
        currentUserId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(currentUserId)) {
            UIUtil.showToast(this, "请先登录");
            finish();
            return;
        }
        
        loadMyResults();
    }

    /**
     * 加载我的成绩列表
     */
    private void loadMyResults() {
        binding.progressMyResults.setVisibility(View.VISIBLE);
        
        // 清空现有数据
        int previousSize = resultList.size();
        resultList.clear();
        if (previousSize > 0) {
            adapter.notifyItemRangeRemoved(0, previousSize);
        }
        
        // 查询该用户的所有成绩
        List<Result> results = resultManager.loadResultsByUserId(currentUserId);
        
        if (results.isEmpty()) {
            binding.progressMyResults.setVisibility(View.GONE);
            binding.tvEmptyHint.setVisibility(View.VISIBLE);
            binding.tvEmptyHint.setText("暂无成绩记录\n完成比赛后成绩会自动保存在这里");
            UIUtil.showToast(this, "暂无成绩");
        } else {
            resultList.addAll(results);
            adapter.notifyItemRangeInserted(0, results.size());
            binding.progressMyResults.setVisibility(View.GONE);
            binding.tvEmptyHint.setVisibility(View.GONE);
            
            UIUtil.showToast(this, "已加载 " + results.size() + " 条成绩");
        }
    }

    @Override
    public void onResultClick(Result result) {
        // 点击成绩项，跳转到详情页面
        Intent intent = new Intent(this, ResultDetailActivity.class);
        intent.putExtra(ResultDetailActivity.EXTRA_RESULT_ID, result.getResultId());
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}


