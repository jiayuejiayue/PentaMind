package com.pentamind.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pentamind.entity.Port;
import com.pentamind.mapper.PortMapper;
import com.pentamind.service.PortService;
import org.springframework.stereotype.Service;

@Service
public class PortServiceImpl extends ServiceImpl<PortMapper, Port> implements PortService {
}
