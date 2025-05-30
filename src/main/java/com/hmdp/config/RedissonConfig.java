package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 创建配置
        Config config = new Config();
        // 使用单机模式
        config.useSingleServer()
                .setAddress("redis://localhost:6379");
        // 创建RedissonClient实例
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient2(){
        // 创建配置
        Config config = new Config();
        // 使用单机模式
        config.useSingleServer()
                .setAddress("redis://localhost:6380");
        // 创建RedissonClient实例
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient3(){
        // 创建配置
        Config config = new Config();
        // 使用单机模式
        config.useSingleServer()
                .setAddress("redis://localhost:6381");
        // 创建RedissonClient实例
        return Redisson.create(config);
    }

}
