package com.historybook.system.service.impl;

import com.historybook.common.core.domain.LoginUser;
import com.historybook.common.core.domain.SysUser;
import com.historybook.common.core.enums.UserStatus;
import com.historybook.common.core.exception.ServiceException;
import com.historybook.common.core.utils.StringUtils;
import com.historybook.common.utils.I18nMessageUtil;
import com.historybook.system.constant.MessageTipConstant;
import com.historybook.system.service.ISysPermissionService;
import com.historybook.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.cas.authentication.CasAssertionAuthenticationToken;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 用于加载用户信息 实现UserDetailsService接口，或者实现AuthenticationUserDetailsService接口
 *
 * @author cn
 * @date 2024/08/20
 */

@Service
public class CasUserDetailsServiceImpl implements AuthenticationUserDetailsService<CasAssertionAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(CasUserDetailsServiceImpl.class);

    @Resource
    private ISysUserService userService;

    @Resource
    private ISysPermissionService permissionService;

    @Override
    public UserDetails loadUserDetails(CasAssertionAuthenticationToken token) throws UsernameNotFoundException {
        String username = token.getName();
        SysUser user = userService.selectUserByUserName(username);
        if (StringUtils.isNull(user)) {
            log.info("登录用户：{} 不存在.", username);
            throw new ServiceException(I18nMessageUtil.getMessage(MessageTipConstant.SYSTEM_USER_ACCOUNT_NOT_EXISTS, username));
        } else if (UserStatus.DELETED.getCode().equals(user.getDelFlag())) {
            log.info("登录用户：{} 已被删除.", username);
            throw new ServiceException(I18nMessageUtil.getMessage(MessageTipConstant.SYSTEM_USER_ACCOUNT_STATUS_DELETE, username));
        } else if (UserStatus.DISABLE.getCode().equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", username);
            throw new ServiceException(I18nMessageUtil.getMessage(MessageTipConstant.SYSTEM_USER_ACCOUNT_STATUS_DISABLE, username));
        }

        return createLoginUser(user);
    }

    private UserDetails createLoginUser(SysUser user) {
        return new LoginUser(user.getUserId(), user.getDeptId(), user, permissionService.getMenuPermission(user.getUserId()));
    }
}
