package com.example.cross_intelligence.mvc.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Fragment 基类，统一处理懒加载与 View 绑定。
 */
public abstract class BaseFragment extends Fragment {

    private View rootView;
    private boolean hasLoadedData;

    @LayoutRes
    protected abstract int getLayoutId();

    protected abstract void initView(@NonNull View root);

    protected void loadDataOnce() {
        // 子类按需覆盖，默认不做任何处理
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (rootView == null && getLayoutId() != 0) {
            rootView = inflater.inflate(getLayoutId(), container, false);
            initView(rootView);
        }
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasLoadedData) {
            loadDataOnce();
            hasLoadedData = true;
        }
    }
}




