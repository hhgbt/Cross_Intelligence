package com.example.cross_intelligence.mvc.view.register;

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
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityRegisterBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.controller.UserManager;
import com.example.cross_intelligence.mvc.model.User;
import com.example.cross_intelligence.mvc.util.UIUtil;

import java.util.Arrays;
import java.util.List;

public class RegisterActivity extends BaseActivity {

    private ActivityRegisterBinding binding;
    private final List<String> roleOptions = Arrays.asList("管理员", "选手");
    private final UserManager userManager = new UserManager();

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置沉浸式状态栏
        setupImmersiveStatusBar();
        
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
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
        binding.actRegisterRole.setAdapter(roleAdapter);
        // 设置弹出菜单的垂直偏移，使其与输入框有适当间距
        binding.actRegisterRole.setDropDownVerticalOffset(4);
        // MaterialAutoCompleteTextView 会自动处理点击和焦点事件

        // 密码显示/隐藏图标切换
        binding.tilRegisterPassword.setEndIconOnClickListener(v -> togglePasswordVisibility());

        // 注册按钮
        binding.btnRegister.setOnClickListener(v -> {
            animateButtonClick(binding.btnRegister);
            handleRegister();
        });
        
        // 返回登录按钮：跳转到登录页面
        binding.btnBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, com.example.cross_intelligence.mvc.view.login.LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }
    
    /**
     * 切换密码可见性
     */
    private void togglePasswordVisibility() {
        boolean isPasswordVisible = binding.etRegisterPassword.getTransformationMethod() == null;
        if (isPasswordVisible) {
            // 隐藏密码
            binding.etRegisterPassword.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
            binding.tilRegisterPassword.setEndIconDrawable(R.drawable.ic_visibility_off);
        } else {
            // 显示密码
            binding.etRegisterPassword.setTransformationMethod(null);
            binding.tilRegisterPassword.setEndIconDrawable(R.drawable.ic_visibility);
        }
        // 将光标移到文本末尾
        binding.etRegisterPassword.setSelection(binding.etRegisterPassword.getText() != null ? binding.etRegisterPassword.getText().length() : 0);
    }

    private void handleRegister() {
        clearErrors();

        String account = binding.etRegisterAccount.getText() != null
                ? binding.etRegisterAccount.getText().toString().trim() : "";
        String password = binding.etRegisterPassword.getText() != null
                ? binding.etRegisterPassword.getText().toString().trim() : "";
        String role = binding.actRegisterRole.getText() != null
                ? binding.actRegisterRole.getText().toString().trim() : "";

        boolean valid = true;
        // 账号格式验证
        if (TextUtils.isEmpty(account)) {
            binding.tilRegisterAccount.setError(getString(R.string.register_invalid_input));
            shakeView(binding.tilRegisterAccount);
            valid = false;
        } else if (account.length() > 20) {
            binding.tilRegisterAccount.setError("账号长度不得超过20个字符");
            shakeView(binding.tilRegisterAccount);
            valid = false;
        } else if (!account.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_]+$")) {
            binding.tilRegisterAccount.setError("账号只能包含中文、英文字母（大小写）、数字和下划线");
            shakeView(binding.tilRegisterAccount);
            valid = false;
        } else {
            binding.tilRegisterAccount.setError(null);
        }
        // 密码格式验证
        if (TextUtils.isEmpty(password)) {
            binding.tilRegisterPassword.setError(getString(R.string.register_invalid_input));
            shakeView(binding.tilRegisterPassword);
            valid = false;
        } else if (password.length() < 6) {
            binding.tilRegisterPassword.setError("密码不得少于6个字符");
            shakeView(binding.tilRegisterPassword);
            valid = false;
        } else if (!password.matches("^[a-zA-Z0-9_]+$")) {
            binding.tilRegisterPassword.setError("密码只能包含字母、数字和下划线");
            shakeView(binding.tilRegisterPassword);
            valid = false;
        } else {
            binding.tilRegisterPassword.setError(null);
        }
        if (TextUtils.isEmpty(role)) {
            binding.tilRegisterRole.setError(getString(R.string.register_invalid_input));
            shakeView(binding.tilRegisterRole);
            valid = false;
        } else {
            binding.tilRegisterRole.setError(null);
        }
        if (!valid) {
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        userManager.register(account, role, account, new UserManager.RegisterCallback() {
            @Override
            public void onSuccess(@NonNull User user) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    UIUtil.showToast(RegisterActivity.this, getString(R.string.register_success));
                    // 注册成功后返回登录页面
                    finish();
                });
            }

            @Override
            public void onFailure(@NonNull Throwable throwable) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (throwable.getMessage() != null && throwable.getMessage().contains("已存在")) {
                        binding.tilRegisterAccount.setError(getString(R.string.register_user_exists));
                    } else {
                        UIUtil.showToast(RegisterActivity.this, throwable.getMessage() != null
                                ? throwable.getMessage()
                                : getString(R.string.register_invalid_input));
                    }
                });
            }
        });
    }

    private void clearErrors() {
        binding.tilRegisterAccount.setError(null);
        binding.tilRegisterPassword.setError(null);
        binding.tilRegisterRole.setError(null);
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




