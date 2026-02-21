package com.nailong.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nailong.netdisk.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 移除存在 SQL 注入风险的方法，改用 Service 层的 QueryWrapper 实现
    // @Select("select * from sys_user where username = #{username} order by ${order}")
    // List<User> searchByUsername(@Param("username") String username, @Param("order") String order);
}
