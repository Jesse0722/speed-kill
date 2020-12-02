package io.github.jesse0722.speedkill;

import io.github.jesse0722.speedkill.service.SpeedKillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpeedKillApplicationTests {
    @Autowired
    private SpeedKillService speedKillService;

}
