package com.sky.aspect;

import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.sky.dto.DishDTO;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
public class DishCacheAspect {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DishService dishService;

    //-------根据id存入缓存-----
    @Around("@annotation(com.sky.annotation.RedisCacheByCategoryId)")
    public Object cacheDishById(ProceedingJoinPoint joinPoint) throws Throwable {
        // 关键修复2：参数校验，避免空指针/类型错误
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof Long)) {
            log.warn("菜品缓存切面：参数异常，categoryId必须为非空Long类型");
            return joinPoint.proceed(); // 参数异常直接执行原方法
        }

        Long categoryId = (Long) args[0];
        String key = "dish_" + categoryId;
        log.info("菜品缓存切面：处理分类ID{}的缓存，Key={}", categoryId, key);

        // 关键修复3：先获取String类型的JSON，而非直接强转List
        String cacheJson = (String) redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cacheJson)) {
            log.info("菜品缓存切面：缓存命中，Key={}", key);
            // 关键修复4：用ObjectMapper将JSON字符串反序列化为List<DishVO>
            // 1. 构建List<DishVO>的类型对象
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, DishVO.class);
// 2. 反序列化
            List<DishVO> list = objectMapper.readValue(cacheJson, listType);
            return Result.success(list);
        }

        // 缓存未命中：查询数据库
        log.info("菜品缓存切面：缓存未命中，执行数据库查询，Key={}", key);
        Result<List<DishVO>> result = (Result<List<DishVO>>) joinPoint.proceed();

        // 存入缓存（List序列化为JSON字符串）
        if (result != null && result.getData() != null) {
            String jsonStr = objectMapper.writeValueAsString(result.getData());
            redisTemplate.opsForValue().set(key, jsonStr, 30, TimeUnit.MINUTES);
            log.info("菜品缓存切面：数据库结果已存入缓存，Key={}", key);
        }

        return result;
    }

    //-------删除缓存-----
    @Around("@annotation(com.sky.annotation.RedisCacheEvitctById)")
    public Object deleteDishCache(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 先执行目标方法（完成数据变更：新增/修改/删除菜品）
        Object result = joinPoint.proceed();

        // 2. 解析参数，收集需要删除的分类ID（去重）
        Set<Long> categoryIds = new HashSet<>();
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg == null) continue;

                // 场景1：参数是DishDTO（新增/修改菜品 → 直接取分类ID）
                if (arg instanceof DishDTO) {
                    DishDTO dishDTO = (DishDTO) arg;
                    if (dishDTO.getCategoryId() != null) {
                        categoryIds.add(dishDTO.getCategoryId());
                        log.info("缓存删除切面：从DishDTO提取分类ID={}", dishDTO.getCategoryId());
                    }
                }
                // 场景2：参数是单个Long（菜品ID → 查菜品获取分类ID）
                else if (arg instanceof Long) {
                    Long dishId = (Long) arg;
                    Long cid = getCategoryIdByDishId(dishId);
                    if (cid != null) {
                        categoryIds.add(cid);
                        log.info("缓存删除切面：从菜品ID={}提取分类ID={}", dishId, cid);
                    }
                }
                // 场景3：参数是List<Long>（批量菜品ID → 遍历查分类ID）
                else if (arg instanceof List<?>) {
                    List<?> dishIds = (List<?>) arg;
                    for (Object obj : dishIds) {
                        if (obj instanceof Long) {
                            Long dishId = (Long) obj;
                            Long cid = getCategoryIdByDishId(dishId);
                            if (cid != null) {
                                categoryIds.add(cid);
                            }
                        }
                    }
                }
            }
        }

        // 3. 批量删除缓存（优化性能，避免循环删除）
        if (!CollectionUtils.isEmpty(categoryIds)) {
            List<String> cacheKeys = categoryIds.stream()
                    .map(cid -> "dish_" + cid)
                    .collect(Collectors.toList());
            redisTemplate.delete(cacheKeys); // 批量删除，效率更高
            log.info("缓存删除切面：成功删除缓存Key列表={}", cacheKeys);
        } else {
            log.warn("缓存删除切面：未提取到任何分类ID，跳过缓存删除");
        }

        // 4. 返回原方法结果
        return result;
    }

    /**
     * 辅助方法：通过菜品ID查询分类ID（容错处理，不影响核心业务）
     */
    private Long getCategoryIdByDishId(Long dishId) {
        try {
            DishVO dish = dishService.getByIdWithFlavor(dishId); // 确保DishService有该方法
            if (dish != null) {
                return dish.getCategoryId();
            }
            log.warn("缓存删除切面：菜品ID={}不存在，无法获取分类ID", dishId);
            return null;
        } catch (Exception e) {
            log.error("缓存删除切面：查询菜品ID={}的分类ID失败", dishId, e);
            return null; // 异常时返回null，不阻断核心流程
        }
    }
}

