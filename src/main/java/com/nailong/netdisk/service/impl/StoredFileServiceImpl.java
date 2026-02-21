package com.nailong.netdisk.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nailong.netdisk.entity.StoredFile;
import com.nailong.netdisk.mapper.StoredFileMapper;
import com.nailong.netdisk.service.StoredFileService;
import org.springframework.stereotype.Service;

@Service
public class StoredFileServiceImpl extends ServiceImpl<StoredFileMapper, StoredFile> implements StoredFileService {
}
