package com.historybook.system.config;


import com.historybook.system.service.ISysMessageTipService;
import com.historybook.system.utils.SysMessageTipUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;


/**
 * 国际化配置
 * @author dyy
 */
@Configuration
public class SysMessageTipConfiguration {

    private ISysMessageTipService sysMessageTipService;

    @Autowired(required = false)
    public void setSysMessageTipService(ISysMessageTipService sysMessageTipService) {
        this.sysMessageTipService = sysMessageTipService;
    }

    @PostConstruct
    public void init() {
        if(sysMessageTipService!=null){
            SysMessageTipUtil.init(sysMessageTipService);
        }
    }
}
