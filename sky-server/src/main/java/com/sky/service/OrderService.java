package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);
    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    PageResult pageQuery(Integer page, Integer pageSize, Integer status);
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderVO getByIdOrder(Long id);

    void cancel(Long id);

    void again(Long id);

    OrderStatisticsVO statistics();

    void confirm(OrdersConfirmDTO id);

    void rejection(OrdersRejectionDTO ordersRejectionDTO);
    void cancel(OrdersCancelDTO ordersCancelDTO);

    void delivery(Long id);

    void complete(Long id);
}
