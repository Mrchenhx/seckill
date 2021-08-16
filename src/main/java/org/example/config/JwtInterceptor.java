package org.example.config;

import org.example.annotation.JwtToken;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.util.JwtUtil;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * @Author: Richard
 * @Create: 2021/07/20 22:04:00
 * @Description: TODO
 */
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object object) throws BusinessException {
        // 从 http 请求头中取出 token
        // String token = httpServletRequest.getHeader("j_token");
        String token = httpServletRequest.getHeader("Authorization");
        System.out.println("JWT =====>  " + token);
        System.out.println("当前访问的url为：" + httpServletRequest.getRequestURL());
        // 如果不是映射到方法直接通过
        if(!(object instanceof HandlerMethod)){
            return true;
        }
        HandlerMethod handlerMethod=(HandlerMethod)object;
        Method method=handlerMethod.getMethod();
        //检查有没有需要用户权限的注解
/*        if (method.isAnnotationPresent(JwtToken.class)) {
            JwtToken jwtToken = method.getAnnotation(JwtToken.class);
            if (jwtToken.required()) {
                // 执行认证
                if (token == null) {
                    throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,
                            "token 无效，请重新获取");
                }
                // 获取 token 中的 userId
                String userId = JwtUtil.getUserId(token);
                System.out.println("用户id:" + userId);

                // 验证 token
                JwtUtil.checkSign(token);
            }
        }*/
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {

    }
    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {

    }
}