package io.github.jesse0722.speedkill.service;

import io.github.jesse0722.speedkill.dao.SpeedKillMapper;
import io.github.jesse0722.speedkill.module.SpeedKill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Lijiajun
 * @date 2020/11/23 14:59
 */
@Service
public class SpeedKillService {


    @Autowired
    private SpeedKillMapper speedKillMapper;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    public SpeedKill get(long id) {
        return speedKillMapper.get(id);
    }

    @Transactional
    public void update(long id) {
        SpeedKill speedKill = speedKillMapper.getWithLock(id);

        if(speedKill.getNumber() > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            speedKillMapper.updateStock(id, speedKill.getNumber() -1);
        }
    }

    @Transactional
    public void update(long id, int number) {
        SpeedKill speedKill = speedKillMapper.getWithLock(id);

        if(speedKill.getNumber() > 0) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            speedKillMapper.updateStock(id, number);
        }
    }

    @Transactional
    public void updateStockByRedis(long id) {
        SpeedKill speedKill = speedKillMapper.get(id);
        String redisLockKey = "product_" + id;
        String redisLockValue = UUID.randomUUID().toString();

        // set nx ex
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(redisLockKey, redisLockValue , 10, TimeUnit.SECONDS);
        if (flag) {
            String s = redisTemplate.opsForValue().get(speedKill.getName());
            redisTemplate.opsForValue().set(speedKill.getName(), String.valueOf(Integer.valueOf(s) - 1));

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //使用lua脚本释放锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

            RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
            Object result = redisTemplate.execute(redisScript, Collections.singletonList(redisLockKey), redisLockValue);
            if(!Long.valueOf(1).equals(result)) {
                throw new RuntimeException("Redis解锁异常!");
            }
        }
    }

    @Transactional
    public void updateStockInRedis(long id) {
        SpeedKill speedKill = speedKillMapper.get(id);
        String redisLockKey = speedKill.getName();
        String count = "1";
        //使用lua脚本释放锁
        StringBuilder luaScript = new StringBuilder();
        luaScript.append("if (redis.call('exists', KEYS[1]) == 1) then"); //查询商品
        luaScript.append("  local stock  = tonumber(redis.call('get', KEYS[1]));"); //如果存在，获取库存
        luaScript.append("  if (stock < tonumber(KEYS[2])) then return 0 \n");
        luaScript.append("  else\n");
        luaScript.append("      redis.call('set', KEYS[1], stock - tonumber(KEYS[2])); return 1;\n");//如果库存大于等于扣减数量则返回1，如果库存不足返回0")
        luaScript.append("  end\n");
        luaScript.append("else return -1\n");
        luaScript.append("end\n");


        RedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript.toString(), Long.class);
        Object result = redisTemplate.execute(redisScript, Arrays.asList(redisLockKey, count));

        if(Long.valueOf("0").equals(result)) {
            System.out.println("库存不足");
            return;
        }
        else if(Long.valueOf("1").equals(result)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            System.out.println("更新库存成功");
        }
        else {
            System.out.println("商品不存在或已下架");
        }
    }



}
