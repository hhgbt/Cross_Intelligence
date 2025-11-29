package com.example.cross_intelligence.mvc.view.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoginFormValidatorTest {

    @Test
    public void validateAccount_adminRule() {
        assertEquals("管理员账号需以 admin 开头",
                LoginFormValidator.validateAccount("teacher01", "管理员"));
        assertNull(LoginFormValidator.validateAccount("admin001", "管理员"));
    }

    @Test
    public void validateAccount_playerRule() {
        assertEquals("选手账号需为学号（至少6位数字）",
                LoginFormValidator.validateAccount("123", "选手"));
        assertNull(LoginFormValidator.validateAccount("2023123456", "选手"));
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




