package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param order
     */
    void insert(Orders order);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);
    @Update("update orders set status = #{cancelled} where id = #{id}")
    void updateStatus(Long id, Integer cancelled);


    OrderStatisticsVO statistics();
    @Select("select count(id) from orders where status = 5 and order_time < #{begin}")
    int completedTimeoutOrders();
    @Update("update orders set status = #{deliveryInProgress} where status = 4 and order_time < #{begin}")
    void updateStatus4Completion(Integer deliveryInProgress, LocalDateTime begin);

    Integer countByMap(Map map);
}
