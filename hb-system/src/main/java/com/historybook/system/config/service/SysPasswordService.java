package com.historybook.system.config.service;

import com.historybook.common.config.manager.AsyncManager;
import com.historybook.common.core.constant.TokenConstants;
import com.historybook.common.core.domain.SysUser;
import com.historybook.common.exception.user.UserPasswordNotMatchException;
import com.historybook.common.exception.user.UserPasswordRetryLimitExceedException;
import com.historybook.common.security.utils.SecurityUtils;
import com.historybook.common.utils.I18nMessageUtil;
import com.historybook.system.config.security.AsyncFactory;
import com.historybook.system.config.security.service.CacheService;
import com.historybook.system.config.security.utils.AuthenticationContextHolder;
import com.historybook.system.constant.MessageTipConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 登录密码方法
 *
 * @author dameng.cn
 */
@Component
public class SysPasswordService {
    @Resource
    private CacheService cacheService;

    /**
     * 最大重试次数
     */
    @Value(value = "${user.password.maxRetryCount}")
    private int maxRetryCount;

    /**
     * 锁定时间
     */
    @Value(value = "${user.password.lockTime}")
    private int lockTime;

    /**
     * 验证用户密码
     * @param user
     */
    public void validate(SysUser user, Locale locale) {
        // 身份验证
        Authentication usernamePasswordAuthenticationToken = AuthenticationContextHolder.getContext();
        // 账号
        String username = usernamePasswordAuthenticationToken.getName();
        // 密码
        String password = usernamePasswordAuthenticationToken.getCredentials().toString();
        // 重试次数
        Integer retryCount = cacheService.getCacheObject(getCacheKey(username));
        if (retryCount == null) {
            retryCount = 0;
        }
        // 超过最大重试次数：抛出异常
        if (retryCount >= maxRetryCount) {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, TokenConstants.LOGIN_FAIL,
                    I18nMessageUtil.getMessage(MessageTipConstant.SYSTEM_USER_PASSWORD_RETRY_LIMIT_EXCEED, locale, maxRetryCount, lockTime)));
            throw new UserPasswordRetryLimitExceedException(maxRetryCount, lockTime);
        }
        // 密码错误：次数+1，抛出异常
        if (!matches(user, password)) {
            retryCount = retryCount + 1;
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, TokenConstants.LOGIN_FAIL,
                    I18nMessageUtil.getMessage(MessageTipConstant.SYSTEM_USER_PASSWORD_RETRY_LIMIT_COUNT, locale, retryCount)));
            cacheService.setCacheObject(getCacheKey(username), retryCount, lockTime, TimeUnit.MINUTES);
            throw new UserPasswordNotMatchException();
        } else {
            // 验证成功：清除缓存
            clearLoginRecordCache(username);
        }
    }

    /**
     * 账号密码是否匹配
     * @param user
     * @param rawPassword
     * @return boolean
     */
    private boolean matches(SysUser user, String rawPassword) {
        return SecurityUtils.matchesPassword(rawPassword, user.getPassword());
    }

    /**
     * 登录账户密码错误次数缓存键名
     *
     * @param username 用户名
     * @return 缓存键key
     */
    private String getCacheKey(String username) {
        return TokenConstants.PWD_ERR_CNT_KEY + username;
    }

    /**
     * 清除登录记录缓存
     * @param loginName
     */
    private void clearLoginRecordCache(String loginName) {
        cacheService.clearLoginRecordCache(getCacheKey(loginName));
    }
}
