package com.example.cross_intelligence.mvc.view.welcome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

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
        startAnimations();
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

    /**
     * 启动所有动画效果
     */
    private void startAnimations() {
        // 1. 背景图呼吸动画（缓慢缩放）
        startBackgroundBreathingAnimation();

        // 2. 标题渐显动画（延迟0.5秒，从下方浮现）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startTitleFadeInAnimation();
        }, 500);
    }

    /**
     * 背景图呼吸动画：缓慢缩放，模拟呼吸感
     */
    private void startBackgroundBreathingAnimation() {
        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(1.0f, 1.05f, 1.0f);
        scaleAnimator.setDuration(8000); // 8秒一个周期
        scaleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scaleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleAnimator.addUpdateListener(animation -> {
            float scale = (Float) animation.getAnimatedValue();
            binding.ivBackground.setScaleX(scale);
            binding.ivBackground.setScaleY(scale);
        });
        scaleAnimator.start();
    }

    /**
     * 标题渐显动画：从下方浮现并变亮
     */
    private void startTitleFadeInAnimation() {
        // 设置初始状态：向下偏移并透明
        binding.titleContainer.setTranslationY(80f);
        binding.titleContainer.setAlpha(0f);
        binding.titleContainer.setVisibility(View.VISIBLE);

        // 渐显动画
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(binding.titleContainer, "alpha", 0f, 1f);
        fadeIn.setDuration(1000);
        fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());

        // 上浮动画
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(binding.titleContainer, "translationY", 80f, 0f);
        slideUp.setDuration(1000);
        slideUp.setInterpolator(new AccelerateDecelerateInterpolator());

        // 同时执行
        fadeIn.start();
        slideUp.start();
    }
}

