package com.example.cross_intelligence.mvc.view.result;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.controller.RaceManager;
import com.example.cross_intelligence.mvc.controller.TrackManager;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.example.cross_intelligence.mvc.model.Result;
import com.example.cross_intelligence.mvc.model.TrackPoint;
import com.example.cross_intelligence.mvc.util.DistanceUtil;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * 我的成绩适配器 - 显示赛事名称和成绩信息（海报式卡片）
 */
class MyResultAdapter extends RecyclerView.Adapter<MyResultAdapter.MyResultViewHolder> {

    interface OnResultClickListener {
        void onResultClick(@NonNull Result result);
    }

    private final List<Result> data;
    private final OnResultClickListener listener;
    private final RaceManager raceManager;
    private final TrackManager trackManager;

    MyResultAdapter(List<Result> data, OnResultClickListener listener) {
        this.data = data;
        this.listener = listener;
        this.raceManager = new RaceManager();
        this.trackManager = new TrackManager();
    }

    @NonNull
    @Override
    public MyResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_result, parent, false);
        return new MyResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyResultViewHolder holder, int position) {
        Result result = data.get(position);
        
        // 获取赛事信息
        Race race = raceManager.getRaceById(result.getRaceId());
        String raceName = race != null && race.getName() != null ? race.getName() : "未知赛事";
        holder.tvRaceName.setText(raceName);
        
        // 显示排名
        if (result.getRank() > 0) {
            holder.tvRank.setText(holder.itemView.getContext().getString(R.string.result_rank_format, result.getRank()));
        } else {
            holder.tvRank.setText(holder.itemView.getContext().getString(R.string.result_dnf));
        }
        
        // 显示总用时（格式化：mm:ss 或 hh:mm:ss）
        long totalSeconds = result.getTotalSeconds();
        holder.tvTotal.setText(formatTime(totalSeconds));
        
        // 计算并显示平均配速（使用和详情页相同的计算方法）
        String paceText = calculatePaceText(result, race);
        holder.tvPace.setText(paceText);
        
        // 设置状态 Chip
        Result.Status status = result.getStatus();
        if (status == null) {
            status = Result.Status.DNF;
        }
        
        String statusText = getStatusText(status);
        holder.chipStatus.setText(statusText);
        
        // 根据状态动态设置 Chip 颜色和图标
        if (status == Result.Status.DNF) {
            // 未完成：红色
            holder.chipStatus.setChipBackgroundColorResource(R.color.status_dnf);
            holder.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
        } else {
            // 已完成：绿色
            holder.chipStatus.setChipBackgroundColorResource(R.color.forest_green);
            holder.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onResultClick(result);
            }
        });
    }
    
    /**
     * 格式化时间为 mm:ss 或 hh:mm:ss
     */
    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    /**
     * 计算平均配速文本（使用和ResultDetailActivity相同的计算方法）
     */
    private String calculatePaceText(Result result, Race race) {
        // 检查是否是异常成绩（未完成）
        boolean isDNF = result.getStatus() == Result.Status.DNF;
        
        if (isDNF) {
            return "0'00\"";
        }
        
        // 优先使用轨迹点计算距离（和详情页一致）
        double totalDistanceKm = 0.0;
        if (race != null) {
            List<TrackPoint> trackPoints = trackManager.queryTrack(race.getRaceId(), result.getUserId());
            if (trackPoints != null && trackPoints.size() >= 2) {
                totalDistanceKm = calculateTotalDistanceFromTrack(trackPoints) / 1000.0; // 转换为公里
            }
            
            // 如果轨迹数据不可用，使用检查点计算距离（备选方案）
            if (totalDistanceKm <= 0 && race.getCheckPoints() != null && race.getCheckPoints().size() >= 2) {
                totalDistanceKm = calculateDistanceFromCheckPoints(race.getCheckPoints()) / 1000.0; // 转换为公里
            }
        }
        
        long totalSeconds = result.getTotalSeconds();
        if (totalDistanceKm > 0 && totalSeconds > 0) {
            // 正常成绩计算配速（和详情页一致）
            double paceMinutes = (totalSeconds / 60.0) / totalDistanceKm;
            int minutes = (int) paceMinutes;
            int seconds = (int) ((paceMinutes - minutes) * 60);
            return String.format("%d'%02d\"", minutes, seconds);
        } else {
            // 其他情况显示默认值
            return "--'/km";
        }
    }
    
    /**
     * 从轨迹点计算总距离（和ResultDetailActivity相同的算法）
     */
    private double calculateTotalDistanceFromTrack(List<TrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.size() < 2) {
            return 0.0;
        }
        
        // 按时间排序
        List<TrackPoint> sortedPoints = new ArrayList<>(trackPoints);
        sortedPoints.sort((p1, p2) -> {
            if (p1.getTimestamp() == null && p2.getTimestamp() == null) return 0;
            if (p1.getTimestamp() == null) return 1;
            if (p2.getTimestamp() == null) return -1;
            return p1.getTimestamp().compareTo(p2.getTimestamp());
        });
        
        double totalDistance = 0.0;
        TrackPoint lastValidPoint = null;
        
        for (TrackPoint curr : sortedPoints) {
            // 跳过无效的点
            if (curr.getLatitude() == 0.0 && curr.getLongitude() == 0.0) {
                continue;
            }
            if (Math.abs(curr.getLatitude()) > 90 || Math.abs(curr.getLongitude()) > 180) {
                continue;
            }
            
            if (lastValidPoint != null) {
                double distance = DistanceUtil.distanceMeters(
                        lastValidPoint.getLatitude(), lastValidPoint.getLongitude(),
                        curr.getLatitude(), curr.getLongitude()
                );
                
                long timeDiff = 0;
                if (lastValidPoint.getTimestamp() != null && curr.getTimestamp() != null) {
                    timeDiff = (curr.getTimestamp().getTime() - lastValidPoint.getTimestamp().getTime()) / 1000;
                }
                
                if (distance <= 0) {
                    continue;
                }
                
                if (timeDiff <= 0) {
                    if (distance < 50) {
                        totalDistance += distance;
                        lastValidPoint = curr;
                    }
                    continue;
                }
                
                // 计算速度并过滤异常值
                double speed = distance / timeDiff;
                double speedKmh = speed * 3.6;
                
                boolean isAbnormal = false;
                if (distance >= 50) {
                    if (speedKmh > 40.0 || distance > 3000) {
                        isAbnormal = true;
                    }
                } else {
                    if (speedKmh > 60.0) {
                        isAbnormal = true;
                    }
                }
                
                if (!isAbnormal) {
                    totalDistance += distance;
                    lastValidPoint = curr;
                }
            } else {
                lastValidPoint = curr;
            }
        }
        
        return totalDistance;
    }
    
    /**
     * 从检查点计算总距离（备选方案）
     */
    private double calculateDistanceFromCheckPoints(List<CheckPoint> checkPoints) {
        if (checkPoints == null || checkPoints.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        for (int i = 1; i < checkPoints.size(); i++) {
            CheckPoint prev = checkPoints.get(i - 1);
            CheckPoint curr = checkPoints.get(i);
            double distance = DistanceUtil.distanceMeters(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );
            totalDistance += distance;
        }
        
        return totalDistance;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
    
    /**
     * 获取状态的中文文本
     */
    private String getStatusText(Result.Status status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case FINISHED:
                return "已完成";
            case FINISHED_WITH_PENALTY:
                return "已完成"; // 删除罚时显示
            case DNF:
                return "未完成";
            default:
                return "未知";
        }
    }

    static class MyResultViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRaceName;
        final TextView tvRank;
        final TextView tvTotal;
        final TextView tvPace;
        final Chip chipStatus;

        MyResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRaceName = itemView.findViewById(R.id.tvRaceName);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvTotal = itemView.findViewById(R.id.tvTotal);
            tvPace = itemView.findViewById(R.id.tvPace);
            chipStatus = itemView.findViewById(R.id.chipStatus);
        }
    }
}

