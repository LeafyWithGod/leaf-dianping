package com.hmdp.interceptor;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.redisUtils.RedisCache;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.redisUtils.RedisConstants.LOGIN_USER_TTL;

/**
 * 拦截器1：
 *      拦截所有请求，有用户的获取用户放行，没有用户的直接放行
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private RedisCache RedisTemplate;

    public RefreshTokenInterceptor(RedisCache stringRedisTemplate) {
        this.RedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 2.基于TOKEN获取redis中的用户
        UserDTO userDTO= RedisTemplate.getCacheObject(token);
        // 3.判断用户是否存在
        if (userDTO==null) {
            return true;
        }
        // 6.存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7.刷新token有效期
        RedisTemplate.expire(token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 8.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}