package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.system.service.ITjSandboxService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 读通鉴-沙盘态势（段落地名地图）
 */
@RestController
@RequestMapping("/tongjian/sandbox")
public class TjSandboxController extends BaseController
{
    @Resource
    private ITjSandboxService sandboxService;

    /**
     * 段落沙盘：返回该段涉及地名的坐标点位
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/paragraph")
    @ApiOperation(value = "段落沙盘态势")
    public AjaxResult paragraph(@RequestParam("contentId") Long contentId)
    {
        return sandboxService.paragraph(contentId);
    }
}
