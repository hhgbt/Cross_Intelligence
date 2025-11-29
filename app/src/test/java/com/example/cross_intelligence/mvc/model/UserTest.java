package com.example.cross_intelligence.mvc.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserTest {

    @Test
    public void userFields_persistValues() {
        User user = new User();
        user.setUserId("20230001");
        user.setName("越野选手");
        user.setRole("选手");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setPhone("13800000000");
        user.setEmail("runner@example.com");
        user.setBio("测试简介");

        assertEquals("20230001", user.getUserId());
        assertEquals("越野选手", user.getName());
        assertEquals("选手", user.getRole());
        assertEquals("https://example.com/avatar.png", user.getAvatarUrl());
        assertEquals("13800000000", user.getPhone());
        assertEquals("runner@example.com", user.getEmail());
        assertEquals("测试简介", user.getBio());
    }
}




