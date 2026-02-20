package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.sky.entity.Orders.DELIVERY_IN_PROGRESS;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    // 每天一点定时完成订单
    @Scheduled(cron = "0 0 1 * * ? ")
    public void completedTimeoutOrders(){
        log.info("定时完成订单开始");
        //更新订单状态
        LocalDateTime time = LocalDateTime.now().minusDays(1);
        orderMapper.updateStatus4Completion(Orders.DELIVERY_IN_PROGRESS, time);
        int count = orderMapper.completedTimeoutOrders();
        log.info("完成{}个订单", count);
    }
}
