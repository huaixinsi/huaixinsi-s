package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);
    void saveDish(Dish dish);

    void saveDishFlavor(List<DishFlavor> flavors);

    Page<DishVO> page(DishPageQueryDTO dishPageQueryDTO);

    // 批量删除菜品（根据菜品ID列表）
    void deleteDish(@Param("ids") List<Long> ids);

    // 批量删除菜品口味（根据菜品ID列表）
    void deleteDishFlavor(@Param("ids") List<Long> ids);

    DishVO getByIdWithFlavor(Long id);
    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);
    List<Dish> list(Dish dish);
}
