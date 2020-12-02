package io.github.jesse0722.speedkill.service;

import io.github.jesse0722.speedkill.dao.OrderMapper;
import io.github.jesse0722.speedkill.module.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lijiajun
 * @date 2020/11/25 22:08
 */
@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;


    private final static int limit = 50;

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

    public void insertSingleThread(int number) {
        long begin = System.currentTimeMillis();
        for (int i = 0; i < number; i++) {
            Order order = new Order(UUID.randomUUID().toString(), (long) (Math.random() * 100), 1);
            try {
                orderMapper.insert(order);
            } catch (Exception e) {
                log.error("{}", e.getMessage(),e);
            }
        }
        System.out.println("insertSingleThread Spend Time:" + (System.currentTimeMillis() - begin));
    }

    public void insertMultiThread(int number) {
        long begin = System.currentTimeMillis();
        for (int i = 0; i < number; i++) {
            Order order = new Order(UUID.randomUUID().toString(), (long) (Math.random() * 100), 1);
            executorService.submit(()-> orderMapper.insert(order));
        }
        System.out.println("insertMultiThread Spend Time:" + (System.currentTimeMillis() - begin));
    }

    public void insertBatchGroup(final int number) {
        long begin = System.currentTimeMillis();
        int orderNum = number;
        while(orderNum > 0) {
            int initSize = orderNum > 500 ? 500 : orderNum;
            List<Order> orders = new ArrayList<>(initSize);
            for (int i = 0; i < initSize; i++) {
                Order order = new Order(UUID.randomUUID().toString(), (long) (Math.random() * 100), 1);
                orders.add(order);
            }
            orderMapper.insertBatch(orders);
            orderNum = orderNum - 500;
        }
        System.out.println("insertBatchGroup Spend Time:" + (System.currentTimeMillis() - begin));

    }
}
