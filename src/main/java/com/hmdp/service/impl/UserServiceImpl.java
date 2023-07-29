package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.redisUtils.RedisCache;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.redisUtils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Leaf
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private RedisCache redisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式不正确");
        }
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.setCacheObject(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("验证码发送成功："+code);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if(phone==null){
            return Result.fail("手机号不能为空");
        }
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式不正确");
        }
        String code = redisTemplate.getCacheObject(LOGIN_CODE_KEY + phone);
        if (code==null||!code.equals(loginForm.getCode())){
            return Result.fail("验证码不正确");
        }
        User user = query().eq("phone", phone).one();
        if (user==null){
            return Result.fail("账号为空");
        }
        String token = JwtUtil.createJWT(phone);
        redisTemplate.setCacheObject(token,BeanUtil.copyProperties(user,UserDTO.class),LOGIN_USER_TTL,TimeUnit.SECONDS);
        return Result.ok(token);
    }
}
