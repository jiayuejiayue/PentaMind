package com.pentamind.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pentamind.entity.Path;
import com.pentamind.mapper.PathMapper;
import com.pentamind.service.PathService;
import org.springframework.stereotype.Service;

@Service
public class PathServiceImpl extends ServiceImpl<PathMapper, Path> implements PathService {
}
