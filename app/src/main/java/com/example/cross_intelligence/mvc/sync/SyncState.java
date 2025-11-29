package com.example.cross_intelligence.mvc.sync;

public class SyncState {
    public enum Status {
        IDLE, RUNNING, SUCCESS, ERROR, OFFLINE
    }

    private final Status status;
    private final int processed;
    private final int total;
    private final String message;

    private SyncState(Status status, int processed, int total, String message) {
        this.status = status;
        this.processed = processed;
        this.total = total;
        this.message = message;
    }

    public static SyncState idle() {
        return new SyncState(Status.IDLE, 0, 0, "待同步");
    }

    public static SyncState running(int processed, int total) {
        return new SyncState(Status.RUNNING, processed, total, "同步中");
    }

    public static SyncState offline() {
        return new SyncState(Status.OFFLINE, 0, 0, "当前离线");
    }

    public static SyncState success() {
        return new SyncState(Status.SUCCESS, 0, 0, "同步完成");
    }

    public static SyncState error(String msg) {
        return new SyncState(Status.ERROR, 0, 0, msg);
    }

    public Status getStatus() {
        return status;
    }

    public int getProcessed() {
        return processed;
    }

    public int getTotal() {
        return total;
    }

    public String getMessage() {
        return message;
    }
}




