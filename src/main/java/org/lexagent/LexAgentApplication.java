package org.lexagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.lexagent.mapper")
public class LexAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LexAgentApplication.class, args);
    }

}