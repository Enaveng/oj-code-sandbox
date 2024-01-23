package com.dlwlram.sandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.dlwlram.sandbox.model.ExecuteCodeRequest;
import com.dlwlram.sandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;


//使用模板方法设计模式重构
@Slf4j
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    public static void main(String[] args) {
        //测试
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        //ResourceUtil.readStr会读取classPath路径下的文件
        executeCodeRequest.setCode(ResourceUtil.readStr("testCode/Main.java", StandardCharsets.UTF_8));
        executeCodeRequest.setLanguage("java");
        javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }

    //    public static void main(String[] args) throws InterruptedException {int a = Integer.parseInt(args[0]);
//        int b = Integer.parseInt(args[1]);
//        Thread.sleep(5000L);
//        System.out.println("结果: " + (a + b));
//    }


}
