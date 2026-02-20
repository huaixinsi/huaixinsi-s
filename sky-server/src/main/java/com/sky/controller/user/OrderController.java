package com.sky.controller.user;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@ApiOperation("订单接口")
@RequestMapping("/user/order")
public class OrderController {
    @Autowired
    private OrderService orderService;
    // 用户下单
    @ApiOperation("用户下单")
    @RequestMapping("/submit")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result historyOrders(Integer page, Integer pageSize,Integer status) {
        log.info("历史订单查询：{}", page, pageSize, status);
        PageResult pageResult = orderService.pageQuery(page, pageSize, status);
        return Result.success(pageResult);
    }
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("订单详情")
    public Result<OrderVO> orderDetail(@PathVariable Long id) {
        log.info("订单详情：{}", id);
        OrderVO orderVO = orderService.getByIdOrder(id);
        return Result.success(orderVO);
    }
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id) {
        log.info("取消订单：{}", id);
        orderService.cancel(id);
        return Result.success();
    }
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result agagin(@PathVariable Long id)
    {
        log.info("再来一单：{}", id);
        orderService.again(id);
        return Result.success();
    }
    @GetMapping("/reminder/{id}")
    @ApiOperation("客户催单")
    public Result reminder(@PathVariable Long id) {
        log.info("客户催单：{}", id);
        orderService.reminder(id);
        return Result.success();
    }
}
