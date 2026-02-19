package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 用于在服务端打印/记录 Redis 发布的来单提醒（仅用于本地验证/观测）。
 * 在生产环境中，可以替换为调用通知推送服务或 WebSocket 转发。
 */
@Component
@Slf4j
public class OrderNotificationListener implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        log.info("[OrderNotification] channel={} message={}", channel, body);
    }
}

