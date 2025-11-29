package com.example.cross_intelligence.mvc.view.result;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.model.Result;

import java.util.List;

class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ResultViewHolder> {

    interface OnResultClickListener {
        void onResultClick(@NonNull Result result);
    }

    private final List<Result> data;
    private final OnResultClickListener listener;

    ResultAdapter(List<Result> data, OnResultClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result_row, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        Result result = data.get(position);
        holder.tvRank.setText(result.getRank() > 0
                ? holder.itemView.getContext().getString(R.string.result_rank_format, result.getRank())
                : holder.itemView.getContext().getString(R.string.result_dnf));
        holder.tvUser.setText(holder.itemView.getContext().getString(
                R.string.result_user_format, result.getUserId()));
        holder.tvTotal.setText(holder.itemView.getContext().getString(
                R.string.result_total_format, result.getTotalSeconds()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onResultClick(result);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvUser;
        final TextView tvTotal;

        ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvUser = itemView.findViewById(R.id.tvUser);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }
}


