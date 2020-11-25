# 秒杀系统关键设计及代码实现

我们都知道，秒杀场景下对库存的资源竞争是非常激烈的，如何在保证库存安全的情况下，提高系统的处理能力，是决定秒杀系统处理能力的关键一环。

基于此，我们去除了一些无关紧要的信息，只保留了一张库存表，用于模拟秒杀过程中资源库存同步的场景。

我们先来讨论一下秒杀的业务场景，我们假设用户每次下单过后都要支付

用户：登录->查询商品->如果改商品可以秒杀（是否在有效器，库存是否充足）-> 点击下单  ->秒杀成功

后台系统：用户点击下单，请求到后台根据id查询商品库并上锁（防止查询到相同库存，导致重复扣减库存导致超卖）-> 如果库存充足-> 事务处理（扣件库存以及其他数据操作）或者采用（消息中间件+补偿机制）来解决数据一致性的问题。

这里需要注意的是实际情况下，用户下单后，需要锁住响应库存，支付后扣件库存，如果用户超出支付时间过后用户未支付，应该将锁住的库存恢复回去。（下单锁定库存，支付减库存）

## **数据库表设计**

```mysql
-- ----------------------------
-- Table structure for speedkill
-- ----------------------------
DROP TABLE IF EXISTS `speedkill`;
CREATE TABLE `speedkill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `name` varchar(120) NOT NULL COMMENT '秒杀商品',
  `number` int NOT NULL COMMENT '库存数量',
  `start_time` timestamp NOT NULL COMMENT '秒杀开始时间',
  `end_time` timestamp NOT NULL COMMENT '秒杀结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_end_time` (`end_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8 COMMENT='秒杀库存表';

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO `testdb`.`speedkill`(`id`, `name`, `number`, `start_time`, `end_time`, `create_time`) VALUES (1000, 'iphone12 128g 黑色', 999, '2020-11-20 16:00:00', '2020-11-21 16:00:00', '2020-11-19 16:00:00');

```

## **代码实现**

### **方案1: 使用数据库排他锁**

将访问库存的操作加锁，保证只有一个线程在访问库存，如此来保证数据同步。

编码中需要注意的是，线程在加锁处理业务过程中，始终应处于一个事务当中，在事务提交前，不得释放锁。

```java
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
```



**压测测试：**  wrk -t4 -c20 -d60S http://localhost:8080/order/kill/1000

**测试结果**：共处理309笔交易，TPS:5.15



### **方案2: 将库存数据同步到redis，利用redis单线程处理的机制保存库存安全。**

在活动开始前，实现将数据库里商品的库存同步到redis，秒杀活动开始后，就直接在redis中进行库存操作，等活动结束后，再将redis的库存同步到数据库。

这里读取库存操作的时候依然需要进行加锁操作，否则依然会出现A、B同时读取数据库存为1，A执行更新操作0，B执行更新操作0的情况，导致超卖。

所以需要用到分布式锁，在查询库存的时候进行set nx ex加锁，处理完业务逻辑过后，在释放锁，释放锁的时候需要判断key值，防止误解锁。如果解锁失败，说明业务处理有异常需要进行回滚。

```java
		@Transactional
    public void updateStockByRedisLock(long id) {
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
```

这里只是用于单机redis的情况，没有考虑redis集群、可重入、可重拾等情况。分布式锁的解决方案可参考redisson。

**压测测试：**  wrk -t4 -c20 -d30S http://localhost:8080/order/kill/1000

**测试结果**：共处理507笔交易，TPS:8.95

可以看到相比方案1，方案2相对更抗压，但是性能也比较差，只有不到10的TPS，这是因为分布式锁限制了在更新库存也是排他性的，因此和方案一没有本质区别。



### 方案3: 库存查询于扣除操作都在redis进行，通过lua脚本进行原子操作和redis的单线程特性，消除锁。

利用lua脚本，将库存查询和减库存操作都放到redis中，通过redis单线程并且原子执行等特点提高处理速度。

```java
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
        luaScript.append("else return -1\n"); //如果商品不存在则返回-1
        luaScript.append("end\n");


        RedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript.toString(), Long.class);
        Object result = redisTemplate.execute(redisScript, Arrays.asList(redisLockKey, count));

        if(Long.valueOf("0").equals(result)) {
            System.out.println("商品不存在或已下架");
            return;
        }
        else if(Long.valueOf("1").equals(result)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("更新库存成功");
        }
        else {
            System.out.println("库存不足");
        }
    }
```

**压测测试：**  wrk -t4 -c20 -d30S http://localhost:8080/order/kill/1000

**测试结果**：共处理11142笔交易，TPS:185.7。

可以看到使用lua脚本，将库存扣减操作交给redis后，利用redis单线程多路复用的IO机制极大的提升了处理能力。



## 后续优化的点

1. 如何保证库存数量和数据库数据一致？redis的库存和用户秒杀信息，订单信息等。
   * 使用分布式事务
   * 使用消息队列

2. 限流、熔断措施方案。

3. 如果某些情况下，不适合将库存放在redis进行操作，还有米有其他提高秒杀并发能力的方案？

   将竞争资源分组，类似ConcurrentHashmap。

4. Cluster集群环境下，基于redis内存的方案，redis集群间数据同步是否可靠？

   将库存分组，提供并行处理能力。
