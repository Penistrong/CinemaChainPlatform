package com.penistrong.CinemaChainPlatform.online.oauth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.token.TokenService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;
import javax.sql.DataSource;

//授权服务器
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    //使用支持OAuth2的password模式（四大授权模式之一）
    @Resource
    private AuthenticationManager authenticationManager;

    //用户对象，为刷新Token提供支持
    @Resource
    private UserDetailsService userDetailsService;

    /*使用内存存储令牌信息，可以改为Redis
    @Autowired(required = false)
    TokenStore inMemoryTokenStore;
    */
    //使用Redis存储
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /*不将Token存储到数据库中，使用内存或Redis
    @Resource
    private DataSource dataSource;

    //初始化JDBC Token Store
    @Bean
    public TokenStore jdbcTokenStore() {
        return new CustomJdbcTokenStore(dataSource);
    }

    @Bean
    public ClientDetailsService clientDetailsService(){
        return new JdbcClientDetailsService(dataSource);
    }
    */

    //指定密码的加密方式，使用BCrypt强哈希函数加密方案，秘钥迭代次数默认为10
    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    //使用POST请求访问接口/oauth/token获取token,请求体中要携带各种信息
    //配置客户端
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
                .withClient("password")
                .authorizedGrantTypes("password", "refresh_token") //授权模式使用password和refresh_token两种
                .accessTokenValiditySeconds(1800) //Access Token过期时间
                .resourceIds("rId") //配置资源ID
                .scopes("all")
                .secret(passwordEncoder().encode("123"));
    }

    /*使用内存保存令牌的配置
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints.tokenStore(inMemoryTokenStore)
                .authenticationManager(authenticationManager)
                .userDetailsService(userDetailsService);
    }*/

    //使用Redis保存令牌的配置
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints.tokenStore(new RedisTokenStore(redisConnectionFactory))
                .authenticationManager(authenticationManager)
                .userDetailsService(userDetailsService);
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        //使用表单进行登录验证，包括client_id和client_secret
        security.allowFormAuthenticationForClients();
    }
}
