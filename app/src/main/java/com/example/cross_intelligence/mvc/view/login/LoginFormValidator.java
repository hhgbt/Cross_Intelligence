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
        if (account.length() > 20) {
            return "账号长度不得超过20个字符";
        }
        // 账号格式验证：允许中文、数字、大小写字母、下划线
        if (!account.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_]+$")) {
            return "账号只能包含中文、英文字母（大小写）、数字和下划线";
        }
        return null;
    }

    @Nullable
    static String validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return "请输入密码";
        }
        if (password.length() < 6) {
            return "密码不得少于6个字符";
        }
        // 密码格式验证：只允许字母、数字、下划线
        if (!password.matches("^[a-zA-Z0-9_]+$")) {
            return "密码只能包含字母、数字和下划线";
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





