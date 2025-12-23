package com.example.cross_intelligence.mvc.view.race;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.CheckPoint;

import java.util.List;

class CheckpointAdapter extends RecyclerView.Adapter<CheckpointAdapter.CheckpointViewHolder> {

    interface OnItemDeleteListener {
        void onDelete(int position);
    }

    interface OnItemQrClickListener {
        void onQrClick(CheckPoint checkPoint);
    }

    interface OnItemLocationClickListener {
        void onLocationClick(CheckPoint checkPoint);
    }

    private final List<CheckPoint> data;
    private final OnItemDeleteListener deleteListener;
    private OnItemQrClickListener qrClickListener;
    private OnItemLocationClickListener locationClickListener;

    CheckpointAdapter(List<CheckPoint> data, OnItemDeleteListener deleteListener) {
        this.data = data;
        this.deleteListener = deleteListener;
    }

    /**
     * 设置二维码点击监听器
     */
    public void setOnItemQrClickListener(OnItemQrClickListener listener) {
        this.qrClickListener = listener;
    }

    /**
     * 设置位置点击监听器
     */
    public void setOnItemLocationClickListener(OnItemLocationClickListener listener) {
        this.locationClickListener = listener;
    }

    @NonNull
    @Override
    public CheckpointViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checkpoint, parent, false);
        return new CheckpointViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CheckpointViewHolder holder, int position) {
        CheckPoint item = data.get(position);
        
        // 设置左侧色彩条颜色
        int colorStripColor = getColorStripColor(item.getType(), holder.itemView);
        holder.vColorStrip.setBackgroundColor(colorStripColor);
        
        // 设置序号
        holder.tvOrderIndex.setText(String.valueOf(item.getOrderIndex()));
        
        // 设置打卡点名称，在前面增加不同类型的 emoji 图标
        String prefix;
        if (CheckPoint.TYPE_START.equals(item.getType())) {
            prefix = "🏁 ";
        } else if (CheckPoint.TYPE_FINISH.equals(item.getType())) {
            prefix = "🥇 ";
        } else {
            prefix = "📍 ";
        }
        holder.tvName.setText(prefix + (item.getName() != null ? item.getName() : ""));
        
        // 显示经纬度信息
        holder.tvCoord.setText(holder.itemView.getContext().getString(
                R.string.checkpoint_coord_format, item.getLatitude(), item.getLongitude()));
        
        // 统一设置为白色背景
        com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) holder.itemView;
        cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
        
        // 位置按钮点击事件（替换二维码按钮）
        if (locationClickListener != null) {
            holder.btnQrCode.setVisibility(View.VISIBLE);
            holder.btnQrCode.setImageResource(R.drawable.ic_location);
            holder.btnQrCode.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.button_blue));
            holder.btnQrCode.setOnClickListener(v -> {
                locationClickListener.onLocationClick(item);
            });
        } else if (qrClickListener != null) {
            // 二维码按钮（仅管理员可见，在创建/编辑页面）
            holder.btnQrCode.setVisibility(View.VISIBLE);
            holder.btnQrCode.setImageResource(R.drawable.ic_qr_code);
            holder.btnQrCode.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.button_blue));
            holder.btnQrCode.setOnClickListener(v -> {
                qrClickListener.onQrClick(item);
            });
        } else {
            holder.btnQrCode.setVisibility(View.GONE);
        }
        
        // 如果 deleteListener 为 null，表示只读模式，隐藏删除按钮
        if (deleteListener == null) {
            holder.btnDelete.setVisibility(View.GONE);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    deleteListener.onDelete(adapterPosition);
                }
            });
        }
    }
    
    /**
     * 根据打卡点类型返回对应的色彩条颜色
     */
    private int getColorStripColor(String type, View view) {
        if (CheckPoint.TYPE_START.equals(type)) {
            // 起点：绿色
            return ContextCompat.getColor(view.getContext(), R.color.forest_green);
        } else if (CheckPoint.TYPE_FINISH.equals(type)) {
            // 终点：橙色
            return ContextCompat.getColor(view.getContext(), R.color.trail_orange);
        } else {
            // 检查点：蓝色
            return ContextCompat.getColor(view.getContext(), R.color.button_blue);
        }
    }
    
    

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class CheckpointViewHolder extends RecyclerView.ViewHolder {
        final View vColorStrip;
        final TextView tvOrderIndex;
        final TextView tvName;
        final TextView tvCoord;
        final ImageButton btnQrCode;
        final ImageButton btnDelete;

        CheckpointViewHolder(@NonNull View itemView) {
            super(itemView);
            vColorStrip = itemView.findViewById(R.id.vColorStrip);
            tvOrderIndex = itemView.findViewById(R.id.tvOrderIndex);
            tvName = itemView.findViewById(R.id.tvPointName);
            tvCoord = itemView.findViewById(R.id.tvPointCoord);
            btnQrCode = itemView.findViewById(R.id.btnQrCode);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}

