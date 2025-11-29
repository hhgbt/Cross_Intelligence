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
        if ("管理员".equals(role) && !account.startsWith("admin")) {
            return "管理员账号需以 admin 开头";
        }
        if ("选手".equals(role) && !account.matches("\\d{6,}")) {
            return "选手账号需为学号（至少6位数字）";
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




