package com.pentamind.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pentamind.entity.Target;
import com.pentamind.mapper.TargetMapper;
import com.pentamind.service.TargetService;
import org.springframework.stereotype.Service;

@Service
public class TargetServiceImpl extends ServiceImpl<TargetMapper, Target> implements TargetService {
}
