package com.penistrong.CinemaChainPlatform.online.oauth;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;

@Configuration
@EnableResourceServer
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources.resourceId("rId") //配置资源id，与授权服务器中的资源id一致
                .stateless(true);   //设置这些资源仅基于令牌认证
    }

    //配置URL访问权限,即资源服务器
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers( "/druid/**",
                        "/user/login/**",
                        "/user/register/**",
                        "/homepage",
                        "/movie/**",
                        "/collection/**",
                        "/watchlist/**").permitAll() //主页、电影详情页、电影分类页不登录均可访问
                .antMatchers("/admin/**").hasRole("admin")
                .antMatchers("/user/**").hasRole("user")
                .anyRequest().authenticated();
    }
}
