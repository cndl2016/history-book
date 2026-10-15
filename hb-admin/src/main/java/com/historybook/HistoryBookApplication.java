package com.historybook;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.historybook.common.core.utils.bean.DbTypeBean;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import javax.annotation.Resource;

/**
 * 启动程序
 *
 * @author historybook
 */
@EnableEncryptableProperties
@MapperScan("com.historybook.**.mapper")
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableAspectJAutoProxy(exposeProxy = true)
public class HistoryBookApplication {
    private static final Logger log = LoggerFactory.getLogger(HistoryBookApplication.class);

    @Resource
    private DbTypeBean dbTypeBean;

    public static void main(String[] args) {
        SpringApplication.run(HistoryBookApplication.class, args);
        log.warn("(♥◠‿◠)ﾉﾞ  History-Book 服务模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }

    @Bean
    public MybatisPlusInterceptor interceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbTypeBean.isH2() ? DbType.H2 : DbType.DM));
        return interceptor;
    }
}
