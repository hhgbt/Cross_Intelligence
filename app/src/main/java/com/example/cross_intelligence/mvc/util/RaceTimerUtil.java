package com.example.cross_intelligence.mvc.util;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Locale;

/**
 * 比赛计时器工具类
 * 用于在 CheckInActivity 中显示实时计时
 * 格式：00:00:00 (时:分:秒)
 */
public class RaceTimerUtil {

    private final TextView timerView;
    private final Handler handler;
    private final Runnable timerRunnable;
    private Date startTime;
    private boolean isRunning = false;

    /**
     * 计时器监听器
     */
    public interface TimerListener {
        void onTick(long elapsedMillis, String formattedTime);
    }

    private TimerListener listener;

    /**
     * 创建计时器
     * @param timerView 显示计时的 TextView
     */
    public RaceTimerUtil(@NonNull TextView timerView) {
        this.timerView = timerView;
        this.handler = new Handler(Looper.getMainLooper());
        this.timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && startTime != null) {
                    long elapsedMillis = System.currentTimeMillis() - startTime.getTime();
                    String formattedTime = formatElapsedTime(elapsedMillis);
                    timerView.setText(formattedTime);
                    
                    // 通知监听器
                    if (listener != null) {
                        listener.onTick(elapsedMillis, formattedTime);
                    }
                    
                    // 每秒更新一次
                    handler.postDelayed(this, 1000);
                }
            }
        };
    }

    /**
     * 设置监听器
     */
    public void setListener(@Nullable TimerListener listener) {
        this.listener = listener;
    }

    /**
     * 开始计时
     * @param startTime 开始时间
     */
    public void start(@NonNull Date startTime) {
        if (isRunning) {
            stop(); // 先停止之前的计时
        }
        
        this.startTime = startTime;
        this.isRunning = true;
        handler.post(timerRunnable);
    }

    /**
     * 停止计时
     */
    public void stop() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    /**
     * 重置计时器
     */
    public void reset() {
        stop();
        startTime = null;
        timerView.setText("00:00:00");
    }

    /**
     * 获取已用时（毫秒）
     */
    public long getElapsedMillis() {
        if (startTime == null) {
            return 0;
        }
        return System.currentTimeMillis() - startTime.getTime();
    }

    /**
     * 获取格式化的已用时
     */
    public String getFormattedElapsedTime() {
        return formatElapsedTime(getElapsedMillis());
    }

    /**
     * 格式化已用时
     * @param millis 毫秒
     * @return 格式化字符串 "HH:MM:SS"
     */
    public static String formatElapsedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        return String.format(Locale.CHINA, "%02d:%02d:%02d",
                hours,
                minutes % 60,
                seconds % 60);
    }

    /**
     * 格式化已用时（带毫秒）
     * @param millis 毫秒
     * @return 格式化字符串 "HH:MM:SS.mmm"
     */
    public static String formatElapsedTimeWithMillis(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainMillis = millis % 1000;

        return String.format(Locale.CHINA, "%02d:%02d:%02d.%03d",
                hours,
                minutes % 60,
                seconds % 60,
                remainMillis);
    }

    /**
     * 判断是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 释放资源
     */
    public void release() {
        stop();
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * 简化版：创建并启动计时器
     * @param timerView 显示计时的 TextView
     * @param startTime 开始时间
     * @return 计时器实例
     */
    public static RaceTimerUtil createAndStart(@NonNull TextView timerView, @NonNull Date startTime) {
        RaceTimerUtil timer = new RaceTimerUtil(timerView);
        timer.start(startTime);
        return timer;
    }
}











