package com.nailong.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nailong.netdisk.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("select * from sys_user where username = #{username} order by ${order}")
    List<User> searchByUsername(@Param("username") String username, @Param("order") String order);
}
