package com.hmdp.config;

import com.hmdp.utils.LoginIntercepter;
import com.hmdp.utils.RefreshTokenIntercepter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginIntercepter())
                .excludePathPatterns(
                        "/user/code", // 排除发送验证码的接口
                        "/user/login", // 排除登录接口
                        "/blog/hot", // 排除获取热门博客的接口
                        "/shop/**", // 排除商铺相关接口
                        "shop-type/**", // 排除商铺类型相关接口
                        "/upload/**", // 排除文件上传接口
                        "/voucher/**"// 排除代金券相关接口
                ).order(1);
        // 刷新token拦截器
        registry.addInterceptor(new RefreshTokenIntercepter(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
