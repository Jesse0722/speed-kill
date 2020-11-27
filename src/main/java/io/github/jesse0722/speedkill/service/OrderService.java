package io.github.jesse0722.speedkill.service;

import io.github.jesse0722.speedkill.dao.OrderMapper;
import io.github.jesse0722.speedkill.module.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lijiajun
 * @date 2020/11/25 22:08
 */
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ExecutorService executorService;


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
}
