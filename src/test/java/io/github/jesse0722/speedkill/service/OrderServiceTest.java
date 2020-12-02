package io.github.jesse0722.speedkill.service;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Lijiajun
 * @date 2020/12/02 22:31
 */
@SpringBootTest
@RunWith(SpringRunner.class)
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    void insertSingleThread() {
        orderService.insertSingleThread(20);
    }

    @Test
    void insertMultiThread() {
        orderService.insertMultiThread(20);
    }

    @Test
    void insertBatchGroup() {
        orderService.insertBatchGroup(20);
    }
}
