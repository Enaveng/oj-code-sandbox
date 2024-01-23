package com.dlwlram.sandbox.controller;

import com.dlwlram.sandbox.JavaNativeCodeSandbox;
import com.dlwlram.sandbox.model.ExecuteCodeRequest;
import com.dlwlram.sandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@RestController
@RequestMapping("/")
public class MainController {
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;


    @GetMapping("/test")
    public String test() {
        System.out.println("hello");
        return "11";
    }

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        //简单的安全认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);  //通过key得到value
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
