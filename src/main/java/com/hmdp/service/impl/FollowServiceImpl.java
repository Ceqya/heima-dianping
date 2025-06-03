package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        //1.判断是关注还是取关
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //关注成功后，向Redis中添加关注用户的ID sadd userId followUserId
                stringRedisTemplate.opsForSet().add("follows:" + userId, followUserId.toString());
            }
        }
        else {
            //3.取关，删除数据 delete from tb_follow where user_id = ? and follow_user_id = ?
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            //取关成功后，从Redis中删除关注用户的ID srem userId followUserId
            stringRedisTemplate.opsForSet().remove("follows:" + userId, followUserId.toString());

        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //1.查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        //获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();
        //2.判断
        if (count > 0) {
            //3.如果关注，返回true
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    @Override
    public Result followCommons(Long id) {
        //1. 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //2. 获取当前用户关注的用户ID集合
        String key2 = "follows:" + id;
        //3. 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            //如果没有交集，返回空
            return Result.ok(Collections.emptyList());

        }
        //4. 解析出id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //5.查询用户
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //6.返回结果
        return Result.ok(userDTOS);

    }
}
