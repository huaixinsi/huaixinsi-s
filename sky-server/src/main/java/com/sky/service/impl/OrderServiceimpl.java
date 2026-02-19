package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class OrderServiceimpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate; // 用于发布来单提醒

    //生成订单号
    private String generateOrderNo() {
        // 1. 获取当前时间戳（yyyyMMddHHmmssSSS，精确到毫秒）
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        // 2. 生成3位随机数
        String randomStr = String.valueOf(new Random().nextInt(900) + 100);
        // 3. 获取用户ID后4位（补0到4位）
        Long userId = BaseContext.getCurrentId();
        String userIdSuffix = String.format("%04d", userId % 10000);
        // 4. 拼接订单号
        return timeStr + randomStr + userIdSuffix;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //判断购物车和地址簿是否存在
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException("地址簿不存在");
        }
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException("购物车为空");
        }
        //订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        // 用户下单即视为已支付：设置支付状态/方式/结账时间、并将订单置为待接单（与 paySuccess 保持一致）
        orders.setPayStatus(Orders.PAID);
        orders.setPayMethod(ordersSubmitDTO.getPayMethod());
        orders.setCheckoutTime(LocalDateTime.now());
        orders.setStatus(Orders.TO_BE_CONFIRMED);
        orders.setNumber(generateOrderNo());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getDetail());
        orderMapper.insert(orders);
        //订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            //订单明细数据插入
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setName(cart.getName());
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);
        //清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 发布来单提醒，消息包含订单关键信息（前端或店铺端可订阅频道 order:arrived）
        try {
            JSONObject msg = new JSONObject();
            msg.put("id", orders.getId());
            msg.put("number", orders.getNumber());
            msg.put("amount", orders.getAmount());
            msg.put("userName", orders.getUserName());
            msg.put("address", orders.getAddress());
            msg.put("orderTime", orders.getOrderTime());
            msg.put("status", orders.getStatus());
            redisTemplate.convertAndSend("order:arrived", msg.toJSONString());
        } catch (Exception ex) {
            // 发布失败不影响下单成功，记录日志（可以改为使用 logger）
            System.err.println("publish order arrived failed: " + ex.getMessage());
        }

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付参数
     * @return 预支付信息
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 检查订单是否已经支付（避免重复请求微信）
        Orders ordersDb = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDb != null && Orders.PAID.equals(ordersDb.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                BigDecimal.valueOf(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo 订单号
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        if (ordersDB == null) {
            // 订单不存在，直接返回
            return;
        }

        // 如果订单已经是已支付状态，则认为已处理，避免重复更新和重复通知
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            return;
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 发布来单提醒（幂等），与 submitOrder 保持一致
        try {
            JSONObject msg = new JSONObject();
            msg.put("id", ordersDB.getId());
            msg.put("number", ordersDB.getNumber());
            msg.put("amount", ordersDB.getAmount());
            msg.put("userName", ordersDB.getUserName());
            msg.put("address", ordersDB.getAddress());
            msg.put("orderTime", ordersDB.getOrderTime());
            msg.put("status", Orders.TO_BE_CONFIRMED);
            redisTemplate.convertAndSend("order:arrived", msg.toJSONString());
        } catch (Exception ex) {
            System.err.println("publish order arrived failed: " + ex.getMessage());
        }
    }

    //历史订单查询
    public PageResult pageQuery(Integer page, Integer pageSize, Integer status) {
        //设置分页
        PageHelper.startPage(page, pageSize);
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        //分页条件查询
        Page<Orders> pageInfo = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        if (pageInfo != null && pageInfo.getTotal() > 0) {
            for (Orders orders : pageInfo) {
                Long orderId = orders.getId();
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orderId));
                list.add(orderVO);
            }
        }
        return new PageResult(pageInfo.getTotal(), list);
    }

    public OrderVO getByIdOrder(Long id) {
        //获取订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //获取订单信息
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setId(id);
        return orderVO;
    }

    public void cancel(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null)
            throw new OrderBusinessException("订单不存在");
        if (Objects.equals(orders.getStatus(), Orders.CONFIRMED))
            throw new OrderBusinessException("订单已确认，不能取消");
        if (Objects.equals(orders.getStatus(), Orders.COMPLETED))
            throw new OrderBusinessException("订单已完成，不能取消");
        if (Objects.equals(orders.getStatus(), Orders.CANCELLED))
            throw new OrderBusinessException("订单已取消，不能取消");
        if (orders.getPayStatus() == Orders.PAID)
            throw new OrderBusinessException("已支付订单不能取消");
        if (orders.getStatus() == orders.PENDING_PAYMENT || orders.getStatus() == orders.TO_BE_CONFIRMED)
            orderMapper.updateStatus(id, Orders.CANCELLED);

    }

    public void again(Long id) {
        Orders orders = orderMapper.getById(id);
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        for (ShoppingCart shoppingCart : shoppingCartList)
            shoppingCartMapper.insert(shoppingCart);

    }

    public PageResult<OrderVO> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> pageInfo = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        if (pageInfo != null && pageInfo.getTotal() > 0) {
            for (Orders orders : pageInfo) {
                Long orderId = orders.getId();
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orderId));
                list.add(orderVO);
            }
        }
        return new PageResult(pageInfo.getTotal(), list);
    }
    public OrderStatisticsVO statistics() {
        return orderMapper.statistics();
    }
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Long id = ordersConfirmDTO.getId();
        Orders orders = orderMapper.getById(id);
        if (orders == null)
            throw new OrderBusinessException("订单不存在");
        if (Objects.equals(orders.getStatus(), Orders.CANCELLED))
            throw new OrderBusinessException("订单已取消，不能接单");
        if (Objects.equals(orders.getStatus(), Orders.COMPLETED))
            throw new OrderBusinessException("订单已完成，不能接单");
        if (Objects.equals(orders.getStatus(), Orders.CONFIRMED))
            throw new OrderBusinessException("订单已确认，不能接单");
        if (orders.getPayStatus() == Orders.PAID)
            orderMapper.updateStatus(id, Orders.CONFIRMED);
    }
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Long id = ordersRejectionDTO.getId();
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null)
            throw new OrderBusinessException("订单不存在");
        if (Objects.equals(ordersDB.getStatus(), Orders.CANCELLED))
            throw new OrderBusinessException("订单已取消，不能拒单");
        if (Objects.equals(ordersDB.getStatus(), Orders.COMPLETED))
            throw new OrderBusinessException("订单已完成，不能拒单");
        if (Objects.equals(ordersDB.getStatus(), Orders.CONFIRMED))
            throw new OrderBusinessException("订单已确认，不能拒单");
        if (ordersDB.getPayStatus() == Orders.PAID) {
            Orders orders = new Orders();
            orders.setId(ordersDB.getId());
            orders.setStatus(Orders.CANCELLED);
            orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
            orders.setCancelTime(LocalDateTime.now());
            orderMapper.update(orders);
        }
    }
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Long id = ordersCancelDTO.getId();
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null)
            throw new OrderBusinessException("订单不存在");
        if (Objects.equals(ordersDB.getStatus(), Orders.CANCELLED))
            throw new OrderBusinessException("订单已取消，不能取消");
        if (Objects.equals(ordersDB.getStatus(), Orders.COMPLETED))
            throw new OrderBusinessException("订单已完成，不能取消");
        if (ordersDB.getPayStatus() == Orders.CONFIRMED) {
            Orders orders = new Orders();
            orders.setId(ordersDB.getId());
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason(ordersCancelDTO.getCancelReason());
            orders.setCancelTime(LocalDateTime.now());
            orderMapper.update(orders);
        }
    }

    @Override
        public void delivery(Long id) {
            // 根据id查询订单
            Orders ordersDB = orderMapper.getById(id);

            // 校验订单是否存在，并且状态为3
            if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
                throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
            }

            Orders orders = new Orders();
            orders.setId(ordersDB.getId());
            // 更新订单状态,状态转为派送中
            orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

            orderMapper.update(orders);
        }
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }
}
