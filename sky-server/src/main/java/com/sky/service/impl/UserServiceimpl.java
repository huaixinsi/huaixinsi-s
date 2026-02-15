package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceimpl implements UserService {

    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private UserMapper userMapper;
    public User wxLogin(UserLoginDTO userLoginDTO) {
        //调用微信接口，获取微信用户信息
        Map<String, String> result = new HashMap<>();
        result.put("appid", weChatProperties.getAppid());
        result.put("secret", weChatProperties.getSecret());
        result.put("js_code", userLoginDTO.getCode());
        result.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN_URL, result);
        JSON jsonObject = JSON.parseObject(json);
        String openid = ((com.alibaba.fastjson.JSONObject) jsonObject).getString("openid");
        //判断openid是否为空
        if (openid == null) {
            throw new LoginFailedException("登录失败");
        }
        //判断用户是否存在
        com.sky.entity.User user = userMapper.getByOpenid(openid);
        //如果是新用户,自动完成注册
        if (user == null)
            user = com.sky.entity.User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
        userMapper.insert(user);
        //返回用户对象
        return user;
    }
}
