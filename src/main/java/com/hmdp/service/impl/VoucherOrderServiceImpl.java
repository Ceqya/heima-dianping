package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {

                try {
                    //1.获取消息队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果获取成功，说明有消息，创建订单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACk stream.orders gl id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePandingList();
                }
            }
        }

        private void handlePandingList() {
            while (true) {
                try {
                    //1.获取pending-list中的订单信息 xreadgroup group g1 c1 count 1 streams.order 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0")) // 从头部开始读取
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //2.1如果获取失败，说明pending-list没有异常消息，结束循环
                        break;
                    }
                    //3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果获取成功，说明有消息，创建订单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACk stream.orders gl id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订pending-list异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                //1.获取订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.创建订单
//                    handleVoucherOrder(voucherOrder);
//
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//                //2.创建订单
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户id
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();

        //4.判断是否获取锁
        if (!isLock) {
            // 获取锁失败
            log.error("不允许重复下单，用户id: {}", userId);
            return ;
        }

        //8.返回订单id
        //synchronized (userId.toString().intern()) {
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
        //}
    }
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id和订单id
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //1.只需lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString(), String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0){
            //2.1不为0，代表没有购买资格
            return Result.fail( r ==1 ? "库存不足" : "用户已购买过该优惠券");
        }

        //3.获取代理对象
        //获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();


        //3.返回订单id
        return Result.ok(orderId);
    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.只需lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), UserHolder.getUser().getId().toString()
//        );
//        //2.判断结果是否为0
//        int r = result.intValue();
//        if (r != 0){
//            //2.1不为0，代表没有购买资格
//            return Result.fail( r ==1 ? "库存不足" : "用户已购买过该优惠券");
//        }
//        //2.2为0，代表有购买资格，把下单信息保存到阻塞队列当中
//        long orderId = redisIdWorker.nextId("order");
//
//        Long userId = UserHolder.getUser().getId();
//
//        //2.3创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //2.4订单id
//        voucherOrder.setId(orderId);
//        //2.5用户id
//        voucherOrder.setUserId(userId);
//        //2.6代金券id
//        voucherOrder.setVoucherId(voucherId);
//        //保存阻塞队列
//        //2.6放入阻塞队列
//        orderTasks.add(voucherOrder);
//        //3.获取代理对象
//        //获取代理对象（事务）
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//
//        //3.返回订单id
//        return Result.ok(orderId);
//    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始或结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //2.尚未开始
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //3.已结束
//            return Result.fail("秒杀已结束");
//        }
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1){
//            //库存不足
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//
//        //判断是否获取锁
//        if (!isLock) {
//            // 获取锁失败
//            return Result.fail("不允许重复下单");
//        }
//
//        //8.返回订单id
//        //synchronized (userId.toString().intern()) {
//        try{
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//        //}
//    }
    //悲观锁实现一人一单，锁加在整个方法上
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单判断
        Long userId = voucherOrder.getUserId();

            //查询订单
            int count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .count();
            //判断是否存在
            if( count > 0) {
                //存在，说明用户已经购买过了
                log.error("用户已购买过该优惠券，用户id: {}", userId);
                return ;
            }

            //6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())//set stock = stock - 1
                    .gt("stock", 0)// where voucher_id = ? and stock > 0
                    .update();
            if(!success) {
                //扣减失败，可能是库存不足
                log.error("库存不足，用户id: {}, 代金券id: {}", userId, voucherOrder.getVoucherId());
                return ;
            }

            //7.创建订单

            save(voucherOrder);


    }

}
