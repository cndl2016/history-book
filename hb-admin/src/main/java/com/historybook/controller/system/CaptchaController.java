package com.historybook.controller.system;

import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.system.service.impl.SysLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 验证码操作处理
 *
 * @author dameng
 */
@RestController
public class CaptchaController {

    @Resource
    private SysLoginService loginService;

    /**
     * 生成验证码
     */
    @GetMapping("/captchaImage")
    public AjaxResult getCode() {
        return loginService.createCaptcha();
    }
}
