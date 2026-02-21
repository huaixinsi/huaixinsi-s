package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper

public interface ReportMapper {
    /**
     * 营业额统计
     * @param beginTime
     * @param endTime
     * @param completed
     * @return
     */
    @Select("select sum(amount) from orders where order_time between #{beginTime} and #{endTime} and status = #{completed}")
    Double getTurnoverStatistics(LocalDateTime beginTime, LocalDateTime endTime, Integer completed);
    @Select("select count(id) from user where create_time between #{beginTime} and #{endTime}")
    Integer getNewUserCount(LocalDateTime beginTime, LocalDateTime endTime);
    @Select("select count(id) from user where create_time <= #{endTime}")
    Integer getTotalUserCount(LocalDateTime endTime);

}
