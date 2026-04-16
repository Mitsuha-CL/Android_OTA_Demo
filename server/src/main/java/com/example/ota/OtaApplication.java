package com.example.ota;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.ota.mapper")
public class OtaApplication {
    public static void main(String[] args) {
        SpringApplication.run(OtaApplication.class, args);
    }
}
