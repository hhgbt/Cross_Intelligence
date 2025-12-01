package com.example.cross_intelligence.mvc.view.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoginFormValidatorTest {

    @Test
    public void validateAccount_format() {
        // 空账号
        assertEquals("请输入账号", LoginFormValidator.validateAccount("", "管理员"));
        assertEquals("请输入账号", LoginFormValidator.validateAccount(null, "选手"));
        
        // 有效账号：数字、字母、下划线组合
        assertNull(LoginFormValidator.validateAccount("admin001", "管理员"));
        assertNull(LoginFormValidator.validateAccount("teacher01", "管理员"));
        assertNull(LoginFormValidator.validateAccount("user_123", "选手"));
        assertNull(LoginFormValidator.validateAccount("ABC123", "选手"));
        assertNull(LoginFormValidator.validateAccount("test_user", "管理员"));
        assertNull(LoginFormValidator.validateAccount("2023123456", "选手"));
        
        // 无效账号：包含特殊字符（除了下划线）
        assertEquals("账号只能包含数字、字母和下划线",
                LoginFormValidator.validateAccount("user@123", "管理员"));
        assertEquals("账号只能包含数字、字母和下划线",
                LoginFormValidator.validateAccount("user-123", "选手"));
        assertEquals("账号只能包含数字、字母和下划线",
                LoginFormValidator.validateAccount("user.123", "管理员"));
        assertEquals("账号只能包含数字、字母和下划线",
                LoginFormValidator.validateAccount("user 123", "选手"));
        assertEquals("账号只能包含数字、字母和下划线",
                LoginFormValidator.validateAccount("用户123", "管理员"));
    }

    @Test
    public void validatePassword_length() {
        assertEquals("密码至少6位", LoginFormValidator.validatePassword("123"));
        assertNull(LoginFormValidator.validatePassword("123456"));
    }

    @Test
    public void validateRole_required() {
        assertEquals("请选择角色", LoginFormValidator.validateRole(""));
        assertNull(LoginFormValidator.validateRole("管理员"));
    }
}





