package com.penistrong.CinemaChainPlatform.online.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
public class UserController {

    //OAuth2登录控制器
    @RequestMapping("/login/oauth2")
    public String OAuth2LoginPage(){
        return "loginPage";
    }
}
