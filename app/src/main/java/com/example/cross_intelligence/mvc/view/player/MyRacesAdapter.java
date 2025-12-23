package com.example.cross_intelligence.mvc.view.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.controller.ResultManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.realm.Realm;

/**
 * 我的赛事适配器：显示已报名的赛事
 * 支持分类：进行中、已完成、已结束（未完成）
 */
public class MyRacesAdapter extends RecyclerView.Adapter<MyRacesAdapter.RaceViewHolder> {

    private final List<Race> items = new ArrayList<>();
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);
    private OnRaceActionListener listener;
    private String currentUserId;
    private ResultManager resultManager;

    public interface OnRaceActionListener {
        void onCheckInClick(Race race);
        void onRaceClick(Race race);
    }

    public void setOnRaceActionListener(OnRaceActionListener listener) {
        this.listener = listener;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        this.resultManager = new ResultManager();
    }

    public void setRaces(@NonNull List<Race> races) {
        items.clear();
        items.addAll(races);
        notifyDataSetChanged();
    }


    /**
     * 检查用户是否完成赛事
     */
    private boolean isRaceCompleted(Race race) {
        if (currentUserId == null || resultManager == null) {
            return false;
        }
        
        Realm realm = Realm.getDefaultInstance();
        try {
            Result result = realm.where(Result.class)
                    .equalTo("raceId", race.getRaceId())
                    .equalTo("userId", currentUserId)
                    .findFirst();
            
            if (result != null) {
                Result.Status status = result.getStatus();
                // FINISHED 或 FINISHED_WITH_PENALTY 表示完成（保留兼容性）
                return status == Result.Status.FINISHED || 
                       status == Result.Status.FINISHED_WITH_PENALTY;
            }
            return false;
        } finally {
            realm.close();
        }
    }

    /**
     * 获取路线文本（起点→终点）
     */
    private String getRouteText(Race race) {
        if (race.getCheckPoints() == null || race.getCheckPoints().isEmpty()) {
            return "暂无打卡点";
        }
        
        String startPoint = null;
        String endPoint = null;
        
        for (CheckPoint point : race.getCheckPoints()) {
            if (CheckPoint.TYPE_START.equals(point.getType())) {
                startPoint = point.getName();
            } else if (CheckPoint.TYPE_FINISH.equals(point.getType())) {
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

    @NonNull
    @Override
    public RaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_race, parent, false);
        return new RaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RaceViewHolder holder, int position) {
        Race race = items.get(position);
        holder.bind(race);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * 赛事项ViewHolder
     */
    class RaceViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRaceName;
        private final TextView tvStatusTag;
        private final TextView tvRaceTimeRange;
        private final TextView tvRaceRoute;
        private final MaterialButton btnCheckIn;

        public RaceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRaceName = itemView.findViewById(R.id.tvRaceName);
            tvStatusTag = itemView.findViewById(R.id.tvStatusTag);
            tvRaceTimeRange = itemView.findViewById(R.id.tvRaceTimeRange);
            tvRaceRoute = itemView.findViewById(R.id.tvRaceRoute);
            btnCheckIn = itemView.findViewById(R.id.btnCheckIn);
        }

        public void bind(Race race) {
            // 设置赛事名称
            tvRaceName.setText(race.getName());
            
            // 设置状态标签
            Date now = new Date();
            boolean isCompleted = isRaceCompleted(race);
            String statusText;
            int statusBgRes;
            
            if (race.getStartTime() == null || race.getEndTime() == null) {
                statusText = isCompleted ? "已完成" : "进行中";
                statusBgRes = isCompleted ? R.drawable.bg_status_tag_completed : R.drawable.bg_status_tag_ongoing;
            } else if (now.before(race.getStartTime())) {
                statusText = "未开始";
                statusBgRes = R.drawable.bg_status_tag_not_started;
            } else if (now.after(race.getEndTime())) {
                statusText = isCompleted ? "已完成" : "已结束";
                statusBgRes = isCompleted ? R.drawable.bg_status_tag_completed : R.drawable.bg_status_tag_ended;
            } else {
                statusText = isCompleted ? "已完成" : "进行中";
                statusBgRes = isCompleted ? R.drawable.bg_status_tag_completed : R.drawable.bg_status_tag_ongoing;
            }
            tvStatusTag.setText(statusText);
            tvStatusTag.setBackgroundResource(statusBgRes);

            // 设置时间段（整行显示）
            if (race.getStartTime() != null && race.getEndTime() != null) {
                String timeRange = dateTimeFormat.format(race.getStartTime()) + " - " 
                        + dateTimeFormat.format(race.getEndTime());
                tvRaceTimeRange.setText(timeRange);
            } else {
                tvRaceTimeRange.setText("待定");
            }

            // 设置路线（起点→终点）
            String routeText = getRouteText(race);
            tvRaceRoute.setText(routeText);

            // 设置按钮：只有"进行中"状态显示"开始参赛"按钮
            if (race.getStartTime() != null && race.getEndTime() != null) {
                if (now.after(race.getStartTime()) && now.before(race.getEndTime()) && !isCompleted) {
                    btnCheckIn.setVisibility(View.VISIBLE);
                    btnCheckIn.setEnabled(true);
                    btnCheckIn.setAlpha(1.0f);
                } else {
                    btnCheckIn.setVisibility(View.GONE);
                }
            } else {
                btnCheckIn.setVisibility(View.GONE);
            }

            // 打卡按钮点击
            btnCheckIn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCheckInClick(race);
                }
            });

            // 整个卡片点击查看详情
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRaceClick(race);
                }
            });
        }
    }
}
