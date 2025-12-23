package com.example.cross_intelligence.mvc.view.login;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.CycleInterpolator;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityLoginBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.UserManager;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.mvc.util.PreferenceUtil;
import com.example.cross_intelligence.mvc.util.UIUtil;
import com.example.cross_intelligence.mvc.view.admin.AdminMainActivity;

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
        
        // 设置沉浸式状态栏
        setupImmersiveStatusBar();
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
        initData();
    }
    
    /**
     * 设置状态栏：白色背景，深色图标
     */
    private void setupImmersiveStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            
            // 设置状态栏为白色
            window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            
            // Android 6.0+ 根据背景颜色设置状态栏图标颜色
            // 白色背景使用深色图标
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                // 使用新的 WindowInsetsController API (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.getInsetsController().setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    // 兼容旧版本 (Android 6.0 - 10)
                    decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    );
                }
            }
        }
    }

    @Override
    protected void initView() {
        // 角色下拉框设置：MaterialAutoCompleteTextView 已内置更好的交互
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown_role, roleOptions);
        binding.actRole.setAdapter(roleAdapter);
        // 设置弹出菜单的垂直偏移，使其与输入框有适当间距
        binding.actRole.setDropDownVerticalOffset(4);
        // MaterialAutoCompleteTextView 会自动处理点击和焦点事件
        
        // 密码显示/隐藏图标切换
        binding.tilPassword.setEndIconOnClickListener(v -> togglePasswordVisibility());
        
        binding.btnLogin.setOnClickListener(v -> {
            // 按钮点击动画
            animateButtonClick(binding.btnLogin);
            handleLogin();
        });
        // 注册按钮：跳转到注册页面
        binding.btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, com.example.cross_intelligence.mvc.view.register.RegisterActivity.class);
            startActivity(intent);
        });
    }
    
    /**
     * 切换密码可见性
     */
    private void togglePasswordVisibility() {
        boolean isPasswordVisible = binding.etPassword.getTransformationMethod() == null;
        if (isPasswordVisible) {
            // 隐藏密码
            binding.etPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
            binding.tilPassword.setEndIconDrawable(R.drawable.ic_visibility_off);
        } else {
            // 显示密码
            binding.etPassword.setTransformationMethod(null);
            binding.tilPassword.setEndIconDrawable(R.drawable.ic_visibility);
        }
        // 将光标移到文本末尾
        binding.etPassword.setSelection(binding.etPassword.getText() != null ? binding.etPassword.getText().length() : 0);
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
            shakeView(binding.tilAccount);
            valid = false;
        } else {
            binding.tilAccount.setError(null);
        }
        if (passwordError != null) {
            binding.tilPassword.setError(passwordError);
            shakeView(binding.tilPassword);
            valid = false;
        } else {
            binding.tilPassword.setError(null);
        }
        if (roleError != null) {
            binding.tilRole.setError(roleError);
            shakeView(binding.tilRole);
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
                        // 未知角色，默认跳转到管理员主页
                        target = AdminMainActivity.class;
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

    /**
     * 抖动动画 - 用于表单验证失败
     */
    private void shakeView(View view) {
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX", 0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
        shake.setDuration(500);
        shake.start();
    }

    /**
     * 按钮点击动画 - 轻微收缩再弹起
     */
    private void animateButtonClick(View button) {
        // 缩小动画
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.95f);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);

        // 弹起动画
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.95f, 1.0f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.95f, 1.0f);
        scaleUpX.setDuration(100);
        scaleUpY.setDuration(100);

        // 组合动画
        AnimatorSet downSet = new AnimatorSet();
        downSet.playTogether(scaleDownX, scaleDownY);

        AnimatorSet upSet = new AnimatorSet();
        upSet.playTogether(scaleUpX, scaleUpY);

        AnimatorSet fullSet = new AnimatorSet();
        fullSet.playSequentially(downSet, upSet);
        fullSet.start();
    }
}

