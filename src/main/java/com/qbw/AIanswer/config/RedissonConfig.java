package com.qbw.AIanswer.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig {


    private String host;

    private Integer port;

    private Integer database;

    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config redissonConfig = new Config();
        String address = String.format("redis://%s:%s", host, port);
        System.out.println(address);
        redissonConfig.useSingleServer().setAddress(address).setPassword(password).setDatabase(1);
        return Redisson.create(redissonConfig);
    }
}
