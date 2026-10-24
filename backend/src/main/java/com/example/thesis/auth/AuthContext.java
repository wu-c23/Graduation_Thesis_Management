package com.example.thesis.auth;

import com.example.thesis.common.ApiException;
import com.example.thesis.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 把通过拦截器校验的用户信息挂到当前请求上，供 Controller 读取。
 */
public final class AuthContext {

    private static final String USER_ATTRIBUTE = AuthContext.class.getName() + ".user";

    private AuthContext() {
    }

    public static void set(HttpServletRequest request, UserView user) {
        request.setAttribute(USER_ATTRIBUTE, user);
    }

    public static UserView get(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ATTRIBUTE);
        if (value instanceof UserView user) {
            return user;
        }
        throw ApiException.unauthorized("请先登录");
    }
}
