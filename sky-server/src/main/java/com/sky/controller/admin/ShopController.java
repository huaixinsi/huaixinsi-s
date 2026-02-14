package com.sky.controller.admin;

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
class AdminShopController {
    @Autowired
    private RedisTemplate redisTemplate;
    //管理端营业状态设置
    @ApiOperation("设置店铺的营业状态")
    @PutMapping("/admin/shop/{status}")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置店铺营业状态{}",status==1?"营业中":"打烊中");
        redisTemplate.opsForValue().set("SHOP_STATUS",status);
        return Result.success();
    }
    @ApiOperation("获取店铺的营业状态")
    @GetMapping("/admin/shop/status")
    public Result<Integer> getStatus(){
        Integer shopStatus = (Integer) redisTemplate.opsForValue().get("SHOP_STATUS");
        // 添加null检查
        if (shopStatus == null) {
            log.info("获取店铺营业状态：未设置，默认为打烊中");
            shopStatus = 0; // 默认状态为打烊中
        } else {
            log.info("获取店铺营业状态{}",shopStatus==1?"营业中":"打烊中");
        }
        return Result.success(shopStatus);
    }
}
