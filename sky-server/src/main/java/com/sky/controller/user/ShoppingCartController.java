package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@ApiOperation("用户购物车接口")
@RequestMapping("/user/shoppingCart")
public class ShoppingCartController {
    @Autowired
    private ShoppingCartService shoppingCartService;
    @ApiOperation("添加购物车")
    @RequestMapping("/add")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("添加购物车");
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }
    @ApiOperation("查看购物车")
    @RequestMapping("/list")
    public Result<List<ShoppingCart>> list(){
        return Result.success(shoppingCartService.list());
    }
    @ApiOperation("清空购物车")
    @RequestMapping("/clean")
    public Result clean(){
        shoppingCartService.clean();
        return Result.success();
    }
    @ApiOperation("删除购物车中的一个商品")
    @RequestMapping("/sub")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO){
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
}
