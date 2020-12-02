package io.github.jesse0722.speedkill.controller;

import io.github.jesse0722.speedkill.module.Order;
import io.github.jesse0722.speedkill.module.SpeedKill;
import io.github.jesse0722.speedkill.service.OrderService;
import io.github.jesse0722.speedkill.service.SpeedKillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * @author Lijiajun
 * @date 2020/11/23 15:06
 */
@RestController
@RequestMapping("/order")
public class SpeedKillController {
    @Autowired
    private SpeedKillService speedKillService;

    @Autowired
    private OrderService orderService;


    @GetMapping("/{id}")
    public SpeedKill get(@PathVariable Long id) {
        return speedKillService.get(id);
    }

    @GetMapping("/kill")
    public String kill(@RequestParam Long id) {
        String orderNo = UUID.randomUUID().toString();
//        speedKillService.updateStockForUpdate(orderNo, id, 1); //方案1，基于数据库排他锁

//        speedKillService.updateStockWithRedisLock(orderNo, id, 1); //方案2，基于redis的分布式锁
        speedKillService.updateStockInRedis(orderNo, id, 1); //方案3，基于redis的lua
        return "success";
    }

    @GetMapping("/deal/{id}")
    public String deal(@PathVariable Long id) {
        Order order = new Order();
        order.setProductId(id);
        order.setStatus(1);
//        orderService.dealOrderSingleThread(order);
//        orderService.dealOrderMultiThread();
        orderService.dealOrderWithJUC(order);
        return "success";
    }

    @GetMapping("/insertBatch")
    public String insertBatch(@RequestParam Integer number){
        orderService.insertBatchGroup(number);
        return "Success";
    }
}
