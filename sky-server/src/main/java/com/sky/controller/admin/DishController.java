package com.sky.controller.admin;

import com.sky.annotation.RedisCacheEvitctById;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.impl.DishServiceimpl;
import com.sky.vo.DishVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
public class DishController {
    @Autowired
    private DishServiceimpl dishService;

    @RedisCacheEvitctById
    @ApiOperation("新增菜品")
    @PostMapping("/admin/dish")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);
        return Result.success();
    }

    @ApiOperation("菜品分页查询")
    @GetMapping("/admin/dish/page")
    public Result page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("分页查询：{}", dishPageQueryDTO);
        PageResult<DishVO> pageResult = dishService.page(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    @RedisCacheEvitctById
    @ApiOperation("批量删除菜品")
    @DeleteMapping("/admin/dish")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除菜品：{}", ids);
        dishService.delete(ids);
        return Result.success();
    }

    @ApiOperation("根据id查询菜品和对应的口味")
    @GetMapping("/admin/dish/{id}")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据id查询菜品和口味：{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }
    @RedisCacheEvitctById
    @ApiOperation("修改菜品的起售和停售")
    @PostMapping("/admin/dish/status/{status}")
    public Result updateStatus(@RequestParam Long id,@PathVariable Integer status) {
        log.info("修改菜品状态：{}", id);
        dishService.updateStatus(status, id);
        return Result.success();
    }
    @RedisCacheEvitctById
    @ApiOperation("修改菜品")
    @PutMapping("/admin/dish")
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);
        return Result.success();
    }
    @GetMapping("/admin/dish/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId) {
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }
}
