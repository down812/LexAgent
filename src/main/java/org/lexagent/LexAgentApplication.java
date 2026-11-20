package org.lexagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("org.lexagent.mapper")
@EnableAsync
public class LexAgentApplication {

    public static void main(String[] args) {
        // 禁用 JVM 系统代理，避免科学上网工具（Clash/V2Ray 等）的系统代理设置
        // 导致 DeepSeek API 请求被路由到海外节点而 Connection reset。
        // DeepSeek 是国内服务，直连即可；如需走代理请在 Clash 规则中将 api.deepseek.com 设为 DIRECT。
        System.setProperty("java.net.useSystemProxies", "false");
        System.setProperty("http.proxyHost", "");
        System.setProperty("https.proxyHost", "");
        System.setProperty("http.nonProxyHosts", "*|0.0.0.0/0");

        SpringApplication.run(LexAgentApplication.class, args);
    }

}