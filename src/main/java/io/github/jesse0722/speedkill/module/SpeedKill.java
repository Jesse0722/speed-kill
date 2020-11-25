package io.github.jesse0722.speedkill.module;

import lombok.Data;

import java.util.Date;

/**
 * @author Lijiajun
 * @date 2020/11/21 16:31
 */

@Data
public class SpeedKill {
    private long id;

    private String name;

    private int number;

    private Date startTime;

    private Date endTime;

    private Date createTime;
}
