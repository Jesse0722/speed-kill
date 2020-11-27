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


INSERT INTO `testdb`.`speedkill`(`id`, `name`, `number`, `start_time`, `end_time`, `create_time`) VALUES (1000, 'iphone12 128g 黑色', 999, '2020-11-20 16:00:00', '2020-11-21 16:00:00', '2020-11-19 16:00:00');

DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
     `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
     `no` varchar(120) NOT NULL COMMENT '订单号',
     `product_id` bigint NOT NULL COMMENT '秒杀商品id',
     `status` int(4) NOT NULL COMMENT '秒杀状态',
     `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     PRIMARY KEY (`id`),
     UNIQUE KEY `idx_no` (`no`),
     KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8 COMMENT='订单表';

SET FOREIGN_KEY_CHECKS = 1;

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
                orderMapper.insert(new Order(orderNo, id, 1));
                Thread.sleep(100);//模拟其他业务操作
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            speedKillMapper.updateStock(id, speedKill.getNumber() -1);
        }
    }
```



**压测测试：**  wrk -t4 -c20 -d60S http://localhost:8080/order/kill/1000

**测试结果**：共处理309笔交易，TPS:5.15



### **方案2: 使用Redis分布式锁，来维护对库存的扣减**

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
            SpeedKill speedKill = speedKillMapper.get(id);
            speedKillMapper.updateStock(id, speedKill.getNumber() - count);
            try {
                  //插入订单
                orderMapper.insert(new Order(orderNo, id, 1));
                Thread.sleep(100);//模拟其他业务操作
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

可以看到相比方案1，方案2并没有什么提升，只有不到10的TPS，这是因为分布式锁限制了在更新库存也是排他性的，因此和方案一没有本质区别。



### 方案3: 库存查询和扣除操作都在redis进行，通过lua脚本进行原子操作和redis的单线程特性来维护库存。

利用lua脚本，将库存查询和减库存操作都放到redis中，通过redis单线程并且原子执行等特点提高处理速度。

```java
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
            Thread.sleep(100); //模拟其他业务操作
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//            System.out.println("更新库存成功");
    } else {
        System.out.println("下单失败");
    }
}
```

**压测测试：**  wrk -t4 -c20 -d30S http://localhost:8080/order/kill/1000

**测试结果**：共处理11142笔交易，TPS:185.7。

可以看到使用lua脚本，将库存扣减操作交给redis后，利用redis单线程多路复用的IO机制极大的提升了处理能力。



### **问题：**

1. 方案三种如何保值redis中的库存与数据库数据一致性？

2. 如何更进一步提高TPS？





## 大批量订单处理---性能优化

```
1. 10-根据课程提供的场景，实现一个订单处理Service，模拟处理100万订单：后面提供模拟数据。
2. 20-使用多线程方法优化订单处理，对比处理性能
3. 30-使用并发工具和集合类改进订单Service，对比处理性能
4. 30-使用分布式集群+分库分表方式处理拆分订单，对比处理性能：第6模块讲解分库分表。
5. 30-使用读写分离和分布式缓存优化订单的读性能：第6、8模块讲解读写分离和缓存。
```

打捞下单成功的订单，假设有100万，进行后续处理，这里简单的修改订单状态模拟后续操作。

### 方案一：单线程处理

```java
/***
 * 单线程处理大量订单
 * @param order
 */
public void dealOrderSingleThread(final Order order) {
    long beginTime = System.currentTimeMillis();

    int total = orderMapper.getTotal(order);

    int offset = 0;
    do {
        List<Order> orders = orderMapper.findList(order, offset, limit);
        orders.forEach(item -> {
            item.setStatus(2);
            orderMapper.update(item);
        });
        offset = offset + limit;
    }while(offset < total);

    long endTime = System.currentTimeMillis();
    System.out.println("Order deal spend time:" + (endTime - beginTime));
}
```

### 方案二：多线程处理

```java
/***
 * 多线程处理订单
 * @param order
 */
public void dealOrderMultiThread(final Order order) {
    long beginTime = System.currentTimeMillis();

    int total = orderMapper.getTotal(order);

    int offset = 0;
    do {
        List<Order> orders = orderMapper.findList(order, limit, offset);
        orders.forEach(item -> executorService.submit(() -> {
            item.setStatus(2);
            orderMapper.update(item);
        }));
        offset = offset + limit;
    } while(offset < total);


    long endTime = System.currentTimeMillis();
    System.out.println("Order deal spend time:" + (endTime - beginTime));
}
```



### 方案三：使用JUC并发工具

使用fork-join框架，将大任务分解成多个子任务并发执行。

```java
/***
 * JUC并发工具处理订单
 * @param order
 */
public void dealOrderWithJUC(final Order order) {
    long beginTime = System.currentTimeMillis();

    int total = orderMapper.getTotal(order);

    ForkJoinPool forkJoinPool = new ForkJoinPool();

    OrderTask orderTask = new OrderTask(0, total, order);
    Future<AtomicInteger> result = forkJoinPool.submit(orderTask);

    try {
        System.out.println("成功处理订单个数:" + result.get());
    } catch (InterruptedException e) {
        e.printStackTrace();
    } catch (ExecutionException e) {
        e.printStackTrace();
    }
    long endTime = System.currentTimeMillis();
    System.out.println("Order deal spend time:" + (endTime - beginTime));
}


private class OrderTask extends RecursiveTask<AtomicInteger> {
    public static final int THRESHOLD = limit;

    private final int start;
    private final int end;
    private final Order order;

    public OrderTask(int start, int end, Order order) {
        this.start = start;
        this.end = end;
        this.order = order;
    }

    @Override
    protected AtomicInteger compute() {
        final AtomicInteger sum = new AtomicInteger(0);

        boolean canCompute = (end - start) <= THRESHOLD;
        if(canCompute) {
            List<Order> orders = orderMapper.findList(order, start, limit);
            orders.forEach(item -> executorService.submit(() -> {
                item.setStatus(2);
                orderMapper.update(item);
                System.out.println("update success");
                sum.incrementAndGet(); //计算更新成功的订单个数
            }));
        } else {
            //如果任务大于阈值，就分裂成两个子任务计算
            int middle = (start + end) / 2;
            OrderTask leftTask = new OrderTask(start, middle, order);
            OrderTask rightTask = new OrderTask(middle + 1, end, order);
            //执行子任务
            leftTask.fork();
            rightTask.fork();
            //等待子任务执行完，并得到结果
            AtomicInteger leftResult = leftTask.join();
            AtomicInteger rightResult = leftTask.join();
            sum.set(leftResult.get() + rightResult.get());
        }
        return sum;
    }
}
```



### 问题：

1. 有没有更好的处理大批量数据的方法？



## 后续优化的点

1. 分库分表
2. 读写分离
