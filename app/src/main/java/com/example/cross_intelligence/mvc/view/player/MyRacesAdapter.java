package com.example.cross_intelligence.mvc.view.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.Race;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 我的赛事适配器：显示已报名的赛事
 */
public class MyRacesAdapter extends RecyclerView.Adapter<MyRacesAdapter.ViewHolder> {

    private final List<Race> races = new ArrayList<>();
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private OnRaceActionListener listener;

    public interface OnRaceActionListener {
        void onCheckInClick(Race race);
        void onRaceClick(Race race);
    }

    public void setOnRaceActionListener(OnRaceActionListener listener) {
        this.listener = listener;
    }

    public void setRaces(@NonNull List<Race> races) {
        this.races.clear();
        this.races.addAll(races);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_race, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Race race = races.get(position);
        holder.bind(race);
    }

    @Override
    public int getItemCount() {
        return races.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRaceName;
        private final TextView tvRaceDescription;
        private final TextView tvRaceTime;
        private final MaterialButton btnCheckIn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRaceName = itemView.findViewById(R.id.tvRaceName);
            tvRaceDescription = itemView.findViewById(R.id.tvRaceDescription);
            tvRaceTime = itemView.findViewById(R.id.tvRaceTime);
            btnCheckIn = itemView.findViewById(R.id.btnCheckIn);
        }

        public void bind(Race race) {
            tvRaceName.setText(race.getName());
            
            // 设置描述
            if (race.getDescription() != null && !race.getDescription().isEmpty()) {
                tvRaceDescription.setText(race.getDescription());
                tvRaceDescription.setVisibility(View.VISIBLE);
            } else {
                tvRaceDescription.setVisibility(View.GONE);
            }

            // 设置时间
            String timeText = "";
            if (race.getStartTime() != null && race.getEndTime() != null) {
                timeText = dateTimeFormat.format(race.getStartTime()) + " - " 
                        + dateTimeFormat.format(race.getEndTime());
            }
            tvRaceTime.setText(timeText);

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


