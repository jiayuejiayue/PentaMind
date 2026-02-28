package com.pentamind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pentamind.entity.Plugin;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PluginMapper extends BaseMapper<Plugin> {
}
