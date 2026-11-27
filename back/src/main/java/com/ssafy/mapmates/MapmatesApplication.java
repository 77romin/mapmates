package com.ssafy.mapmates;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ssafy.mapmates.dao")
public class MapmatesApplication {
    public static void main(String[] args) {
        SpringApplication.run(MapmatesApplication.class, args);
    }
}
