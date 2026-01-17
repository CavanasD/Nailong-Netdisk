package com.nailong.netdisk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(excludeName = {"org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"})
@MapperScan("com.nailong.netdisk.mapper")
public class NetdiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetdiskApplication.class, args);

    }

}
