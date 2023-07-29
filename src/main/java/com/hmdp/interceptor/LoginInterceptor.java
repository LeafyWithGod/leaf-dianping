package com.hmdp.interceptor;

import com.hmdp.utils.redisUtils.RedisCache;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 拦截器2：
 *      拦截所有请求，有用户的放行，没有用户的拦截
 */
public class LoginInterceptor implements HandlerInterceptor {

    private RedisCache redisCache;

    public LoginInterceptor() {
    }

    public LoginInterceptor(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}