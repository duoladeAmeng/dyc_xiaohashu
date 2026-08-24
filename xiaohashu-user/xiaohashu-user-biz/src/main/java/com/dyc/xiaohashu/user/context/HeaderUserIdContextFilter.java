package com.dyc.xiaohashu.user.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从请求头提取当前用户 ID，并写入当前请求上下文。
 */
@Component
@Slf4j
public class HeaderUserIdContextFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "userId";
    public static final String X_USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            userId = request.getHeader(X_USER_ID_HEADER);
        }

        if (!StringUtils.hasText(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            LoginUserContextHolder.setUserId(Long.valueOf(userId));
            filterChain.doFilter(request, response);
        } catch (NumberFormatException e) {
            log.warn("忽略非法用户 ID 请求头，{}={}, {}={}", USER_ID_HEADER,
                    request.getHeader(USER_ID_HEADER), X_USER_ID_HEADER, request.getHeader(X_USER_ID_HEADER));
            filterChain.doFilter(request, response);
        } finally {
            LoginUserContextHolder.remove();
        }
    }
}
