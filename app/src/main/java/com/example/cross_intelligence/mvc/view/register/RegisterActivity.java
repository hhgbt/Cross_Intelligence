package com.example.cross_intelligence.mvc.view.register;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

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
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();
    }

    @Override
    protected void initView() {
        // 角色下拉框设置
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown_role, roleOptions);
        binding.actRegisterRole.setAdapter(roleAdapter);
        binding.actRegisterRole.setOnClickListener(v -> binding.actRegisterRole.showDropDown());
        binding.actRegisterRole.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.actRegisterRole.showDropDown();
            }
        });
        binding.tilRegisterRole.setEndIconOnClickListener(v -> binding.actRegisterRole.showDropDown());
        binding.tilRegisterRole.setOnClickListener(v -> binding.actRegisterRole.showDropDown());

        // 注册按钮
        binding.btnRegister.setOnClickListener(v -> handleRegister());
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
        if (TextUtils.isEmpty(account)) {
            binding.tilRegisterAccount.setError(getString(R.string.register_invalid_input));
            valid = false;
        } else {
            binding.tilRegisterAccount.setError(null);
        }
        if (TextUtils.isEmpty(password)) {
            binding.tilRegisterPassword.setError(getString(R.string.register_invalid_input));
            valid = false;
        } else {
            binding.tilRegisterPassword.setError(null);
        }
        if (TextUtils.isEmpty(role)) {
            binding.tilRegisterRole.setError(getString(R.string.register_invalid_input));
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
}



