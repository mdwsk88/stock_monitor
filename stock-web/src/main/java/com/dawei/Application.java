package com.dawei;

import com.dawei.common.utils.DotenvLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dawei.mapper")
public class Application {
    public static void main(String[] args) {
        DotenvLoader.loadToSystemProperties();
        SpringApplication.run(Application.class, args);
    }
}
