package com.example.cross_intelligence.mvc.controller;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cross_intelligence.mvc.model.User;

import io.realm.Realm;

/**
 * User 业务控制器，负责登录、角色判定、资料更新与本地持久化。
 */
public class UserManager {

    public interface LoginCallback {
        void onSuccess(@NonNull User user);

        void onFailure(@NonNull Throwable throwable);
    }

    public interface RegisterCallback {
        void onSuccess(@NonNull User user);

        void onFailure(@NonNull Throwable throwable);
    }

    public interface UserCallback {
        void onResult(@Nullable User user);
    }

    public interface CompletionCallback {
        void onComplete();

        void onError(@NonNull Throwable throwable);
    }

    public void login(String userId, String password, @Nullable String role, LoginCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            User user = bgRealm.where(User.class).equalTo("userId", userId).findFirst();
            if (user == null) {
                throw new IllegalStateException("用户不存在");
            }
            if (!TextUtils.isEmpty(role)) {
                user.setRole(role);
            }
            if (TextUtils.isEmpty(user.getName())) {
                user.setName(userId);
            }
            // 密码校验示例：现实中应由服务端完成，此处仅验证非空
            if (TextUtils.isEmpty(password)) {
                throw new IllegalArgumentException("密码不能为空");
            }
        }, () -> {
            try {
                User user = realm.where(User.class).equalTo("userId", userId).findFirst();
                if (user != null) {
                    callback.onSuccess(realm.copyFromRealm(user));
                } else {
                    callback.onFailure(new IllegalStateException("用户不存在"));
                }
            } finally {
                realm.close();
            }
        }, error -> {
            realm.close();
            callback.onFailure(error);
        });
    }

    public void fetchUser(String userId, UserCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        User user = realm.where(User.class).equalTo("userId", userId).findFirst();
        User detached = user != null ? realm.copyFromRealm(user) : null;
        realm.close();
        callback.onResult(detached);
    }

    public void register(@NonNull String userId, @NonNull String role, @Nullable String displayName,
                         @NonNull RegisterCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onFailure(new IllegalArgumentException("账号不能为空"));
            return;
        }
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> {
            User existing = bgRealm.where(User.class).equalTo("userId", userId).findFirst();
            if (existing != null) {
                throw new IllegalStateException("用户已存在");
            }
            User newUser = bgRealm.createObject(User.class, userId);
            newUser.setRole(role);
            newUser.setName(!TextUtils.isEmpty(displayName) ? displayName : userId);
        }, () -> {
            try {
                User user = realm.where(User.class).equalTo("userId", userId).findFirst();
                if (user != null) {
                    callback.onSuccess(realm.copyFromRealm(user));
                } else {
                    callback.onFailure(new IllegalStateException("注册失败"));
                }
            } finally {
                realm.close();
            }
        }, error -> {
            realm.close();
            callback.onFailure(error);
        });
    }

    public void updateProfile(@NonNull User newProfile, @NonNull CompletionCallback callback) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransactionAsync(bgRealm -> bgRealm.insertOrUpdate(newProfile),
                () -> {
                    realm.close();
                    callback.onComplete();
                },
                error -> {
                    realm.close();
                    callback.onError(error);
                });
    }

    public String getRole(String userId) {
        Realm realm = Realm.getDefaultInstance();
        User user = realm.where(User.class).equalTo("userId", userId).findFirst();
        String role = user != null ? user.getRole() : null;
        realm.close();
        return role;
    }
}
