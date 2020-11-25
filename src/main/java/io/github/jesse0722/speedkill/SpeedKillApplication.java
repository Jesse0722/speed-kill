package io.github.jesse0722.speedkill;

import io.github.jesse0722.speedkill.module.SpeedKill;
import io.github.jesse0722.speedkill.service.SpeedKillService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootApplication(scanBasePackages = "io.github.jesse0722.speedkill")
public class SpeedKillApplication implements InitializingBean {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private SpeedKillService speedKillService;

    public static void main(String[] args) {
        SpringApplication.run(SpeedKillApplication.class, args);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        SpeedKill speedKill = speedKillService.get(1000);
        speedKillService.update(1000L, 1000000);
        redisTemplate.opsForValue().set(speedKill.getName(), String.valueOf(speedKill.getNumber()));
    }
}
