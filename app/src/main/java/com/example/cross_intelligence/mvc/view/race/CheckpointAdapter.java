package com.example.cross_intelligence.mvc.view.race;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    private final List<CheckPoint> data;
    private final OnItemDeleteListener deleteListener;
    private OnItemQrClickListener qrClickListener;

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

    @NonNull
    @Override
    public CheckpointViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checkpoint, parent, false);
        return new CheckpointViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CheckpointViewHolder holder, int position) {
        CheckPoint item = data.get(position);
        
        // 根据类型添加标识符号
        String typeIcon = getTypeIcon(item.getType());
        String nameText = item.getName();
        if (item.getType() != null && !item.getType().isEmpty()) {
            nameText = typeIcon + " " + nameText + " (" + item.getType() + ")";
        }
        
        holder.tvName.setText(holder.itemView.getContext().getString(
                R.string.checkpoint_name_format, item.getOrderIndex(), nameText));
        holder.tvCoord.setText(holder.itemView.getContext().getString(
                R.string.checkpoint_coord_format, item.getLatitude(), item.getLongitude()));
        
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

        // 设置二维码按钮点击事件
        holder.btnShowQr.setOnClickListener(v -> {
            if (qrClickListener != null) {
                qrClickListener.onQrClick(item);
            }
        });
    }
    
    /**
     * 根据打卡点类型返回对应的图标
     */
    private String getTypeIcon(String type) {
        if (CheckPoint.TYPE_START.equals(type)) {
            return "🏁"; // 起点旗帜
        } else if (CheckPoint.TYPE_FINISH.equals(type)) {
            return "🎯"; // 终点靶心
        } else {
            return "📍"; // 检查点图钉
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class CheckpointViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvCoord;
        final ImageButton btnShowQr;
        final ImageButton btnDelete;

        CheckpointViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPointName);
            tvCoord = itemView.findViewById(R.id.tvPointCoord);
            btnShowQr = itemView.findViewById(R.id.btnShowQr);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}

