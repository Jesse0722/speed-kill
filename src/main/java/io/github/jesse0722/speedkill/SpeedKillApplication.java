package io.github.jesse0722.speedkill;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootApplication
@MapperScan(basePackages = "io.github.jesse0722.speedkill.dao")
public class SpeedKillApplication implements InitializingBean {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
//    @Autowired
//    private SpeedKillMapper speedKillMapper;

    public static void main(String[] args) {
        SpringApplication.run(SpeedKillApplication.class, args);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }
//
//    @Override
//    public void afterPropertiesSet() throws Exception {
//        SpeedKill speedKill = speedKillMapper.get(1000);
//        speedKillMapper.updateStock(1000L, 1000000);
//        redisTemplate.opsForValue().set(speedKill.getName(), String.valueOf(speedKill.getNumber()));
//    }
}
