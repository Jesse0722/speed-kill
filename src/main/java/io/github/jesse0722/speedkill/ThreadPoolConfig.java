package io.github.jesse0722.speedkill;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Lijiajun
 * @date 2020/11/25 23:05
 */
@Configuration
public class ThreadPoolConfig {

    @Bean
    public ExecutorService pool() {
        return Executors.newFixedThreadPool(16);
    }
}
