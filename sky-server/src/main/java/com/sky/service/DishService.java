package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.vo.DishItemVO;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {
    void saveWithFlavor(DishDTO dishDTO);
    PageResult<DishVO> page(DishPageQueryDTO dishPageQueryDTO);
    void delete(List<Long> ids);

    DishVO getByIdWithFlavor(Long id);

    void updateStatus(Integer status, Long id);

    void updateWithFlavor(DishDTO dishDTO);

    List<DishVO> listWithFlavor(Dish dish);




}
