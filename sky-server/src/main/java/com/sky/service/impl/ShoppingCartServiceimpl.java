package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.SetmealService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
    private RedisTemplate redisTemplate;
    public void add(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        //创建一个Redis的Key：user_用户ID_shopping_cart
        String cartKey = "user_" + BaseContext.getCurrentId()+"_shopping_cart";
        //判断购物车中是否有该菜品或套餐
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //用商品唯一标识作为Hash的field：菜品用dishId，套餐用setmealId
        String itemField = null;
        if(list != null && list.size() > 0){
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateById(cart);
            // 同步更新Redis缓存
            String updateField = cart.getDishId() != null ? "dish_" + cart.getDishId() : "setmeal_" + cart.getSetmealId();
            redisTemplate.opsForHash().put(cartKey, updateField, cart);
            redisTemplate.expire(cartKey, 30, TimeUnit.MINUTES);
        }
        //如果购物车中没有该菜品或套餐
        else{
            //判断添加的是菜品还是套餐
            if(shoppingCart.getDishId() != null){
                //添加的是菜品
                itemField = "dish_" + shoppingCart.getDishId();
                DishVO dishvo = dishMapper.getByIdWithFlavor(shoppingCart.getDishId()); // 获取菜品信息
                shoppingCart.setAmount(dishvo.getPrice());
                shoppingCart.setImage(dishvo.getImage());
                shoppingCart.setName(dishvo.getName());
                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());
            }
            else{
                //添加的是套餐
                Long setmealId = shoppingCart.getSetmealId();
                itemField = "setmeal_" + setmealId;
                Setmeal setmeal = setmealMapper.getById(setmealId);
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());
            }
            shoppingCartMapper.insert(shoppingCart);
            //同步保存Redis缓存
            // Hash类型：Key=用户购物车，Field=商品唯一标识，Value=购物车商品对象
            redisTemplate.opsForHash().put(cartKey, itemField, shoppingCart);
            // 设置整个购物车Key的过期时间（30分钟）
            redisTemplate.expire(cartKey, 30, TimeUnit.MINUTES);
        }

    }
    public List<ShoppingCart> list() {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        //创建一个Redis的Key：user_用户ID_shopping_cart
        String cartKey = "user_" + BaseContext.getCurrentId()+"_shopping_cart";
        // 2. 优先查询Redis缓存
        // 获取Redis Hash中所有的购物车商品（Field对应的Value）
        Map<Object, Object> cartMap = redisTemplate.opsForHash().entries(cartKey);
        // 3. 如果Redis中有数据，直接转换为List返回
        List<ShoppingCart> cartList = new ArrayList<>();
        if (!cartMap.isEmpty()) {
            for (Object value : cartMap.values()) {
                // 确保类型正确，转换为ShoppingCart对象
                if (value instanceof ShoppingCart) {
                    cartList.add((ShoppingCart) value);
                }
            }
            return cartList;
        }
        // 4. Redis中无数据，查询数据库
        cartList = shoppingCartMapper.list(shoppingCart);
        // 5. 将数据库查询结果同步到Redis，方便后续查询命中缓存
        if (!cartList.isEmpty()) {
            for (ShoppingCart cart : cartList) {
                // 生成每个商品的Field（dish_XXX/setmeal_XXX）
                String itemField = cart.getDishId() != null
                        ? "dish_" + cart.getDishId()
                        : "setmeal_" + cart.getSetmealId();
                // 写入Redis Hash
                redisTemplate.opsForHash().put(cartKey, itemField, cart);
            }
            // 设置缓存过期时间（30分钟）
            redisTemplate.expire(cartKey, 30, TimeUnit.MINUTES);
        }

        // 6. 返回数据库查询结果
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
        //创建一个ShoppingCart对象
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        shoppingCart.setDishId(shoppingCartDTO.getDishId());
        shoppingCart.setSetmealId(shoppingCartDTO.getSetmealId());
        //获取购物车数据
        List<ShoppingCart> cartList = shoppingCartMapper.list(shoppingCart);
        if (cartList != null && cartList.size() > 0) {
            ShoppingCart cart = cartList.get(0);
            // 减少数量
            cart.setNumber(cart.getNumber() - 1);
            //获取Redis的Key和Field
            String cartKey = "user_" + BaseContext.getCurrentId() + "_shopping_cart";
            String itemField = cart.getDishId() != null ? "dish_" + cart.getDishId() : "setmeal_" + cart.getSetmealId();
            // 判断数量
            if (cart.getNumber() > 0) {
                shoppingCartMapper.updateById(cart);
                redisTemplate.opsForHash().put(cartKey, itemField, cart);
            } else if(cart.getNumber() == 0){
                shoppingCartMapper.deleteById(shoppingCart);
                redisTemplate.opsForHash().delete(cartKey, itemField);
            }
            redisTemplate.expire(cartKey, 30, TimeUnit.MINUTES);
        }
    }
}

