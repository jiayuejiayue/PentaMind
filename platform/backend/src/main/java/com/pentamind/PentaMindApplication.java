package com.pentamind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * PentaMind 渗透测试管理平台 - 启动类
 */
@EnableAsync
@SpringBootApplication
public class PentaMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(PentaMindApplication.class, args);
        System.out.println("""
                    ╔════════════════════════════════════════════════╗
                    ║   PentaMind Platform v1.0                      ║
                    ║   Backend running at http://localhost:8080      ║
                    ╚════════════════════════════════════════════════╝
                """);
    }
}
