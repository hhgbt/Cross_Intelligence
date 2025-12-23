package com.example.cross_intelligence.mvc.view;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Intent;
import android.content.pm.ActivityInfo;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cross_intelligence.R;
import com.example.cross_intelligence.mvc.view.login.LoginActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UiAppearanceTest {

    @Before
    public void setup() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    @After
    public void tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Test
    public void loginDarkMode_componentsVisible() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
    }

    // 旧版排行榜 LeaderboardActivity 已被移除，且当前项目使用管理员结果页替代。
    // 为避免依赖已删除的 Activity，这里的相关 UI 测试一并移除，仅保留登录页暗色模式的可见性检查。
}

