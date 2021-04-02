package com.penistrong.CinemaChainPlatform.online.controller;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableAutoConfiguration
public class HelloController {
    @GetMapping("show")
    public String test(){
        return "Hello, SpringBoot!";
    }
}
