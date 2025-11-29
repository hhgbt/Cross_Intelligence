package com.example.cross_intelligence.mvc.view.welcome;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.databinding.ActivityWelcomeBinding;
import com.example.cross_intelligence.mvc.base.BaseActivity;
import com.example.cross_intelligence.mvc.view.login.LoginActivity;
import com.example.cross_intelligence.mvc.view.register.RegisterActivity;

public class WelcomeActivity extends BaseActivity {

    private ActivityWelcomeBinding binding;

    @Override
    protected int getLayoutId() {
        return 0; // 使用 ViewBinding
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
    }

    @Override
    protected void initView() {
        // 登录按钮：跳转到登录页面
        binding.btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // 注册按钮：跳转到注册页面
        binding.btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}

