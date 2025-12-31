package com.example.cross_intelligence.mvc.view.player;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.controller.RaceSignupController;
import com.example.cross_intelligence.mvc.model.CheckPoint;
import com.example.cross_intelligence.mvc.model.Race;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 赛事大厅适配器：显示所有可报名赛事
 */
public class RaceDiscoveryAdapter extends RecyclerView.Adapter<RaceDiscoveryAdapter.ViewHolder> {

    private final List<Race> races = new ArrayList<>();
    // 仅在主线程使用，作为日期展示格式
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private OnRaceActionListener listener;
    private RaceSignupController signupController;
    private String currentUserId;

    public interface OnRaceActionListener {
        void onSignupClick(Race race);
        void onRaceClick(Race race);
    }

    public void setOnRaceActionListener(OnRaceActionListener listener) {
        this.listener = listener;
    }

    public void setSignupInfo(RaceSignupController signupController, String currentUserId) {
        this.signupController = signupController;
        this.currentUserId = currentUserId;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setRaces(@NonNull List<Race> races) {
        this.races.clear();
        this.races.addAll(races);
        // 排序：进行中未报名>未开始>进行中已报名>已结束已报名>已结束未报名
        sortRaces();
        notifyDataSetChanged();
    }

    /**
     * 排序赛事列表
     */
    private void sortRaces() {
        Date now = new Date();
        races.sort((r1, r2) -> {
            boolean r1Ended = r1.getEndTime() != null && r1.getEndTime().before(now);
            boolean r2Ended = r2.getEndTime() != null && r2.getEndTime().before(now);
            boolean r1Started = r1.getStartTime() != null && r1.getStartTime().before(now);
            boolean r2Started = r2.getStartTime() != null && r2.getStartTime().before(now);
            boolean r1SignedUp = signupController != null && currentUserId != null 
                    && signupController.isUserSignedUp(currentUserId, r1.getRaceId());
            boolean r2SignedUp = signupController != null && currentUserId != null 
                    && signupController.isUserSignedUp(currentUserId, r2.getRaceId());

            // 获取优先级：数字越小优先级越高
            int priority1 = getSortPriority(r1Ended, r1Started, r1SignedUp);
            int priority2 = getSortPriority(r2Ended, r2Started, r2SignedUp);
            
            return Integer.compare(priority1, priority2);
        });
    }

    /**
     * 获取排序优先级
     * 进行中未报名(1) > 未开始(2) > 进行中已报名(3) > 已结束已报名(4) > 已结束未报名(5)
     */
    private int getSortPriority(boolean isEnded, boolean isStarted, boolean isSignedUp) {
        if (isEnded) {
            return isSignedUp ? 4 : 5;
        } else if (isStarted) {
            return isSignedUp ? 3 : 1;
        } else {
            return 2; // 未开始
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_race_discovery, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Race race = races.get(position);
        holder.bind(race, signupController, currentUserId, listener);
    }

    @Override
    public int getItemCount() {
        return races.size();
    }

    /**
     * 获取起点和终点名称
     */
    private static String getRouteText(Race race) {
        if (race.getCheckPoints() == null || race.getCheckPoints().isEmpty()) {
            return "起点→终点";
        }
        
        String startName = "起点";
        String endName = "终点";
        
        for (CheckPoint point : race.getCheckPoints()) {
            if (CheckPoint.TYPE_START.equals(point.getType())) {
                startName = point.getName() != null ? point.getName() : "起点";
            } else if (CheckPoint.TYPE_FINISH.equals(point.getType())) {
                endName = point.getName() != null ? point.getName() : "终点";
            }
        }
        
        return startName + "→" + endName;
    }

    /**
     * 判断赛事状态
     */
    private static RaceStatus getRaceStatus(Race race) {
        Date now = new Date();
        boolean isEnded = race.getEndTime() != null && race.getEndTime().before(now);
        boolean isStarted = race.getStartTime() != null && race.getStartTime().before(now);
        
        if (isEnded) {
            return RaceStatus.ENDED;
        }
        
        if (isStarted) {
            return RaceStatus.ONGOING;
        }
        
        // 未开始
        return RaceStatus.NOT_STARTED;
    }

    private enum RaceStatus {
        NOT_STARTED, // 未开始
        ONGOING,     // 进行中
        ENDED        // 已结束
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivRaceImage;
        private final TextView tvStatusTag;
        private final TextView tvRaceName;
        private final TextView tvRaceRoute;
        private final TextView tvRaceTime;
        private final MaterialButton btnSignup;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivRaceImage = itemView.findViewById(R.id.ivRaceImage);
            tvStatusTag = itemView.findViewById(R.id.tvStatusTag);
            tvRaceName = itemView.findViewById(R.id.tvRaceName);
            tvRaceRoute = itemView.findViewById(R.id.tvRaceRoute);
            tvRaceTime = itemView.findViewById(R.id.tvRaceTime);
            btnSignup = itemView.findViewById(R.id.btnSignup);
        }

        void bind(Race race,
                  @Nullable RaceSignupController signupController,
                  @Nullable String userId,
                  @Nullable OnRaceActionListener listener) {
            // 设置赛事名称
            tvRaceName.setText(race.getName());

            // 加载赛事图片
            loadRaceImage(race);

            // 显示起点→终点
            String routeText = getRouteText(race);
            tvRaceRoute.setText(routeText);
            tvRaceRoute.setVisibility(View.VISIBLE);

            // 设置时间信息
            if (race.getStartTime() != null) {
                String dateText = DATE_FORMAT.format(race.getStartTime());
                android.content.Context context = itemView.getContext();
                tvRaceTime.setText(context.getString(R.string.race_start_date_format, dateText));
                tvRaceTime.setVisibility(View.VISIBLE);
            } else {
                tvRaceTime.setVisibility(View.GONE);
            }

            // 设置状态标签和按钮
            RaceStatus status = getRaceStatus(race);
            boolean isSignedUp = signupController != null && userId != null 
                    && signupController.isUserSignedUp(userId, race.getRaceId());
            updateStatusAndButton(status, isSignedUp, race);

            // 报名按钮点击
            btnSignup.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSignupClick(race);
                }
            });

            // 整个卡片点击查看详情
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRaceClick(race);
                }
            });
        }

        private void loadRaceImage(Race race) {
            String thumbnailPath = race.getThumbnailPath();
            android.content.Context context = itemView.getContext();

            // 1) 优先使用 Race 自带的 thumbnailPath
            if (thumbnailPath != null && !thumbnailPath.isEmpty()) {
                java.io.File imageFile = new java.io.File(thumbnailPath);
                if (imageFile.exists() && imageFile.canRead() && imageFile.length() > 0) {
                    Glide.with(context)
                            .load(imageFile)
                            .placeholder(android.R.drawable.ic_menu_mapmode)
                            .error(android.R.drawable.ic_menu_mapmode)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .centerCrop()
                            .into(ivRaceImage);
                    return;
                }
            }

            // 2) 回退：根据 MapThumbnailUtil 的命名规则推算本地路径（files/race_thumbnails/<raceId>_thumbnail.png）
            String raceId = race.getRaceId();
            if (raceId != null && !raceId.isEmpty()) {
                java.io.File fallbackFile = new java.io.File(context.getFilesDir(),
                        "race_thumbnails/" + raceId + "_thumbnail.png");
                if (fallbackFile.exists() && fallbackFile.canRead() && fallbackFile.length() > 0) {
                    Glide.with(context)
                            .load(fallbackFile)
                            .placeholder(android.R.drawable.ic_menu_mapmode)
                            .error(android.R.drawable.ic_menu_mapmode)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .centerCrop()
                            .into(ivRaceImage);
                    return;
                }
            }

            // 3) 没有缩略图则显示占位图
            Glide.with(context)
                    .load(android.R.drawable.ic_menu_mapmode)
                    .into(ivRaceImage);
        }

        private void updateStatusAndButton(RaceStatus status, boolean isSignedUp, Race race) {
            android.content.Context context = itemView.getContext();
            Drawable gradientBg = ContextCompat.getDrawable(context, R.drawable.bg_button_signup_gradient);
            Drawable signedBg = ContextCompat.getDrawable(context, R.drawable.bg_button_signup_signed);
            Drawable disabledBg = ContextCompat.getDrawable(context, R.drawable.bg_button_signup_disabled);
            
            switch (status) {
                case NOT_STARTED:
                    tvStatusTag.setText("未开始");
                    tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_not_started);
                    tvStatusTag.setVisibility(View.VISIBLE);
                    // 未开始状态：显示“不可报名”灰色按钮，不可点击
                    btnSignup.setVisibility(View.VISIBLE);
                    btnSignup.setText("不可报名");
                    if (disabledBg != null) {
                        btnSignup.setBackground(disabledBg);
                    } else {
                        btnSignup.setBackgroundResource(R.drawable.bg_button_signup_disabled);
                    }
                    btnSignup.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                    btnSignup.setEnabled(false);
                    break;
                    
                case ONGOING:
                    tvStatusTag.setText("进行中");
                    tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_ongoing);
                    tvStatusTag.setVisibility(View.VISIBLE);
                    btnSignup.setVisibility(View.VISIBLE);
                    if (isSignedUp) {
                        // 进行中已报名：白底绿框
                        btnSignup.setText("已报名");
                        if (signedBg != null) {
                            btnSignup.setBackground(signedBg);
                        } else {
                            btnSignup.setBackgroundResource(R.drawable.bg_button_signup_signed);
                        }
                        btnSignup.setTextColor(ContextCompat.getColor(context, R.color.forest_green));
                        btnSignup.setEnabled(false);
                    } else {
                        // 进行中未报名：显示报名按钮
                        btnSignup.setText("报名");
                        btnSignup.setBackground(gradientBg);
                        btnSignup.setTextColor(ContextCompat.getColor(context, android.R.color.white));
                        btnSignup.setEnabled(true);
                    }
                    break;
                    
                case ENDED:
                    tvStatusTag.setText("已结束");
                    tvStatusTag.setBackgroundResource(R.drawable.bg_status_tag_closed);
                    tvStatusTag.setVisibility(View.VISIBLE);
                    if (isSignedUp) {
                        // 已结束已报名：白底绿框绿字（和进行中已报名一样）
                        btnSignup.setVisibility(View.VISIBLE);
                        btnSignup.setText("已报名");
                        if (signedBg != null) {
                            btnSignup.setBackground(signedBg);
                        } else {
                            btnSignup.setBackgroundResource(R.drawable.bg_button_signup_signed);
                        }
                        btnSignup.setTextColor(ContextCompat.getColor(context, R.color.forest_green));
                        btnSignup.setEnabled(false);
                    } else {
                        // 已结束未报名：不显示按钮
                        btnSignup.setVisibility(View.GONE);
                    }
                    break;
            }
        }
    }
}




