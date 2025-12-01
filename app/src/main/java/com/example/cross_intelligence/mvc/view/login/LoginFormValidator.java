package com.example.cross_intelligence.mvc.view.login;

import android.text.TextUtils;

import androidx.annotation.Nullable;

final class LoginFormValidator {

    private LoginFormValidator() {
    }

    @Nullable
    static String validateAccount(String account, String role) {
        if (TextUtils.isEmpty(account)) {
            return "请输入账号";
        }
        // 账号格式验证：允许数字、大小写字母、下划线
        if (!account.matches("^[a-zA-Z0-9_]+$")) {
            return "账号只能包含数字、字母和下划线";
        }
        return null;
    }

    @Nullable
    static String validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return "请输入密码";
        }
        if (password.length() < 6) {
            return "密码至少6位";
        }
        return null;
    }

    @Nullable
    static String validateRole(String role) {
        if (TextUtils.isEmpty(role)) {
            return "请选择角色";
        }
        if (!("管理员".equals(role) || "选手".equals(role))) {
            return "角色无效";
        }
        return null;
    }
}





