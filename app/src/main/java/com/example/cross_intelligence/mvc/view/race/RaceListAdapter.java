package com.example.cross_intelligence.mvc.view.race;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RaceListAdapter extends RecyclerView.Adapter<RaceListAdapter.RaceViewHolder> {
    // 统一时间格式化规则（和管理员端保持一致）
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    private List<Race> raceList;
    private OnRaceItemClickListener listener;

    // 点击事件回调（保持原有接口，兼容普通用户端跳转逻辑）
    public interface OnRaceItemClickListener {
        void onRaceClick(Race race);
    }

    public RaceListAdapter(OnRaceItemClickListener listener) {
        this.listener = listener;
    }

    // 设置数据（保留原有方法，保证调用逻辑不变）
    public void setData(List<Race> raceList) {
        this.raceList = raceList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 使用传统LayoutInflater加载布局
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_race_list, parent, false); // 确保布局文件名正确
        return new RaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RaceViewHolder holder, int position) {
        Race race = raceList.get(position);
        if (race == null) return;

        // 检查赛事是否已结束
        boolean isEnded = isRaceEnded(race);

        // 1. 显示赛事名称
        holder.tvRaceName.setText(race.getName());
        
        // 设置状态标签
        if (isEnded) {
            holder.tvStatusTag.setText("已结束");
            holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ended);
        } else {
            holder.tvStatusTag.setText("进行中");
            holder.tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ongoing);
        }

        // 2. 显示赛事时间范围（替换原有仅显示日期的逻辑，和管理员端对齐）
        String timeText = getTimeText(race);
        holder.tvRaceTimeRange.setText(timeText);

        // 3. 显示赛事路线（起点→终点，和管理员端逻辑一致）
        String routeText = getRouteText(race);
        holder.tvRaceRoute.setText(routeText);

        // 4. 如果赛事已结束，设置灰色样式并禁用点击
        if (isEnded) {
            holder.itemView.setAlpha(0.5f); // 变灰
            holder.itemView.setEnabled(false); // 禁用点击
            holder.itemView.setOnClickListener(null);
        } else {
            holder.itemView.setAlpha(1.0f); // 正常显示
            holder.itemView.setEnabled(true); // 启用点击
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRaceClick(race);
                }
            });
        }
    }
    
    /**
     * 检查赛事是否已结束
     */
    private boolean isRaceEnded(Race race) {
        if (race.getEndTime() == null) {
            return false;
        }
        Date now = new Date();
        return race.getEndTime().before(now);
    }

    @Override
    public int getItemCount() {
        return raceList == null ? 0 : raceList.size();
    }

    // 静态内部类 ViewHolder（改用findViewById）
    static class RaceViewHolder extends RecyclerView.ViewHolder {
        TextView tvRaceName;
        TextView tvStatusTag;
        TextView tvRaceTimeRange;
        TextView tvRaceRoute;

        public RaceViewHolder(View itemView) {
            super(itemView);
            // 绑定布局控件（确保控件ID和布局文件一致）
            tvRaceName = itemView.findViewById(R.id.tvRaceName);
            tvStatusTag = itemView.findViewById(R.id.tvStatusTag);
            tvRaceTimeRange = itemView.findViewById(R.id.tvRaceTimeRange);
            tvRaceRoute = itemView.findViewById(R.id.tvRaceRoute);
        }
    }

    // 【核心适配】获取赛事路线文本（复用管理员端逻辑）
    private String getRouteText(Race race) {
        List<CheckPoint> points = race.getCheckPoints();
        if (points == null || points.isEmpty()) {
            return "暂无打卡点";
        }

        String startPoint = null;
        String endPoint = null;

        for (CheckPoint point : points) {
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

    // 【核心适配】获取赛事时间文本（复用管理员端逻辑，使用中文格式）
    private String getTimeText(Race race) {
        SimpleDateFormat chineseFormat = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);
        if (race.getStartTime() != null && race.getEndTime() != null) {
            // 完整时间范围：yyyy年M月d日 HH:mm - yyyy年M月d日 HH:mm
            return chineseFormat.format(race.getStartTime()) + " - " + chineseFormat.format(race.getEndTime());
        } else if (race.getStartTime() != null) {
            // 仅显示开始时间
            return chineseFormat.format(race.getStartTime()) + " - ";
        } else {
            return "待定";
        }
    }
}