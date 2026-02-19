package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ShoppingCartServiceimpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 统一的购物车过期时间（分钟）
    private static final long CART_EXPIRE_MINUTES = 30L;

    /**
     * 添加商品到购物车（菜品或套餐）
     */
    public void add(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());

        String cartKey = cartKey();

        // 判断购物车中是否已存在该商品
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && !list.isEmpty()) {
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateById(cart);

            // 同步更新Redis缓存
            String updateField = itemField(cart);
            writeToRedis(cartKey, updateField, cart);
            return;
        }

        // 购物车中没有该商品，初始化并插入
        String itemField;
        if (shoppingCart.getDishId() != null) {
            itemField = "dish_" + shoppingCart.getDishId();
            DishVO dishvo = dishMapper.getByIdWithFlavor(shoppingCart.getDishId());
            shoppingCart.setAmount(dishvo.getPrice());
            shoppingCart.setImage(dishvo.getImage());
            shoppingCart.setName(dishvo.getName());
        } else {
            Long setmealId = shoppingCart.getSetmealId();
            itemField = "setmeal_" + setmealId;
            Setmeal setmeal = setmealMapper.getById(setmealId);
            shoppingCart.setAmount(setmeal.getPrice());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setName(setmeal.getName());
        }
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());

        shoppingCartMapper.insert(shoppingCart);
        // 保存到Redis
        writeToRedis(cartKey, itemField, shoppingCart);
    }

    /**
     * 返回当前用户购物车列表，优先走缓存
     */
    public List<ShoppingCart> list() {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        String cartKey = cartKey();

        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();
        Map<String, Object> cartMap = hashOps.entries(cartKey);
        List<ShoppingCart> cartList = new ArrayList<>();
        if (!cartMap.isEmpty()) {
            for (Object value : cartMap.values()) {
                if (value instanceof ShoppingCart) {
                    cartList.add((ShoppingCart) value);
                }
            }
            return cartList;
        }

        // 缓存未命中，查询数据库并回写缓存
        cartList = shoppingCartMapper.list(shoppingCart);
        if (cartList != null && !cartList.isEmpty()) {
            for (ShoppingCart cart : cartList) {
                String itemField = itemField(cart);
                hashOps.put(cartKey, itemField, cart);
            }
            redisTemplate.expire(cartKey, CART_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
        return cartList;
    }

    @Transactional(rollbackFor = Exception.class)
    public void clean() {
        Long userId = BaseContext.getCurrentId();
        String cartKey = "user_" + userId + "_shopping_cart";
        redisTemplate.delete(cartKey);
        shoppingCartMapper.deleteByUserId(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        shoppingCart.setDishId(shoppingCartDTO.getDishId());
        shoppingCart.setSetmealId(shoppingCartDTO.getSetmealId());

        List<ShoppingCart> cartList = shoppingCartMapper.list(shoppingCart);
        if (cartList != null && !cartList.isEmpty()) {
            ShoppingCart cart = cartList.get(0);
            cart.setNumber(cart.getNumber() - 1);

            String cartKey = cartKey();
            String itemField = itemField(cart);

            HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();

            if (cart.getNumber() > 0) {
                shoppingCartMapper.updateById(cart);
                writeToRedis(cartKey, itemField, cart);
            } else if (cart.getNumber() == 0) {
                shoppingCartMapper.deleteById(shoppingCart);
                hashOps.delete(cartKey, itemField);
                // 保持和其它写入位置一致的过期策略（如果仍存在其他字段）
                redisTemplate.expire(cartKey, CART_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
        }
    }

    // --- helper methods ---
    private String cartKey() {
        return "user_" + BaseContext.getCurrentId() + "_shopping_cart";
    }

    private String itemField(ShoppingCart cart) {
        return cart.getDishId() != null ? "dish_" + cart.getDishId() : "setmeal_" + cart.getSetmealId();
    }

    private void writeToRedis(String cartKey, String field, ShoppingCart cart) {
        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();
        hashOps.put(cartKey, field, cart);
        redisTemplate.expire(cartKey, CART_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }
}
