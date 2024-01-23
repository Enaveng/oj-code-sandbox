package com.dlwlram.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.dlwlram.sandbox.model.ExecuteCodeRequest;
import com.dlwlram.sandbox.model.ExecuteCodeResponse;
import com.dlwlram.sandbox.model.ExecuteMessage;
import com.dlwlram.sandbox.model.JudgeInfo;
import com.dlwlram.sandbox.utils.RunProcessUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class JavaDockerCodeSandboxOld implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    private static boolean FIRST_INIT = false;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();  //参数
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        //1.将用户的代码保存为文件
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

        //2.编译代码 得到class文件
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

        //使用docker代码沙箱进行代码的执行
        //3.拉取jdk环境镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {   //仅仅是第一次初始化时需要去拉取镜像
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println(item.getStatus());
                    super.onNext(item);
                }
            };
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                log.info("拉取镜像发生异常");
                throw new RuntimeException(e);
            }
            System.out.println("镜像拉取完成");
            FIRST_INIT = false;
        }
        //4.创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //设置容器配置
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);  //设置容器内存为100MB
        hostConfig.withMemorySwap(0L);              //设置容器的内存交换为0 即禁用内存交换
        hostConfig.withCpuCount(1L);                //设置容器的最大cpu使用数为1
//        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));  //将主机上的 userCodeParentPath 目录与容器内的 /app 目录进行绑定。
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)  //分配一个伪终端 表示采取交互式运行
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();  //得到创建之后的容器id
        //5.启动容器
        dockerClient.startContainerCmd(containerId).exec();
        //6.执行命令并得到返回结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {   //代码执行入参
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            //执行的命令为 java -cp /app 1 3     以下仅仅是创建执行命令 并没有执行
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);
            //执行命令
            String id = execCreateCmdResponse.getId();
            //得到输出结果
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] errorMessage = new String[1];
            final String[] successMessage = new String[1];
            long time;
            final long[] maxMemory = new long[1];
            final boolean[] timeOut = {true};
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    System.out.println("程序执行完成");
                    timeOut[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    //判断输出的结果是错误结果还是正确结果
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {  //表示输出的是错误结果   得到的是 byte[] 需要使用new String()
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出的错误结果为:" + errorMessage[0]);
                    } else {
                        successMessage[0] = new String(frame.getPayload());
                        System.out.println("输出的正确结果为:" + successMessage[0]);
                    }
                    super.onNext(frame);
                }
            };
            //获取程序占用的内存
            dockerClient.statsCmd(containerId).exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("程序内存占用为:" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            try {
                //获取程序执行的时间
                stopWatch.start();
                dockerClient.execStartCmd(id)
                        .exec(execStartResultCallback)
                        //这种超时限制的方式无论是否超时程序都会向下继续执行
                        .awaitCompletion(TIME_OUT, TimeUnit.MINUTES);    //添加参数限制程序的运行时间 防止用户恶意运行死循环代码
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setSuccessMessage(successMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }

        //拼接结果返回
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        long maxTime = 0;
        long finalMemory = 0;
        List<String> outputList = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {  //表示编译运行的过程当中存在错误
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;  //有一个错误就直接将此次判题确认为错误
            }
            Long time = executeMessage.getTime();
            //取最大时间
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            //取最大占用内存
            Long memory = executeMessage.getMemory();
            finalMemory = Math.max(memory, finalMemory);
            String successMessage = executeMessage.getSuccessMessage();
            outputList.add(successMessage);
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(finalMemory);
        System.out.println(judgeInfo);
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
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        //ResourceUtil.readStr会读取classPath路径下的文件
        executeCodeRequest.setCode(ResourceUtil.readStr("testCode/Main.java", StandardCharsets.UTF_8));
        executeCodeRequest.setLanguage("java");
        javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
