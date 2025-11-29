package com.example.cross_intelligence.mvc.util;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 动态权限申请工具，链式调用配置权限、回调，内部保证线程安全。
 */
public final class PermissionUtil {

    public interface PermissionCallback {
        void onGranted();

        void onDenied(@NonNull List<String> deniedPermissions);
    }

    private final Context context;
    private final ActivityResultLauncher<String[]> launcher;
    private final List<String> permissions = new ArrayList<>();
    private PermissionCallback callback;
    private final AtomicBoolean requesting = new AtomicBoolean(false);

    private PermissionUtil(@NonNull Context context, @NonNull ActivityResultCaller caller) {
        this.context = context.getApplicationContext();
        this.launcher = caller.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    requesting.set(false);
                    List<String> denied = new ArrayList<>();
                    for (String permission : permissions) {
                        Boolean granted = result.get(permission);
                        if (granted == null || !granted) {
                            denied.add(permission);
                        }
                    }
                    if (denied.isEmpty()) {
                        if (callback != null) {
                            callback.onGranted();
                        }
                    } else if (callback != null) {
                        callback.onDenied(denied);
                    }
                });
    }

    public static PermissionUtil with(@NonNull Context context, @NonNull ActivityResultCaller caller) {
        return new PermissionUtil(context, caller);
    }

    public PermissionUtil permissions(@NonNull String... perms) {
        permissions.clear();
        permissions.addAll(Arrays.asList(perms));
        return this;
    }

    public PermissionUtil callback(@NonNull PermissionCallback callback) {
        this.callback = callback;
        return this;
    }

    @MainThread
    public void request() {
        if (permissions.isEmpty() || requesting.get()) {
            return;
        }
        List<String> needRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest.add(permission);
            }
        }
        if (needRequest.isEmpty()) {
            if (callback != null) {
                callback.onGranted();
            }
            return;
        }
        requesting.set(true);
        launcher.launch(needRequest.toArray(new String[0]));
    }
}




