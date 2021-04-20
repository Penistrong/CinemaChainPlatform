package com.penistrong.CinemaChainPlatform.online.controller;

import com.penistrong.CinemaChainPlatform.online.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    /* 使用POST请求访问context_path/oauth/token获取access_token再访问受限资源（页面等），不用自写登录控制器了，用Token操作
    //Session登录控制器,GET请求访问登陆页面
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String LoginPage() {
        return "loginPage";
    }

    //处理form login请求,POST请求处理登录信息
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String Login() {

    }
    */

    //OAuth2登录控制器
    @RequestMapping("/login/oauth2")
    public String OAuth2LoginPage(){
        return "OAuthLoginPage";
    }
}
