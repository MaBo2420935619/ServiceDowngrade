package com.mabo.controller;

import com.mabo.block.BlockHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

@RestController
@RequestMapping("addEvent")
@Slf4j
public class AddEventController {
    @Resource
    private RedisTemplate redisTemplate;
    /**
     * @Author mabo
     * @Description   该方法主动调用
     * 浏览器输入下方地址即可测试
     *          http://localhost:8099/addEvent/reduce?s=123456
     */
//    @BlockHandler(value = 3,method = "reduceFinal",aClass = BlockController.class)
    // 同一个类中的方法，也可以不使用aClass来指定类路径

    //限制一秒内只能请求2次
    @BlockHandler(value = 2,method = "reduceFinal")
    @RequestMapping("reduce")
    public String reduce(@RequestParam("s") String s){

        String key="BlockHandler.com.mabo.controller.AddEventController.reduce";
        Object o = redisTemplate.opsForValue().get(key);
        log.info("reduce，参数"+s+"   请求次数:"+o);
        return "reduce，参数"+s+"   请求次数:"+o;
    }


    public String reduceFinal(String s){
        String key="BlockHandler.com.mabo.controller.AddEventController.reduce";
        Object o = redisTemplate.opsForValue().get(key);
        return "进入降级方法，不执行原方法，参数"+s+"   请求次数:"+o;
    }

}
