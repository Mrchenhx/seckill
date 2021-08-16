package org.example;

import org.apache.tomcat.util.http.fileupload.RequestContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * @Author: Richard
 * @Create: 2021/08/08 21:00:00
 * @Description: TODO
 */

// @Aspect // 将一个类定义为一个切面类
// @Component  // 把切面类加入到IOC容器中
public class AspectDemo {

    @Pointcut("execution(public * org.example.controller.*.*(..))")
    public void webLog(){}

    // 请求method前打印内容
    @Before("webLog()") // 在切点前执行方法,内容为指定的切点
    public void doBefore(JoinPoint joinPoint) {
        System.out.println("测试 aop before 输出...");
        ServletRequestAttributes request = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request1 = request.getRequest();
        System.out.println("==========Before===========");
        System.out.println(request1.getRequestURL().toString());
        System.out.println(request1.getMethod());
        System.out.println("=====================");
    }

    @AfterReturning(returning = "ret", pointcut = "webLog()")
    public void doAfterReturning(Object ret){
        // 处理完请求，返回内容
        System.out.println("*****************");
    }
}
