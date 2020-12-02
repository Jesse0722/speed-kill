package io.github.jesse0722.speedkill.service;

import io.github.jesse0722.speedkill.dao.OrderMapper;
import io.github.jesse0722.speedkill.dao.SpeedKillMapper;
import io.github.jesse0722.speedkill.module.Order;
import io.github.jesse0722.speedkill.module.SpeedKill;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
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
@Slf4j
public class SpeedKillService {

    private final long userId = 100000L;

    @Autowired
    private SpeedKillMapper speedKillMapper;
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    public SpeedKill get(long id) {
        log.error("{}", id);
        return speedKillMapper.get(id);
    }


    @Transactional
    public void updateStockForUpdate(String orderNo, long id, int count) {
        SpeedKill speedKill = speedKillMapper.getWithLock(id);

        if(speedKill.getNumber() > 0) {
            try {
                //插入订单
                orderMapper.insert(new Order(orderNo, id, 1));
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            speedKillMapper.updateStock(id, speedKill.getNumber() - count);
        }
    }

    /****
     * 有并发问题。
     * @param orderNo
     * @param id
     * @param count
     */
    @Transactional
    public void updateStockWithRedisLock(String orderNo, long id, int count) {
        String redisLockKey = "product_" + id;
        String redisLockValue = UUID.randomUUID().toString();

        // set nx ex
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(redisLockKey, redisLockValue , 10, TimeUnit.SECONDS);
        if (flag) {
            SpeedKill speedKill = speedKillMapper.get(id);
            speedKillMapper.updateStock(id, speedKill.getNumber() - count);

            try {
                //插入订单
                orderMapper.insert(new Order(orderNo, id, 1));
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                //使用lua脚本释放锁
                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

                RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
                Object result = redisTemplate.execute(redisScript, Collections.singletonList(redisLockKey), redisLockValue);
                if(!Long.valueOf(1).equals(result)) {
                    throw new RuntimeException("Redis解锁异常!"); //事务回滚
                }
            }

        }
    }

    /***
     * 使用Redis lua script保证库存扣减的原子性
     *
     * 口库存和增加等单需要保持数据一致
     * @param orderNo
     * @param id
     * @param count
      */
    public void updateStockInRedis(String orderNo, long id, int count) {
        SpeedKill speedKill = speedKillMapper.get(id);
        String key = speedKill.getName();
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
        Object result = redisTemplate.execute(redisScript, Arrays.asList(key, String.valueOf(count)));

        if (Long.valueOf("1").equals(result)) {
            try {
                //插入订单
                orderMapper.insert(new Order(orderNo, id, 1)); //入库失败会导致数据不一致
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            System.out.println("更新库存成功");
        } else {
            System.out.println("下单失败");
        }
    }

}
