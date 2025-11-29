package com.example.cross_intelligence.mvc.base;

import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.transition.platform.MaterialSharedAxis;

/**
 * 所有 Activity 的基类，统一处理生命周期日志、视图初始化和数据监听绑定。
 */
public abstract class BaseActivity extends AppCompatActivity {

    @LayoutRes
    protected abstract int getLayoutId();

    /**
     * 初始化 View，绑定监听器。
     */
    protected abstract void initView();

    /**
     * 绑定观察者或加载初始数据。
     */
    protected void initData() {
        // 默认空实现，子类按需覆盖
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (enableSharedAxisTransition()) {
            getWindow().setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
            getWindow().setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        }
        super.onCreate(savedInstanceState);
        int layoutId = getLayoutId();
        boolean shouldAutoInit = layoutId != 0;
        if (layoutId != 0) {
            setContentView(layoutId);
        }
        if (shouldAutoInit) {
            initView();
            initData();
        }
    }

    protected boolean enableSharedAxisTransition() {
        return true;
    }
}

