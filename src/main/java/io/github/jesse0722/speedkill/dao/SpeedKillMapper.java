package io.github.jesse0722.speedkill.dao;

import io.github.jesse0722.speedkill.module.SpeedKill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author Lijiajun
 * @date 2020/11/21 16:30
 */
@Mapper
public interface SpeedKillMapper {

    SpeedKill get(long id);

    @Select("select * from speedkill where id = #{id} for update")
    SpeedKill getWithLock(long id);

    @Update("update speedkill set number = #{number} where id = #{id}")
    void updateStock(long id, int number);
}
