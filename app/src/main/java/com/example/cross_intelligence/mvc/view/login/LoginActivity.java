package com.example.cross_intelligence.mvc.view.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityLoginBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.UserManager;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.admin.AdminMainActivity;
import com.example.cross_intelligence.mvc.view.profile.UserSettingsActivity;

import java.util.Arrays;
import java.util.List;

public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private final List<String> roleOptions = Arrays.asList("管理员", "选手");
    private final UserManager userManager = new UserManager();

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding inflate
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }

    @Override
    protected void initView() {
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown_role, roleOptions);
        binding.actRole.setAdapter(roleAdapter);
        binding.actRole.setOnClickListener(v -> binding.actRole.showDropDown());
        binding.actRole.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.actRole.showDropDown();
            }
        });
        binding.tilRole.setEndIconOnClickListener(v -> binding.actRole.showDropDown());
        binding.tilRole.setOnClickListener(v -> binding.actRole.showDropDown());
        binding.btnLogin.setOnClickListener(v -> handleLogin());
        // 注册按钮：跳转到注册页面
        binding.btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, com.example.cross_intelligence.mvc.view.register.RegisterActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void initData() {
        String savedAccount = PreferenceUtil.getString(this, "account", "");
        String savedRole = PreferenceUtil.getString(this, "role", "");
        binding.etAccount.setText(savedAccount);
        binding.actRole.setText(savedRole, false);
    }

    private void handleLogin() {
        clearErrors();
        String account = binding.etAccount.getText() != null ? binding.etAccount.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
        String role = binding.actRole.getText() != null ? binding.actRole.getText().toString().trim() : "";

        boolean valid = true;
        String accountError = LoginFormValidator.validateAccount(account, role);
        String passwordError = LoginFormValidator.validatePassword(password);
        String roleError = LoginFormValidator.validateRole(role);
        if (accountError != null) {
            binding.tilAccount.setError(accountError);
            valid = false;
        } else {
            binding.tilAccount.setError(null);
        }
        if (passwordError != null) {
            binding.tilPassword.setError(passwordError);
            valid = false;
        } else {
            binding.tilPassword.setError(null);
        }
        if (roleError != null) {
            binding.tilRole.setError(roleError);
            valid = false;
        } else {
            binding.tilRole.setError(null);
        }
        if (!valid) {
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        // 示例身份验证：真实项目应请求服务器
        simulateVerification(account, password, role);
    }


    private void simulateVerification(String account, String password, String role) {
        userManager.login(account, password, role, new UserManager.LoginCallback() {
            @Override
            public void onSuccess(@NonNull User user) {
                saveLoginState(account, role);
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    UIUtil.showToast(LoginActivity.this, "登录成功：" + role);
                    // 根据角色跳转到不同的主页
                    Class<?> target;
                    if ("管理员".equals(role)) {
                        target = AdminMainActivity.class;
                    } else if ("选手".equals(role)) {
                        target = com.example.cross_intelligence.mvc.view.player.PlayerMainActivity.class;
                    } else {
                        target = UserSettingsActivity.class;
                    }
                    Intent intent = new Intent(LoginActivity.this, target);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onFailure(@NonNull Throwable throwable) {
                onLoginFailed(throwable);
            }
        });
    }

    private void saveLoginState(String account, String role) {
        PreferenceUtil.editor(this)
                .putString("account", account)
                .putString("role", role)
                .putBoolean("logged_in", true)
                .applyAsync();
    }

    private void onLoginFailed(Throwable throwable) {
        runOnUiThread(() -> {
            binding.progressBar.setVisibility(View.GONE);
            UIUtil.showToast(LoginActivity.this, throwable.getMessage() != null ? throwable.getMessage() : "登录失败");
        });
    }

    private void clearErrors() {
        binding.tilAccount.setError(null);
        binding.tilPassword.setError(null);
        binding.tilRole.setError(null);
    }
}

