package com.example.cross_intelligence.mvc.controller;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cross_intelligence.mvc.model.User;

import io.realm.Realm;

import com.example.cross_intelligence.mvc.util.DataConverter;
import cn.leancloud.LCObject;
import cn.leancloud.LCQuery;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

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
                // 本地没有用户，尝试从云端拉取
                // 注意：这里是后台线程，不能直接进行异步网络请求并阻塞
                // 简化逻辑：先尝试本地登录，如果失败则在 UI 层处理（或 callback.onFailure 后尝试云端登录）
                // 由于 login 接口设计为同步返回结果，这里保持原样，只做本地登录
                // 云端同步逻辑放在登录成功后触发
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
                    // 【新增】登录成功后，同步本地用户数据到云端（或从云端拉取最新）
                    syncUserToCloud(user.getUserId());
                } else {
                    callback.onFailure(new IllegalStateException("用户不存在"));
                }
            } finally {
                realm.close();
            }
        }, error -> {
            realm.close();
            // 登录失败，尝试从云端拉取用户数据
            // 如果云端有，则拉取到本地并自动登录
            fetchUserFromCloud(userId, new UserCallback() {
                @Override
                public void onResult(User cloudUser) {
                    if (cloudUser != null) {
                        // 拉取成功，保存到本地并回调成功
                        Realm r = Realm.getDefaultInstance();
                        r.executeTransactionAsync(bg -> bg.copyToRealmOrUpdate(cloudUser), 
                            () -> {
                                r.close();
                                callback.onSuccess(cloudUser);
                            }, 
                            e -> {
                                r.close();
                                callback.onFailure(new IllegalStateException("同步云端用户失败"));
                            }
                        );
                    } else {
                        // 云端也没有，彻底失败
                        callback.onFailure(error);
                    }
                }
            });
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
                    // 【新增】注册成功后，立即同步到云端
                    syncUserToCloud(userId);
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
                    // 【新增】资料更新后，同步到云端
                    syncUserToCloud(newProfile.getUserId());
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

    /**
     * 【新增】同步用户数据到 LeanCloud
     */
    private void syncUserToCloud(String userId) {
        Realm bgRealm = Realm.getDefaultInstance();
        User user = bgRealm.where(User.class).equalTo("userId", userId).findFirst();
        
        if (user != null) {
            User userCopy = bgRealm.copyFromRealm(user);
            bgRealm.close();
            
            // 先查询云端是否已存在该用户（通过 userId）
            LCQuery<LCObject> query = new LCQuery<>("AppUser");
            query.whereEqualTo("userId", userId);
            query.getFirstInBackground().subscribe(new Observer<LCObject>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onNext(LCObject existingUser) {
                    // 云端已存在，更新它
                    LCObject lcUser = DataConverter.toLeanCloud(userCopy);
                    // 必须设置 objectId 才能更新，而不是创建新对象
                    try {
                        lcUser = LCObject.createWithoutData("AppUser", existingUser.getObjectId());
                        // 重新设置属性
                        lcUser.put("name", userCopy.getName());
                        lcUser.put("role", userCopy.getRole());
                        lcUser.put("phone", userCopy.getPhone());
                        lcUser.put("email", userCopy.getEmail());
                        lcUser.put("bio", userCopy.getBio());
                        lcUser.put("avatarUrl", userCopy.getAvatarUrl());
                        
                        saveUserToCloud(lcUser, userId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    // 云端不存在（或其他错误），尝试创建新对象
                    LCObject lcUser = DataConverter.toLeanCloud(userCopy);
                    saveUserToCloud(lcUser, userId);
                }

                @Override
                public void onComplete() {}
            });
        } else {
            bgRealm.close();
        }
    }

    private void saveUserToCloud(LCObject lcUser, String userId) {
        lcUser.saveInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject savedUser) {
                // 保存成功，更新本地 cloudId
                try (Realm r = Realm.getDefaultInstance()) {
                    r.executeTransaction(t -> {
                        User localUser = t.where(User.class).equalTo("userId", userId).findFirst();
                        if (localUser != null) {
                            localUser.setCloudId(savedUser.getObjectId());
                        }
                    });
                }
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onComplete() {}
        });
    }

    /**
     * 【新增】从云端拉取用户数据
     */
    private void fetchUserFromCloud(String userId, UserCallback callback) {
        LCQuery<LCObject> query = new LCQuery<>("AppUser");
        query.whereEqualTo("userId", userId);
        query.getFirstInBackground().subscribe(new Observer<LCObject>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onNext(LCObject lcUser) {
                if (lcUser != null) {
                    User user = DataConverter.toRealmUser(lcUser);
                    callback.onResult(user);
                } else {
                    callback.onResult(null);
                }
            }

            @Override
            public void onError(Throwable e) {
                callback.onResult(null);
            }

            @Override
            public void onComplete() {}
        });
    }
}
