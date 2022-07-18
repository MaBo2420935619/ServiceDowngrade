package com.mabo.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class BlockController {
    @Resource
    private RedisTemplate redisTemplate;

    public String reduceFinal(String s){
        String key="BlockHandler.com.mabo.controller.AddEventController.reduce";
        Object o = redisTemplate.opsForValue().get(key);
        return "进入降级方法，不执行原方法，参数"+s+"  请求次数:"+o;
    }
}
