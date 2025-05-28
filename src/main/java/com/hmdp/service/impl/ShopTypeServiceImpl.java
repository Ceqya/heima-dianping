package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result show() {

        String key = "SHOP_TYPE";
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);

        // 判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> shopTypesList = JSONUtil.toList(shopTypeJson, ShopType.class);
            Result.ok(shopTypesList);
        }
        // 如果redis中不存在，根据id查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        // 如果数据库中不存在，返回错误信息
        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("商铺类型不存在");
        }
        // 如果存在，将数据写入redis
        String shopTypeJsonStr = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(key, shopTypeJsonStr);
        // 返回商铺类型信息
        return Result.ok(shopTypes);

    }
}
