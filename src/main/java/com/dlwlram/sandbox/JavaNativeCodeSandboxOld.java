package com.dlwlram.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.dlwlram.sandbox.model.ExecuteCodeRequest;
import com.dlwlram.sandbox.model.ExecuteCodeResponse;
import com.dlwlram.sandbox.model.ExecuteMessage;
import com.dlwlram.sandbox.model.JudgeInfo;
import com.dlwlram.sandbox.utils.RunProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


@Slf4j
@Component
public class JavaNativeCodeSandboxOld implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();  //参数
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        //先得到当前的工作目录
        String userDir = System.getProperty("user.dir");
        //不直接使用"/"拼接路径的原因是在Linux系统下的符号不同
        /**
         *  On UNIX systems the value of this field is '/'; on Microsoft Windows systems it is '\\'.
         */
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;//全局代码目录
        //判断该目录是否存在 不存在则创建
        if (FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //代码文件的父目录  目的是为了将每一个代码文件存储到不同的文件夹下 起到隔离作用
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //将代码内容写入到文件当中
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        //Java操作命令行编译代码 参数为实际的命令
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        log.info(userCodeFile.getAbsolutePath());
        log.info(userCodePath);
        log.info(compileCmd);
        log.info(userCodeParentPath);
        try {
            //得到的是一个进程
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = RunProcessUtils.RunProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //编译完成之后执行代码
        for (String input : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
            try {
                //得到的是一个进程而不会创建新的线程
                Process compileProcess = Runtime.getRuntime().exec(runCmd);
                //创建守护线程判断代码执行是否超时
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("程序运行超时 中断");
                        compileProcess.destroy();  //当程序执行时间大于所设置的最大时间时就不执行
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = RunProcessUtils.RunProcessAndGetMessage(compileProcess, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }
        //拼接结果返回
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        long maxTime = 0;
        List<String> outputList = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) { //表示编译运行的过程当中存在错误
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;  //有一个错误就直接将此次判题确认为错误
            }
            Long time = executeMessage.getTime();
            //取最大时间
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            String successMessage = executeMessage.getSuccessMessage();
            outputList.add(successMessage);
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        //judgeInfo.setMemory();  需要使用第三方库得到程序的运行内存很麻烦
        executeCodeResponse.setJudgeInfo(judgeInfo);
        //防止在每次代码编译运行之后文件保留 导致服务器空间不足 需要删除代码目录
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    /**
     * 封装程序执行错误时返回错误的ExecuteCodeResponse 比如程序编译失败 代码沙箱出现问题等
     *
     * @param e 异常
     * @return ExecuteCodeResponse
     */
    private ExecuteCodeResponse getErrorResponse(Exception e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());  //错误时输出直接返回空
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    public static void main(String[] args) {
        //测试
        JavaNativeCodeSandboxOld javaNativeCodeSandbox = new JavaNativeCodeSandboxOld();
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
