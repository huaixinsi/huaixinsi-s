package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@ApiOperation("店铺相关接口")
class UserShopController {
    @Autowired
    private RedisTemplate redisTemplate;
    //用户端营业状态查询
    @ApiOperation("查询店铺的营业状态")
    @GetMapping("/user/shop/status")
    public Result<Integer> getStatusOnUser(){
        Integer shopStatus = (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
        log.info("查询店铺营业状态{}",shopStatus==1?"营业中":"打烊中");
        return Result.success(shopStatus);
    }
}
