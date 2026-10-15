package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.core.web.page.TableDataInfo;
import com.historybook.common.log.annotation.Log;
import com.historybook.common.log.enums.BusinessType;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.common.security.utils.SecurityUtils;
import com.historybook.system.entity.tongjian.TjFavorite;
import com.historybook.system.service.ITjFavoriteService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 读通鉴-收藏
 */
@RestController
@RequestMapping("/tongjian/favorite")
public class TjFavoriteController extends BaseController
{
    @Resource
    private ITjFavoriteService favoriteService;

    /**
     * 我的收藏列表
     */
    @RequiresPermissions("tongjian:favorite:list")
    @GetMapping("/list")
    @ApiOperation(value = "我的收藏列表")
    public TableDataInfo list()
    {
        startPage();
        List<TjFavorite> list = favoriteService.selectMyFavorites(SecurityUtils.getUserId());
        return getDataTable(list);
    }

    /**
     * 收藏段落
     */
    @RequiresPermissions("tongjian:favorite:add")
    @Log(title = "读通鉴收藏", businessType = BusinessType.INSERT)
    @PostMapping("/{contentId}")
    @ApiOperation(value = "收藏段落")
    public AjaxResult add(@PathVariable Long contentId)
    {
        boolean ok = favoriteService.addFavorite(SecurityUtils.getUserId(), contentId);
        return ok ? AjaxResult.success("收藏成功") : AjaxResult.error("已收藏过该段落");
    }

    /**
     * 取消收藏
     */
    @RequiresPermissions("tongjian:favorite:remove")
    @Log(title = "读通鉴收藏", businessType = BusinessType.DELETE)
    @DeleteMapping("/{contentId}")
    @ApiOperation(value = "取消收藏")
    public AjaxResult remove(@PathVariable Long contentId)
    {
        return toAjax(favoriteService.removeFavorite(SecurityUtils.getUserId(), contentId));
    }
}
