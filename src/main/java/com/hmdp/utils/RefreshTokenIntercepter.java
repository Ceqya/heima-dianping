package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenIntercepter implements HandlerInterceptor {


    public RefreshTokenIntercepter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.获取token中的用户信息
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token))
        {
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 2.判断用户是否存在
        if (userMap.isEmpty()) {
            // 3.如果不存在，拦截，返回错误信息
            return true; // 阻止请求继续
        }
        // 4.将查询到的hash数据转为userDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(), false);

        // 5.如果存在，保存用户信息到ThreadLocal中
        UserHolder.saveUser(userDTO);
        //6.刷新token有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        //7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}