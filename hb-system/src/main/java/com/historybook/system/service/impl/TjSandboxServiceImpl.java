package com.historybook.system.service.impl;

import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.system.entity.tongjian.TjPlaceDict;
import com.historybook.system.entity.tongjian.TjReign;
import com.historybook.system.mapper.TjSandboxMapper;
import com.historybook.system.service.ITjSandboxService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 读通鉴-沙盘态势 服务层处理
 * <p>
 * 思路：段落 TAGS 里混着分类/人物/官职/地名（简繁混合、地名多为通名），
 * 逐个拿去查 CHGIS 地名字典（精确 + 通名带政区后缀），查不到的忽略；
 * 同名多处时按纪年公元年份过滤始置/废置区间，取年代最吻合的一条。
 */
@Service
public class TjSandboxServiceImpl implements ITjSandboxService
{
    /** 合法的政区后缀（通名匹配时要求字典名 = 标签 + 后缀） */
    private static final Set<String> ADMIN_SUFFIX = new HashSet<>(Arrays.asList(
            "县", "縣", "州", "郡", "府", "军", "軍", "国", "國", "道", "路", "卫", "衛",
            "城", "厅", "廳", "司", "镇", "鎮", "寨", "关", "關", "监", "監"));

    /** 非地名的通用分类标签，直接跳过避免噪音 */
    private static final Set<String> SKIP_TAGS = new HashSet<>(Arrays.asList(
            "人事", "内政", "外交", "军事", "权争", "史论", "经济", "文化", "灾异", "法制",
            "民族", "宗教", "科技", "民生", "财政", "水利", "漕运", "屯田", "选举", "教育"));

    /** 单段最多返回的点位数 */
    private static final int MAX_PLACES = 60;

    /** 字典地名缓存（简体+繁体全集，懒加载） */
    private volatile Set<String> dictNameCache;

    @Resource
    private TjSandboxMapper sandboxMapper;

    @Override
    public Set<String> matchableTags(java.util.Collection<String> tags)
    {
        Set<String> names = dictNames();
        Set<String> result = new HashSet<>();
        for (String tag : tags)
        {
            if (tag == null)
            {
                continue;
            }
            String t = tag.trim();
            if (t.length() < 2 || SKIP_TAGS.contains(t))
            {
                continue;
            }
            if (names.contains(t))
            {
                result.add(t);
                continue;
            }
            for (String suffix : ADMIN_SUFFIX)
            {
                if (names.contains(t + suffix))
                {
                    result.add(t);
                    break;
                }
            }
        }
        return result;
    }

    /** 字典地名全集（懒加载缓存，字典只在启动导入时写入） */
    private Set<String> dictNames()
    {
        Set<String> cache = dictNameCache;
        if (cache == null)
        {
            synchronized (this)
            {
                if (dictNameCache == null)
                {
                    dictNameCache = new HashSet<>(sandboxMapper.selectAllDictNames());
                }
                cache = dictNameCache;
            }
        }
        return cache;
    }

    @Override
    public AjaxResult paragraph(Long contentId)
    {
        String tags = sandboxMapper.selectParagraphTags(contentId);
        if (tags == null || tags.trim().isEmpty())
        {
            return AjaxResult.success("该段落没有标签，无法生成沙盘", null);
        }
        TjReign reign = sandboxMapper.selectReignByContentId(contentId);
        Integer year = reign != null ? reign.getYearInt() : null;

        // 标签去重保序
        Set<String> tagSet = new LinkedHashSet<>();
        for (String t : tags.split(","))
        {
            String tag = t.trim();
            if (tag.length() >= 2 && !SKIP_TAGS.contains(tag))
            {
                tagSet.add(tag);
            }
        }

        List<Map<String, Object>> places = new ArrayList<>();
        Set<String> seenCoord = new HashSet<>();
        for (String tag : tagSet)
        {
            if (places.size() >= MAX_PLACES)
            {
                break;
            }
            List<TjPlaceDict> candidates = sandboxMapper.selectPlacesByName(tag);
            TjPlaceDict best = pickBest(candidates, tag, year);
            if (best == null || best.getXCoor() == null || best.getYCoor() == null)
            {
                continue;
            }
            // 同坐标去重（不同标签命中同一地点）
            String coordKey = Math.round(best.getXCoor() * 1000) + "_" + Math.round(best.getYCoor() * 1000);
            if (!seenCoord.add(coordKey))
            {
                continue;
            }
            Map<String, Object> p = new HashMap<>();
            p.put("tag", tag);
            p.put("name", best.getNameFt() != null ? best.getNameFt() : best.getNameCh());
            p.put("nameCh", best.getNameCh());
            p.put("x", best.getXCoor());
            p.put("y", best.getYCoor());
            p.put("typeCh", best.getTypeCh());
            p.put("presLoc", best.getPresLoc());
            p.put("begYr", best.getBegYr());
            p.put("endYr", best.getEndYr());
            places.add(p);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("contentId", contentId);
        data.put("year", year);
        data.put("reignTitle", reign != null
                ? (reign.getEmperorName() == null ? "" : reign.getEmperorName())
                + (reign.getReignName() == null ? "" : reign.getReignName())
                + (reign.getYearAnno() == null ? "" : reign.getYearAnno())
                : "");
        data.put("places", places);
        return AjaxResult.success(data);
    }

    /**
     * 从候选里挑最合适的一条：
     * 1. 通名前缀匹配的，要求后缀是合法政区后缀（避免「晋」命中「晋阳县」这类噪音）；
     * 2. 有公元年份时，优先取始置年<=year<=废置年的；都不含则取始置年最接近的。
     */
    private TjPlaceDict pickBest(List<TjPlaceDict> candidates, String tag, Integer year)
    {
        TjPlaceDict best = null;
        int bestScore = Integer.MIN_VALUE;
        for (TjPlaceDict d : candidates)
        {
            if (!nameMatches(d, tag))
            {
                continue;
            }
            int score = 0;
            // 精确名优先于通名+后缀
            if (tag.equals(d.getNameCh()) || tag.equals(d.getNameFt()))
            {
                score += 1000;
            }
            if (year != null)
            {
                int beg = d.getBegYr() != null ? d.getBegYr() : -99999;
                int end = d.getEndYr() != null ? d.getEndYr() : 99999;
                if (year >= beg && year <= end)
                {
                    score += 500;
                }
                else if (d.getBegYr() != null)
                {
                    // 年代越近越好（差距封顶 2000 年）
                    score += Math.max(0, 400 - Math.abs(d.getBegYr() - year) / 5);
                }
            }
            if (score > bestScore)
            {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /** 字典名与标签是否构成「同名」或「通名+政区后缀」 */
    private boolean nameMatches(TjPlaceDict d, String tag)
    {
        if (tag.equals(d.getNameCh()) || tag.equals(d.getNameFt()))
        {
            return true;
        }
        for (String name : new String[] { d.getNameCh(), d.getNameFt() })
        {
            if (name != null && name.startsWith(tag) && name.length() > tag.length())
            {
                String suffix = name.substring(tag.length());
                if (ADMIN_SUFFIX.contains(suffix))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
