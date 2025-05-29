package com.hmdp.utils;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 雪花算法的起始时间戳（2022-01-01 00:00:00 UTC）
     * 注意：这里的时间戳是以秒为单位的
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L; // 2022-01-01 00:00:00 UTC
    private static final int COUNT_BITS = 32; // 序列号位数

    //只是用redis实现全局唯一id，并返回利用redis生成的id，并没有把id存到redis中，但是可以通关redis的自增功能看到当天有多少个id被生成
    public long nextId(String keyPrefix) {
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP; // 计算自起始时间以来的秒数

        //生成序列号
        //获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接并返回
        return timestamp << COUNT_BITS | count;
    }


}
