package io.github.jesse0722.speedkill.controller;

import io.github.jesse0722.speedkill.module.SpeedKill;
import io.github.jesse0722.speedkill.service.SpeedKillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author Lijiajun
 * @date 2020/11/23 15:06
 */
@RestController
@RequestMapping("/order")
public class SpeedKillController {
    @Autowired
    private SpeedKillService speedKillService;


    @GetMapping("/{id}")
    public SpeedKill get(@PathVariable Long id) {
        return speedKillService.get(id);
    }

    @GetMapping("/kill/{id}")
    public String kill(@PathVariable Long id) {
        //speedKillService.update(id); //方案1，基于数据库排他锁

        //speedKillService.updateStockByRedis(id); //方案2，基于redis的分布式锁
        speedKillService.updateStockInRedis(id); //方案3，基于redis的lua
        return "success";
    }

}