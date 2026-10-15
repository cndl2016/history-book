package com.historybook.system.service.impl;

import com.historybook.system.entity.tongjian.TjReign;
import com.historybook.system.entity.tongjian.TjVolume;
import com.historybook.system.mapper.TjCatalogMapper;
import com.historybook.system.service.ITjCatalogService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读通鉴-目录 服务层处理
 */
@Service
public class TjCatalogServiceImpl implements ITjCatalogService
{
    @Resource
    private TjCatalogMapper catalogMapper;

    @Override
    public List<TjVolume> selectCatalogTree()
    {
        List<TjVolume> volumes = catalogMapper.selectVolumeList();
        List<TjReign> reigns = catalogMapper.selectReignList();
        Map<Long, List<TjReign>> reignMap = new LinkedHashMap<>();
        for (TjReign reign : reigns)
        {
            reignMap.computeIfAbsent(reign.getVolumeId(), k -> new ArrayList<>()).add(reign);
        }
        for (TjVolume volume : volumes)
        {
            volume.setChildren(reignMap.getOrDefault(volume.getVolumeId(), new ArrayList<>()));
        }
        return volumes;
    }

    @Override
    public TjReign selectReignById(Long reignId)
    {
        return catalogMapper.selectReignById(reignId);
    }
}
