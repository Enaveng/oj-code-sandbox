package com.dlwlram.sandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.dlwlram.sandbox.model.ExecuteCodeRequest;
import com.dlwlram.sandbox.model.ExecuteCodeResponse;
import com.dlwlram.sandbox.model.ExecuteMessage;
import com.dlwlram.sandbox.model.JudgeInfo;
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
import java.util.*;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static boolean FIRST_INIT = false;

    private static final long TIME_OUT = 5000L;

    @Override
    public List<ExecuteMessage> execCodeFile(ExecuteCodeRequest executeCodeRequest, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<String> inputList = executeCodeRequest.getInputList();
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
        return executeMessageList;
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
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        //ResourceUtil.readStr会读取classPath路径下的文件
        executeCodeRequest.setCode(ResourceUtil.readStr("testCode/Main.java", StandardCharsets.UTF_8));
        executeCodeRequest.setLanguage("java");
        javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
