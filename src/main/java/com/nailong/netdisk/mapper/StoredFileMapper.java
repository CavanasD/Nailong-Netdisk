package com.nailong.netdisk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nailong.netdisk.entity.StoredFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFile> {
}
