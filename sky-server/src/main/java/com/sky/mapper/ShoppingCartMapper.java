package com.sky.mapper;


import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    void updateById(ShoppingCart cart);

    void insert(ShoppingCart shoppingCart);
    

    void deleteByUserId(Long userId);

    void deleteById(ShoppingCart shoppingCart);
}
