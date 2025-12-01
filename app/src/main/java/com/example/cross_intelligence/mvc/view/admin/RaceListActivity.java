package com.example.cross_intelligence.mvc.view.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.databinding.ActivityRaceListBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.race.CreateRaceActivity;
import com.example.cross_intelligence.mvc.view.race.RaceDetailActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 管理员赛事列表页面：显示该管理员创建的所有赛事
 */
public class RaceListActivity extends BaseActivity {

    private ActivityRaceListBinding binding;
    private RaceManager raceManager;
    private RaceAdapter adapter;
    private List<Race> raceList = new ArrayList<>();
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRaceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        raceManager = new RaceManager();
        initView();
        initData();
    }

    @Override
    protected void initView() {
        // 设置工具栏返回按钮
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 初始化 RecyclerView
        adapter = new RaceAdapter(raceList, dateTimeFormat, race -> {
            // 点击赛事，跳转到详情页
            Intent intent = new Intent(RaceListActivity.this, RaceDetailActivity.class);
            intent.putExtra("raceId", race.getRaceId());
            startActivity(intent);
        }, race -> {
            // 编辑赛事
            Intent intent = new Intent(RaceListActivity.this, CreateRaceActivity.class);
            intent.putExtra("raceId", race.getRaceId());
            intent.putExtra("mode", "edit");
            startActivity(intent);
        }, race -> {
            // 删除赛事
            showDeleteConfirmDialog(race);
        });
        binding.rvRaces.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRaces.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        // 获取当前登录的管理员账号
        String organizerId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(organizerId)) {
            UIUtil.showToast(this, "请先登录");
            finish();
            return;
        }

        // 查询该管理员创建的所有赛事
        raceManager.queryRacesByOrganizer(organizerId, races -> {
            runOnUiThread(() -> {
                raceList.clear();
                raceList.addAll(races);
                adapter.notifyDataSetChanged();
                // 显示/隐藏空状态
                binding.tvEmpty.setVisibility(raceList.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时刷新列表
        initData();
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmDialog(Race race) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除赛事 \"" + race.getName() + "\" 吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    raceManager.deleteRace(race.getRaceId());
                    UIUtil.showToast(this, "赛事已删除");
                    // 刷新列表
                    initData();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 赛事列表适配器回调接口
     */
    private interface OnRaceClickListener {
        void onRaceClick(@NonNull Race race);
    }

    private interface OnRaceEditListener {
        void onRaceEdit(@NonNull Race race);
    }

    private interface OnRaceDeleteListener {
        void onRaceDelete(@NonNull Race race);
    }

    /**
     * 赛事列表适配器
     */
    private static class RaceAdapter extends RecyclerView.Adapter<RaceAdapter.RaceViewHolder> {

        private final List<Race> data;
        private final SimpleDateFormat dateTimeFormat;
        private final OnRaceClickListener clickListener;
        private final OnRaceEditListener editListener;
        private final OnRaceDeleteListener deleteListener;

        RaceAdapter(List<Race> data, SimpleDateFormat dateTimeFormat, OnRaceClickListener clickListener,
                   OnRaceEditListener editListener, OnRaceDeleteListener deleteListener) {
            this.data = data;
            this.dateTimeFormat = dateTimeFormat;
            this.clickListener = clickListener;
            this.editListener = editListener;
            this.deleteListener = deleteListener;
        }

        @NonNull
        @Override
        public RaceViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(com.example.cross_intelligence.R.layout.item_race, parent, false);
            return new RaceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RaceViewHolder holder, int position) {
            Race race = data.get(position);
            // 显示序号和赛事名称（格式：序号. 赛事名称）
            int seqNum = race.getSequenceNumber();
            String displayName;
            if (seqNum > 0) {
                displayName = seqNum + ". " + race.getName();
            } else {
                // 如果序号为0或未设置，使用位置+1作为临时序号
                displayName = (position + 1) + ". " + race.getName();
            }
            holder.tvRaceName.setText(displayName);

            // 显示起点和终点
            String routeText = getRouteText(race);
            holder.tvRoute.setText(routeText);

            // 显示时间范围
            String timeText = getTimeText(race);
            holder.tvTime.setText(timeText);

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onRaceClick(race);
                }
            });

            holder.btnEdit.setOnClickListener(v -> {
                if (editListener != null) {
                    editListener.onRaceEdit(race);
                }
            });

            holder.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onRaceDelete(race);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private String getRouteText(Race race) {
            List<CheckPoint> points = race.getCheckPoints();
            if (points == null || points.isEmpty()) {
                return "暂无打卡点";
            }

            String startPoint = null;
            String endPoint = null;

            for (CheckPoint point : points) {
                if ("起点".equals(point.getType())) {
                    startPoint = point.getName();
                } else if ("终点".equals(point.getType())) {
                    endPoint = point.getName();
                }
            }

            if (startPoint != null && endPoint != null) {
                return startPoint + " → " + endPoint;
            } else if (startPoint != null) {
                return startPoint + " → ...";
            } else if (endPoint != null) {
                return "... → " + endPoint;
            } else {
                return "检查点路线";
            }
        }

        private String getTimeText(Race race) {
            Date start = race.getStartTime();
            Date end = race.getEndTime();
            if (start != null && end != null) {
                return dateTimeFormat.format(start) + " - " + dateTimeFormat.format(end);
            } else if (start != null) {
                return dateTimeFormat.format(start) + " - ";
            } else {
                return "";
            }
        }

        class RaceViewHolder extends RecyclerView.ViewHolder {
            final TextView tvRaceName;
            final TextView tvRoute;
            final TextView tvTime;
            final ImageButton btnEdit;
            final ImageButton btnDelete;

            RaceViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                tvRaceName = itemView.findViewById(com.example.cross_intelligence.R.id.tvRaceName);
                tvRoute = itemView.findViewById(com.example.cross_intelligence.R.id.tvRoute);
                tvTime = itemView.findViewById(com.example.cross_intelligence.R.id.tvTime);
                btnEdit = itemView.findViewById(com.example.cross_intelligence.R.id.btnEdit);
                btnDelete = itemView.findViewById(com.example.cross_intelligence.R.id.btnDelete);
            }
        }
    }
}

