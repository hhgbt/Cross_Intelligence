package com.example.cross_intelligence.mvc.view.result;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityResultDetailBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.ResultExportUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.io.IOException;
import java.util.Collections;

public class ResultDetailActivity extends BaseActivity {

    public static final String EXTRA_RESULT_ID = "extra_result_id";

    private ActivityResultDetailBinding binding;
    private Result currentResult;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        binding.btnShare.setOnClickListener(v -> shareResult());
        binding.btnExportSingle.setOnClickListener(v -> exportResult());
    }

    @Override
    protected void initData() {
        ResultManager resultManager = new ResultManager();
        String resultId = getIntent().getStringExtra(EXTRA_RESULT_ID);
        if (TextUtils.isEmpty(resultId)) {
            UIUtil.showToast(this, "缺少成绩ID");
            finish();
            return;
        }
        currentResult = resultManager.loadResultById(resultId);
        if (currentResult == null) {
            UIUtil.showToast(this, "成绩不存在");
            finish();
            return;
        }
        renderResult(currentResult);
    }

    private void renderResult(Result result) {
        binding.tvUser.setText(getString(R.string.result_user_format, result.getUserId()));
        binding.tvRank.setText(result.getRank() > 0
                ? getString(R.string.result_rank_full_format, result.getRank())
                : getString(R.string.result_rank_dnf));
        binding.tvElapsed.setText(getString(R.string.result_elapsed_format, result.getElapsedSeconds()));
        binding.tvPenalty.setText(getString(R.string.result_penalty_format, result.getPenaltySeconds()));
        binding.tvTotal.setText(getString(R.string.result_total_format, result.getTotalSeconds()));
        binding.tvStatus.setText(getString(R.string.result_status_format, result.getStatus()));
    }

    private void shareResult() {
        if (currentResult == null) return;
        String shareText = "选手 " + currentResult.getUserId() + " 用时 "
                + currentResult.getTotalSeconds() + " 秒，状态：" + currentResult.getStatus();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "分享成绩"));
    }

    private void exportResult() {
        if (currentResult == null) return;
        try {
            Uri uri = ResultExportUtil.exportToFile(this,
                    Collections.singletonList(currentResult),
                    "result_" + currentResult.getUserId() + ".csv");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出成绩"));
        } catch (IOException e) {
            UIUtil.showToast(this, "导出失败：" + e.getMessage());
        }
    }
}


