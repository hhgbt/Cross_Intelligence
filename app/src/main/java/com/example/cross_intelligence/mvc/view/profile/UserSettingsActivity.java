package com.example.cross_intelligence.mvc.view.profile;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cross_intelligence.databinding.ActivityUserSettingsBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.UserManager;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;

/**
 * 用户设置页面：编辑姓名、电话、邮箱、简介并持久化到 Realm。
 */
public class UserSettingsActivity extends BaseActivity {

    private ActivityUserSettingsBinding binding;
    private final UserManager userManager = new UserManager();
    private String currentUserId;
    private User editingUser;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    @Override
    protected void initData() {
        currentUserId = PreferenceUtil.getString(this, "account", "");
        if (TextUtils.isEmpty(currentUserId)) {
            UIUtil.showToast(this, "请先登录");
            finish();
            return;
        }
        userManager.fetchUser(currentUserId, user -> {
            editingUser = user;
            if (editingUser != null) {
                binding.etName.setText(editingUser.getName());
                binding.etPhone.setText(editingUser.getPhone());
                binding.etEmail.setText(editingUser.getEmail());
                binding.etBio.setText(editingUser.getBio());
            }
        });
    }

    private void saveProfile() {
        if (editingUser == null) {
            editingUser = new User();
            editingUser.setUserId(currentUserId);
        }
        editingUser.setName(binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "");
        editingUser.setPhone(binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "");
        editingUser.setEmail(binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "");
        editingUser.setBio(binding.etBio.getText() != null ? binding.etBio.getText().toString().trim() : "");

        userManager.updateProfile(editingUser, new UserManager.CompletionCallback() {
            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    UIUtil.showToast(UserSettingsActivity.this, "保存成功");
                    finish();
                });
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                runOnUiThread(() -> UIUtil.showToast(UserSettingsActivity.this,
                        throwable.getMessage() != null ? throwable.getMessage() : "保存失败"));
            }
        });
    }
}

