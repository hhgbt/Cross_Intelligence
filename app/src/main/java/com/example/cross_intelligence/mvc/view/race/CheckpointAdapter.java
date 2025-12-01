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

    private final List<CheckPoint> data;
    private final OnItemDeleteListener deleteListener;

    CheckpointAdapter(List<CheckPoint> data, OnItemDeleteListener deleteListener) {
        this.data = data;
        this.deleteListener = deleteListener;
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
        String nameText = item.getName();
        if (item.getType() != null && !item.getType().isEmpty()) {
            nameText = nameText + "(" + item.getType() + ")";
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
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class CheckpointViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvCoord;
        final ImageButton btnDelete;

        CheckpointViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPointName);
            tvCoord = itemView.findViewById(R.id.tvPointCoord);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}

