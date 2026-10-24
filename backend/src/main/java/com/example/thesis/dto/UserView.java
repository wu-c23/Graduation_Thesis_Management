package com.example.thesis.dto;

import com.example.thesis.entity.User;

/**
 * 对外返回的用户信息，绝不包含 passwordHash。
 */
public record UserView(Long id, String username, String realName, String role) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}
