package com.dlwlram.sandbox.controller;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/")
public class MainController {
    @GetMapping("/test")
    public String test() {
        System.out.println("hello");
        return "11";
    }
}
