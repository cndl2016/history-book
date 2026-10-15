package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.system.service.ITjCatalogService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 读通鉴-目录（卷/纪年树）
 */
@RestController
@RequestMapping("/tongjian/catalog")
public class TjCatalogController extends BaseController
{
    @Resource
    private ITjCatalogService catalogService;

    /**
     * 目录树：卷 -> 纪年
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/tree")
    @ApiOperation(value = "读通鉴目录树")
    public AjaxResult tree()
    {
        return AjaxResult.success(catalogService.selectCatalogTree());
    }

    /**
     * 纪年详情（含卷名）
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/reign/{reignId}")
    @ApiOperation(value = "纪年详情")
    public AjaxResult reign(@PathVariable Long reignId)
    {
        return AjaxResult.success(catalogService.selectReignById(reignId));
    }
}
