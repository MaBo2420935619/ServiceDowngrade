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

/**
 * @Description : 定义自定义注解的切面
 * @Author : mabo
 */
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
