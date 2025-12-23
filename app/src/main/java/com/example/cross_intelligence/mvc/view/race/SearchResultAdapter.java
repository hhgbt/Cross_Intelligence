package com.example.cross_intelligence.mvc.view.race;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.services.help.Tip;
import com.example.cross_intelligence.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索结果适配器：用于显示高德地图 Inputtips 搜索建议
 */
class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {

    interface OnItemClickListener {
        void onItemClick(Tip tip);
    }

    private final List<Tip> data = new ArrayList<>();
    private OnItemClickListener itemClickListener;

    void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    void updateData(List<Tip> newData) {
        data.clear();
        if (newData != null) {
            data.addAll(newData);
        }
        notifyDataSetChanged();
    }

    void clearData() {
        data.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new SearchResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        Tip tip = data.get(position);
        
        // 设置位置图标颜色为蓝色
        holder.ivLocationIcon.setColorFilter(
                ContextCompat.getColor(holder.itemView.getContext(), R.color.button_blue),
                android.graphics.PorterDuff.Mode.SRC_IN);
        
        // 显示地点名称
        holder.tvName.setText(tip.getName() != null ? tip.getName() : "");
        
        // 显示地址信息（如果有）
        String address = tip.getAddress();
        if (address != null && !address.isEmpty()) {
            holder.tvAddress.setText(address);
            holder.tvAddress.setVisibility(View.VISIBLE);
        } else {
            holder.tvAddress.setVisibility(View.GONE);
        }
        
        // 点击事件 - 使用 OnTouchListener 确保立即响应，不被其他视图拦截
        holder.itemView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.util.Log.d("SearchResultAdapter", "=== 点击搜索结果项: " + tip.getName() + " ===");
                if (itemClickListener != null) {
                    android.util.Log.d("SearchResultAdapter", "调用 itemClickListener.onItemClick");
                    itemClickListener.onItemClick(tip);
                    return true; // 消费事件
                }
            }
            return false; // 不消费事件，让其他处理
        });
        
        // 也设置 OnClickListener 作为备用
        holder.itemView.setOnClickListener(v -> {
            android.util.Log.d("SearchResultAdapter", "OnClickListener 被触发: " + tip.getName());
            if (itemClickListener != null) {
                itemClickListener.onItemClick(tip);
            }
        });
        
        // 确保 itemView 可以接收点击事件
        holder.itemView.setClickable(true);
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
        holder.itemView.setEnabled(true);
        
        // 也确保 CardView 可以接收点击
        if (holder.itemView instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView cardView = 
                (com.google.android.material.card.MaterialCardView) holder.itemView;
            cardView.setClickable(true);
            cardView.setFocusable(true);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivLocationIcon;
        final TextView tvName;
        final TextView tvAddress;

        SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            ivLocationIcon = itemView.findViewById(R.id.ivLocationIcon);
            tvName = itemView.findViewById(R.id.tvSearchResultName);
            tvAddress = itemView.findViewById(R.id.tvSearchResultAddress);
        }
    }
}

