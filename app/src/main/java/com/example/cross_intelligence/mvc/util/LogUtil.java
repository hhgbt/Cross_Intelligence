package com.example.cross_intelligence.mvc.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单分级日志工具，支持链式设置 Tag、消息及异常。
 */
public final class LogUtil {

    public enum Level {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR);

        final int priority;

        Level(int priority) {
            this.priority = priority;
        }
    }

    private static final AtomicInteger GLOBAL_LEVEL = new AtomicInteger(Log.VERBOSE);

    public static void setGlobalLevel(@NonNull Level level) {
        GLOBAL_LEVEL.set(level.priority);
    }

    private final String tag;
    private String message;
    private Throwable throwable;
    private Level level = Level.DEBUG;

    private LogUtil(@NonNull String tag) {
        this.tag = tag;
    }

    public static LogUtil tag(@NonNull String tag) {
        return new LogUtil(tag);
    }

    public LogUtil level(@NonNull Level level) {
        this.level = level;
        return this;
    }

    public LogUtil message(@Nullable String message) {
        this.message = message;
        return this;
    }

    public LogUtil throwable(@Nullable Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public void print() {
        if (level.priority < GLOBAL_LEVEL.get()) {
            return;
        }
        if (throwable != null) {
            Log.println(level.priority, tag, formatMessage() + '\n' + Log.getStackTraceString(throwable));
        } else {
            Log.println(level.priority, tag, formatMessage());
        }
    }

    private String formatMessage() {
        return message == null ? "" : message;
    }
}




