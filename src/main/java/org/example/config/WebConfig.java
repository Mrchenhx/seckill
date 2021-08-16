package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: Richard
 * @Create: 2021/07/20 22:14:00
 * @Description: TODO
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(jwtInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/user/**");
    }

    /**
     * jwt拦截器
     * @return
     */
    @Bean
    public JwtInterceptor jwtInterceptor() {
        return new JwtInterceptor();
    }
}
