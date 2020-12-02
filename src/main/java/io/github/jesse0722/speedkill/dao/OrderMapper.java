package io.github.jesse0722.speedkill.dao;

import io.github.jesse0722.speedkill.module.Order;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Lijiajun
 * @date 2020/11/25 15:15
 */
@Mapper
public interface OrderMapper {

    void insert(Order order);

    void update(Order order);

    int getTotal(Order order);

    List<Order> findList(@Param("order") Order order, @Param("offset") int offset, @Param("limit")int limit);

    void insertBatch(List<Order> list);
}
