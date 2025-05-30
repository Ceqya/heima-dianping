//package com.hmdp;
//
//import lombok.extern.slf4j.Slf4j;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.TestInstance;
//import org.junit.runner.RunWith;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.junit4.SpringRunner;
//
//import javax.annotation.Resource;
//import java.util.concurrent.TimeUnit;
//
//@RunWith(SpringRunner.class) // JUnit4 下需要
//@SpringBootTest
//@Slf4j
//public class RedissonTest {
//
//    @Resource
//    private RedissonClient redissonClient;
//    @Resource
//    private RedissonClient redissonClient2;
//    @Resource
//    private RedissonClient redissonClient3;
//
//    private RLock lock;
//
//    @Before
//    public void setUp() {
//        RLock lock1 = redissonClient.getLock("order");
//        RLock lock2 = redissonClient2.getLock("order");
//        RLock lock3 = redissonClient3.getLock("order");
//
//        //创建连锁 multi-lock
//        lock = redissonClient.getMultiLock(lock1, lock2, lock3);
//    }
//    @Test
//    public void method1() throws InterruptedException {
//
//        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
//        if (!isLock) {
//            log.error("获取锁失败");
//            return;
//        }
//        try {
//            log.info("获取锁成功，执行方法1");
//            method2();
//            log.info("方法1执行");
//        } finally {
//            log.warn("释放锁");
//            lock.unlock();
//        }
//    }
//    @Test
//    public void method2() throws InterruptedException {
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            log.error("获取锁失败");
//            return;
//        }
//        try {
//            log.info("获取锁成功，执行方法2");
//
//            log.info("方法2执行");
//        } finally {
//            log.warn("释放锁");
//            lock.unlock();
//        }
//    }
//
//}
