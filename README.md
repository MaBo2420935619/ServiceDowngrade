# Listener
使用一个注解实现SpringBoot接口限流（Redis、反射、切面实现）
# 一、前言
sentinel的限流功能非常强大，但是单机springboot如何实现简单的接口限流。

**这篇文章将告诉你如何用一个注解@BlockHandler，就可以将你的接口通过切面的方式实现限流**
## 限流注解使用
![在这里插入图片描述](https://img-blog.csdnimg.cn/2044c177f11247c1a10d4d7c514c97aa.png)

# 二、限流效果截图
## 正常响应
![在这里插入图片描述](https://img-blog.csdnimg.cn/b59472daf4e74f22ac070aa77c287938.png)

## 限流响应
同一时间多次请求地址 http://localhost:8099/addEvent/reduce?s=123456 就会出现降级
![在这里插入图片描述](https://img-blog.csdnimg.cn/32d6ec4654434cb4aa8548adb97f7e72.png)



# 三、如何使用限流注解
## POM文件

```java
	<parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.1</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
    </dependencies>
```

## 1.创建接口和降级接口

> 创建需要线路的接口reduce，并在接口方法上使用注解@BlockHandler(value = 2,method = "reduceFinal")，2表示一秒内接收2次请求，大于两次后限流，，method表示限流后执行的方法名称，不指定aclass，默认为当前类的方法reduceFinal。

```java
package com.mabo.controller;

import com.mabo.block.BlockHandler;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

@RestController
@RequestMapping("addEvent")
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
        return "reduce，参数"+s+"   请求次数:"+o;
    }


    public String reduceFinal(String s){
        String key="BlockHandler.com.mabo.controller.AddEventController.reduce";
        Object o = redisTemplate.opsForValue().get(key);
        return "进入降级方法，不执行原方法，参数"+s+"   请求次数:"+o;
    }

}

```
**降级方法也可以是其他类的方法**
```java
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

```

## 2.引入BlockHandler 注解及其解析器

> BlockHandler 一共有四个参数，分别为
> 默认timeOut事件端内可以请求的次数为100次
> 降级后执行的方法所在的类
> 降级后执行的方法
> 缓存的有效事件（redis记录当前请求的次数，默认1s）

```java
package com.mabo.block;
/**
 * @Description : 在当前修饰的方法前后执行其他的方法
 * @Author : mabo
*/
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BlockHandler {
    //默认timeOut事件端内可以请求的次数为100次
    int value() default 100;
    Class aClass() default Class.class;
    String method() ;
    //默认缓存事件为1s
    int timeOut() default 1;
}
```
**注解解析器**
```java
package com.mabo.block;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
@Component
@Aspect
public class BlockHandlerAspect {
    @Resource
    private RedisTemplate redisTemplate;
    @Autowired
    private ApplicationContext applicationContext;
    /**
     * @Description : 使用Around可以修改方法的参数，返回值，
     * 甚至不执行原来的方法,但是原来的方法不执行会导致before和after注解的内容不执行
     * 通过around给原方法赋给参数
     */
    @Around("@annotation(blockHandler)")
    public Object addEventListener(ProceedingJoinPoint joinPoint, BlockHandler blockHandler) throws Throwable {
        //是否需要被限流
        boolean needHandle=false;
        //返回值
        Object proceed =null;
        Signature signature = joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String declaringTypeName = signature.getDeclaringTypeName();
        Class<?> aClass = Class.forName(declaringTypeName);
        String methodName = signature.getName();
        //判断是否需要切面
        String key="BlockHandler."+declaringTypeName+"."+methodName;
        Object o = redisTemplate.opsForValue().get(key);
        int num=0;
        if (o!=null){
            num = (Integer) o;
            if (num>=blockHandler.value()){
                needHandle=true;
            }
        }
        if(needHandle){
            if (!blockHandler.aClass().equals(Class.class)){
                aClass=blockHandler.aClass();
            }
            methodName = blockHandler.method();
        }
        num++;
        //执行被切面的方法
        Method[] methods = aClass.getMethods();
        Object bean = applicationContext.getBean(aClass);
        for (Method method : methods) {
            //获取指定方法上的注解的属性
            if (method.getName().equals(methodName)){
                if (!needHandle){
                    //不降级才执行
                    proceed = joinPoint.proceed(args);
                    redisTemplate.opsForValue().set(key,num,blockHandler.timeOut(), TimeUnit.SECONDS);
                }
                else {
                    proceed = method.invoke(bean, args);
                }
                break;
            }
        }
        return proceed;
    }

}

```

# 三、限流实现原理
该方法是利用切面、注解、反射和Redis来实现SpringBoot的接口限流，当然也可以用来限制方法的请求次数
## 1.通过Aspect的切面，切入事件方法
首先使用Aspec的Around注解，切入reduce的方法中
## 2.获取缓存数据，判断是否需要限流
切入后，先获取要执行的方法名称在redis缓存中的数据，当缓存中该方法的请求次数大于注解的value值，则需要降级。
![在这里插入图片描述](https://img-blog.csdnimg.cn/d98b1e0274de45eeb81e43989c54d211.png)
## 3.执行方法
**不需要降级，则执行原方法**
**需要降级，就不执行原方法，执行降级方法**

![在这里插入图片描述](https://img-blog.csdnimg.cn/e66422f3e55c4f6990ad9ac4f8add215.png)

## 4注意（非常重要）

- 原方法和降级方法的参数数量，类型，顺序必须一致，否则可能导致反射执行方法失败

# 四、 Github项目地址
[Github项目地址]()


