package com.penistrong.CinemaChainPlatform.online.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

//Completely Override the Auto-configuration of SpringBoot2.0
//@Configuration
public class OAuth2LoginConfig {

    //@EnableWebSecurity
    public static class OAuth2LoginSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        public void configure(WebSecurity web) throws Exception{
            web.ignoring().antMatchers("/js/**", "/css/**","**/*.jpg", "**/*.ico");
        }

        //注册OAuth2认证方式
        @Override
        protected void configure(HttpSecurity http) throws Exception{
            http
                    .authorizeRequests()
                        .antMatchers( "/druid/**", "/user/login/oauth2/**", "/homepage", "/movie/**", "/collection/**", "/js/**", "/css/**","/*/*.jpg", "/*/*.png", "/*.ico").permitAll() //主页、电影详情页、电影分类页不登录均可访问
                        .antMatchers("/admin/**").hasRole("ADMIN")                          //后台管理页面必须当前用户为管理员角色
                        .anyRequest().authenticated()                                                   //其他都要验证登录信息
                    .and()
                    .oauth2Login()//使用OAuth2实现 "Login with Github" or "Login with Google" etc
                        .loginPage("/user/login/oauth2")//注意要在authorizeRequests()里放行登录页面，且要使用控制器处理该页面
                    .and()
                    .logout()//使用WebSecurityConfigurerAdapter已自动配置登出功能，默认URL为"/logout",登出后将清理session、RememberMe authentication并重定向到"/login?logout"，所以要自定义
                        .logoutUrl("/user/logout")              //如果没有关闭CSRF跨站请求保护，登出时该URL必须接收POST类型的请求
                        .logoutSuccessUrl("/homepage")
                        //.logoutSuccessHandler(logoutSuccessHandler) //自定义登出成功时的处理函数
                        .invalidateHttpSession(true)            //默认为真，即登出时清理Session
                        //.addLogoutHandler(logoutHandler)
                        //.deleteCookies(cookieNamesToClear)
                    .and()
                    .csrf().disable();//不使用CSRF Token，关闭跨站请求防护
        }


    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(){
        return new InMemoryClientRegistrationRepository(this.googleClientRegistration());
    }

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private ClientRegistration googleClientRegistration(){
        return ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUriTemplate("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email", "address", "phone")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .build();

    }

    /*
    private ClientRegistration qqClientRegistration(){
        return
    }*/
}
