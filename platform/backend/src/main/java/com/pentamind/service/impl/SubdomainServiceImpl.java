package com.pentamind.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pentamind.entity.Subdomain;
import com.pentamind.mapper.SubdomainMapper;
import com.pentamind.service.SubdomainService;
import org.springframework.stereotype.Service;

@Service
public class SubdomainServiceImpl extends ServiceImpl<SubdomainMapper, Subdomain> implements SubdomainService {
}
