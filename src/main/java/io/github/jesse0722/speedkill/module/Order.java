package io.github.jesse0722.speedkill.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

/**
 * @author Lijiajun
 * @date 2020/11/25 15:10
 */
@Data
@AllArgsConstructor
public class Order {

    private long id;

    private String no;

    private long productId;

    private int status;

    private Date createTime;

    public Order(String no, long productId, int status) {
        this.no = no;
        this.productId = productId;
        this.status = status;
        this.createTime = new Date();
    }

    public Order() {
    }
}
