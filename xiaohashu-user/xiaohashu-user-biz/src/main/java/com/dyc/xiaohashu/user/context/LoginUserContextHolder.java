package com.dyc.xiaohashu.user.context;

/**
 * 当前登录用户上下文。
 */
public final class LoginUserContextHolder {

    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    private LoginUserContextHolder() {
    }

    public static void setUserId(Long userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    public static void remove() {
        USER_ID_THREAD_LOCAL.remove();
    }
}
