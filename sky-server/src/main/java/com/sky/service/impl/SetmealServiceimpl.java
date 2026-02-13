package com.sky.service.impl;

import com.sky.dto.SetmealDTO;
import org.springframework.stereotype.Service;

@Service
public class SetmealServiceimpl {
    //新增套餐
    public void save(SetmealDTO setmealDTO) {
        //1.保存套餐信息，操作setmeal，执行insert操作
        //2.保存套餐和菜品的关联关系，操作setmeal_dish，执行insert操作
        //3.保存套餐和标签的关联关系，操作setmeal_flavor，执行insert操作
    }
}
