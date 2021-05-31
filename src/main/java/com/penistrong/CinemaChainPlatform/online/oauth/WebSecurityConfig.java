package com.penistrong.CinemaChainPlatform.online.oauth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    @Override
    protected UserDetailsService userDetailsService() {
        return super.userDetailsService();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        /*
        auth.inMemoryAuthentication()
                .withUser("admin")
                .password(new BCryptPasswordEncoder().encode("123"))
                .roles("admin")
                .and()
                .withUser("penistrong")
                .password(new BCryptPasswordEncoder().encode("chenliwei"))
                .roles("user");
         */
        auth.jdbcAuthentication()
                .dataSource(dataSource)
                .usersByUsernameQuery("SELECT username,password,enabled FROM users WHERE username=?")
                .authoritiesByUsernameQuery("SELECT username,authority FROM authorities WHERE username=?")
                .passwordEncoder(passwordEncoder);

    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .headers().frameOptions().disable()
                .and()
            .antMatcher("/oauth/**").authorizeRequests()
                .antMatchers("/oauth/**").permitAll();
    }

    //放行静态资源
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
                .antMatchers("/js/**",
                        "/css/**",
                        "/*/*.jpg",
                        "/*/*.png",
                        "/*.ico");
    }
}
