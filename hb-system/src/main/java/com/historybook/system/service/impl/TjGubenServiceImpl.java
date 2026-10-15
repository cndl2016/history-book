package com.historybook.system.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.historybook.common.config.InfoConfig;
import com.historybook.common.core.utils.StringUtils;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.system.entity.tongjian.TjContent;
import com.historybook.system.entity.tongjian.TjGubenAnchor;
import com.historybook.system.entity.tongjian.TjGubenPage;
import com.historybook.system.mapper.TjGubenMapper;
import com.historybook.system.service.ISysConfigService;
import com.historybook.system.service.ITjGubenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 读通鉴-古本 服务层处理
 *
 * 本地缓存优先，令牌（tongjian.guben.token）仅用于回源回填：
 * - 段落锚点与扫描页元数据缓存于 tj_guben_anchor / tj_guben_page；
 * - 扫描页图片下载到 {profile}/guben/ 目录，经 /profile/guben/xxx.jpg 匿名访问；
 * - 已缓存内容的查看、翻页完全离线，不再需要令牌。
 */
@Service
public class TjGubenServiceImpl implements ITjGubenService
{
    private static final Logger log = LoggerFactory.getLogger(TjGubenServiceImpl.class);

    private static final String GUBEN_URL = "https://www.dutongjian.com/api/guben/view";

    /** 参数设置中的令牌键名（仅回填时需要） */
    public static final String TOKEN_CONFIG_KEY = "tongjian.guben.token";

    /** 本地图片目录（相对 profile 根） */
    private static final String LOCAL_DIR = "guben";

    /** 本地图片 URL 前缀（profile 静态映射，匿名可访问） */
    private static final String LOCAL_URL_PREFIX = "/profile/" + LOCAL_DIR + "/";

    /** 回填限速：每次上游请求间隔 */
    private static final long CRAWL_INTERVAL_MS = 300L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Resource
    private ISysConfigService configService;

    @Resource
    private TjGubenMapper gubenMapper;

    /** 回填任务状态 */
    private final AtomicBoolean crawling = new AtomicBoolean(false);
    private volatile boolean crawlStopRequested = false;
    private final Map<String, Object> crawlProgress = new LinkedHashMap<>();

    // ---------------------------------------------------------------- 查看

    @Override
    public AjaxResult view(Map<String, Object> payload)
    {
        String mode = String.valueOf(payload.getOrDefault("mode", "default"));
        if ("anchor".equals(mode))
        {
            return viewAnchor(payload);
        }
        if ("page".equals(mode))
        {
            return viewPage(payload);
        }
        return AjaxResult.error("古本查看模式不支持：" + mode);
    }

    /** 段落定位：先查本地锚点缓存，未命中再回源 */
    private AjaxResult viewAnchor(Map<String, Object> payload)
    {
        String tongjianId = String.valueOf(payload.get("paragraphTongjianId"));
        Integer startIndex = payload.get("startIndex") == null ? 0
                : Integer.valueOf(String.valueOf(payload.get("startIndex")));
        String selectedText = String.valueOf(payload.getOrDefault("selectedText", ""));

        TjGubenAnchor anchor = gubenMapper.selectAnchor(tongjianId, startIndex);
        if (anchor != null)
        {
            TjGubenPage page = gubenMapper.selectPage(anchor.getImageName());
            AjaxResult local = tryLocalResult(page, anchor);
            if (local != null)
            {
                return local;
            }
        }

        // 本地未命中，回源（需要令牌）
        String token = currentToken();
        if (token == null)
        {
            return AjaxResult.error("该段落古本尚未缓存到本地，且未配置回填令牌（" + TOKEN_CONFIG_KEY
                    + "，可在「参数设置」中配置后回填一次，之后离线可看）");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("mode", "anchor");
        body.put("paragraph_tongjian_id", tongjianId);
        body.put("start_index", startIndex);
        body.put("selected_text", selectedText);
        JSONObject data = callUpstream(body, token);
        if (data == null)
        {
            return AjaxResult.error("古本回源失败，请稍后重试或检查 " + TOKEN_CONFIG_KEY + " 是否有效");
        }
        // 落库：锚点 + 页元数据
        if (anchor == null)
        {
            anchor = new TjGubenAnchor();
            anchor.setTongjianId(tongjianId);
            anchor.setStartIndex(startIndex);
            anchor.setImageName(data.getString("image_name"));
            JSONObject box = data.getJSONObject("anchor_box");
            if (box != null)
            {
                anchor.setBoxTop(box.getInteger("location_top"));
                anchor.setBoxLeft(box.getInteger("location_left"));
                anchor.setBoxWidth(box.getInteger("location_width"));
                anchor.setBoxHeight(box.getInteger("location_height"));
            }
            gubenMapper.insertAnchor(anchor);
        }
        TjGubenPage page = savePageMeta(data);
        // 图片落盘
        String localUrl = ensureLocalImage(page, data.getString("image_url"));
        Map<String, Object> result = buildResult(page, localUrl != null ? localUrl : data.getString("image_url"));
        result.put("anchorBox", anchorBoxMap(anchor));
        return AjaxResult.success(result);
    }

    /** 翻页：先沿本地页链走，断链再回源 */
    private AjaxResult viewPage(Map<String, Object> payload)
    {
        String imageName = String.valueOf(payload.get("imageName"));
        String direction = String.valueOf(payload.getOrDefault("direction", "next"));
        boolean toNext = !"prev".equals(direction);

        TjGubenPage current = gubenMapper.selectPage(imageName);
        String targetName = current == null ? null
                : (toNext ? current.getNextName() : current.getPrevName());
        if (StringUtils.isNotEmpty(targetName))
        {
            TjGubenPage target = gubenMapper.selectPage(targetName);
            AjaxResult local = tryLocalResult(target, null);
            if (local != null)
            {
                return local;
            }
        }

        String token = currentToken();
        if (token == null)
        {
            return AjaxResult.error("该页尚未缓存到本地，且未配置回填令牌（" + TOKEN_CONFIG_KEY + "）");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("mode", "page");
        body.put("image_name", imageName);
        body.put("direction", direction);
        JSONObject data = callUpstream(body, token);
        if (data == null)
        {
            return AjaxResult.error("古本回源失败，请稍后重试或检查 " + TOKEN_CONFIG_KEY + " 是否有效");
        }
        String newName = data.getString("image_name");
        TjGubenPage page = savePageMeta(data);
        // 建立双向页链
        if (current != null && StringUtils.isNotEmpty(newName))
        {
            gubenMapper.updatePageLinks(imageName,
                    toNext ? current.getPrevName() : newName,
                    toNext ? newName : current.getNextName());
            TjGubenPage np = gubenMapper.selectPage(newName);
            gubenMapper.updatePageLinks(newName,
                    toNext ? imageName : (np == null ? null : np.getPrevName()),
                    toNext ? (np == null ? null : np.getNextName()) : imageName);
        }
        String localUrl = ensureLocalImage(page, data.getString("image_url"));
        Map<String, Object> result = buildResult(page, localUrl != null ? localUrl : data.getString("image_url"));
        return AjaxResult.success(result);
    }

    /** 本地命中且文件存在时直接返回 */
    private AjaxResult tryLocalResult(TjGubenPage page, TjGubenAnchor anchor)
    {
        if (page == null || StringUtils.isEmpty(page.getLocalFile()))
        {
            return null;
        }
        File f = new File(InfoConfig.getProfile(), page.getLocalFile());
        if (!f.isFile())
        {
            return null;
        }
        Map<String, Object> result = buildResult(page, LOCAL_URL_PREFIX + page.getImageName());
        if (anchor != null)
        {
            result.put("anchorBox", anchorBoxMap(anchor));
        }
        return AjaxResult.success(result);
    }

    /** 组装返回（与前端 GubenViewer 约定的字段） */
    private Map<String, Object> buildResult(TjGubenPage page, String imageUrl)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("imageName", page.getImageName());
        result.put("imageUrl", imageUrl);
        result.put("hasPrev", page.getHasPrev() == null || page.getHasPrev() == 1);
        result.put("hasNext", page.getHasNext() == null || page.getHasNext() == 1);
        return result;
    }

    private Map<String, Object> anchorBoxMap(TjGubenAnchor anchor)
    {
        if (anchor.getBoxTop() == null)
        {
            return null;
        }
        Map<String, Object> box = new HashMap<>();
        box.put("locationTop", anchor.getBoxTop());
        box.put("locationLeft", anchor.getBoxLeft());
        box.put("locationWidth", anchor.getBoxWidth());
        box.put("locationHeight", anchor.getBoxHeight());
        return box;
    }

    /** 页元数据落库（不存在则插入，存在则更新翻页标记） */
    private TjGubenPage savePageMeta(JSONObject data)
    {
        String imageName = data.getString("image_name");
        int hasPrev = Boolean.TRUE.equals(data.getBoolean("has_prev")) ? 1 : 0;
        int hasNext = Boolean.TRUE.equals(data.getBoolean("has_next")) ? 1 : 0;
        TjGubenPage page = gubenMapper.selectPage(imageName);
        if (page == null)
        {
            page = new TjGubenPage();
            page.setImageName(imageName);
            page.setHasPrev(hasPrev);
            page.setHasNext(hasNext);
            gubenMapper.insertPage(page);
        }
        else
        {
            page.setHasPrev(hasPrev);
            page.setHasNext(hasNext);
            gubenMapper.updatePageNavFlags(page);
        }
        return page;
    }

    /**
     * 下载扫描页到本地 {profile}/guben/ 目录
     *
     * @return 本地 URL；下载失败返回 null（调用方回退为签名 URL）
     */
    private String ensureLocalImage(TjGubenPage page, String signedUrl)
    {
        String localFile = LOCAL_DIR + "/" + page.getImageName();
        Path target = Paths.get(InfoConfig.getProfile(), localFile);
        if (Files.isRegularFile(target))
        {
            if (StringUtils.isEmpty(page.getLocalFile()))
            {
                try
                {
                    gubenMapper.updatePageLocal(page.getImageName(), localFile, Files.size(target));
                }
                catch (Exception ignore) { }
            }
            return LOCAL_URL_PREFIX + page.getImageName();
        }
        if (StringUtils.isEmpty(signedUrl))
        {
            return null;
        }
        try
        {
            Files.createDirectories(target.getParent());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(signedUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200)
            {
                log.warn("古本图片下载失败 {}: HTTP {}", page.getImageName(), response.statusCode());
                return null;
            }
            try (InputStream in = response.body())
            {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(target);
            gubenMapper.updatePageLocal(page.getImageName(), localFile, size);
            page.setLocalFile(localFile);
            page.setFileSize(size);
            return LOCAL_URL_PREFIX + page.getImageName();
        }
        catch (Exception e)
        {
            log.warn("古本图片下载异常 {}: {}", page.getImageName(), e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- 回填

    @Override
    public AjaxResult startCrawl()
    {
        if (crawling.get())
        {
            return AjaxResult.error("回填任务正在运行中");
        }
        String token = currentToken();
        if (token == null)
        {
            return AjaxResult.error("回填需要配置 " + TOKEN_CONFIG_KEY + "（「参数设置」中维护），回填完成后查看不再需要令牌");
        }
        crawlStopRequested = false;
        crawlProgress.clear();
        crawlProgress.put("message", "启动中…");
        Thread thread = new Thread(() -> runCrawl(token), "guben-crawl");
        thread.setDaemon(true);
        thread.start();
        return AjaxResult.success("回填任务已启动");
    }

    @Override
    public AjaxResult stopCrawl()
    {
        if (!crawling.get())
        {
            return AjaxResult.error("回填任务未在运行");
        }
        crawlStopRequested = true;
        return AjaxResult.success("已请求停止，当前段落处理完后退出");
    }

    @Override
    public AjaxResult crawlStatus()
    {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", crawling.get());
        status.putAll(crawlProgress);
        status.put("anchorCount", gubenMapper.countAnchors());
        status.put("pageCount", gubenMapper.countPages());
        status.put("localPageCount", gubenMapper.countLocalPages());
        Long bytes = gubenMapper.sumLocalBytes();
        status.put("localBytes", bytes == null ? 0 : bytes);
        return AjaxResult.success(status);
    }

    private void runCrawl(String token)
    {
        if (!crawling.compareAndSet(false, true))
        {
            return;
        }
        int done = 0;
        int failed = 0;
        try
        {
            List<TjContent> list = gubenMapper.selectUncachedParagraphs();
            crawlProgress.put("total", list.size());
            crawlProgress.put("done", 0);
            crawlProgress.put("failed", 0);
            for (TjContent content : list)
            {
                if (crawlStopRequested)
                {
                    crawlProgress.put("message", "已手动停止");
                    break;
                }
                String text = StringUtils.isNotEmpty(content.getOriginal()) ? content.getOriginal()
                        : StringUtils.defaultString(content.getSimplified());
                int startIndex = (content.getNumber() == null ? 1 : content.getNumber()) - 1;
                try
                {
                    Map<String, Object> body = new HashMap<>();
                    body.put("mode", "anchor");
                    body.put("paragraph_tongjian_id", content.getTongjianId());
                    body.put("start_index", startIndex);
                    body.put("selected_text", text);
                    JSONObject data = callUpstream(body, token);
                    if (data == null)
                    {
                        failed++;
                    }
                    else
                    {
                        TjGubenAnchor anchor = new TjGubenAnchor();
                        anchor.setTongjianId(content.getTongjianId());
                        anchor.setStartIndex(startIndex);
                        anchor.setImageName(data.getString("image_name"));
                        JSONObject box = data.getJSONObject("anchor_box");
                        if (box != null)
                        {
                            anchor.setBoxTop(box.getInteger("location_top"));
                            anchor.setBoxLeft(box.getInteger("location_left"));
                            anchor.setBoxWidth(box.getInteger("location_width"));
                            anchor.setBoxHeight(box.getInteger("location_height"));
                        }
                        gubenMapper.insertAnchor(anchor);
                        TjGubenPage page = savePageMeta(data);
                        ensureLocalImage(page, data.getString("image_url"));
                        done++;
                        crawlProgress.put("message", content.getTongjianId() + " -> " + page.getImageName());
                    }
                }
                catch (Exception e)
                {
                    failed++;
                    log.warn("古本回填段落失败 {}: {}", content.getTongjianId(), e.getMessage());
                }
                crawlProgress.put("done", done);
                crawlProgress.put("failed", failed);
                try
                {
                    Thread.sleep(CRAWL_INTERVAL_MS);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!crawlStopRequested)
            {
                crawlProgress.put("message", failed == 0 ? "全部完成" : "完成（失败 " + failed + " 条，可再次启动续跑）");
            }
        }
        catch (Exception e)
        {
            log.error("古本回填任务异常", e);
            crawlProgress.put("message", "任务异常：" + e.getMessage());
        }
        finally
        {
            crawling.set(false);
        }
    }

    // ---------------------------------------------------------------- 上游

    private String currentToken()
    {
        String token = configService.selectConfigByKey(TOKEN_CONFIG_KEY);
        return StringUtils.isEmpty(token) || StringUtils.isEmpty(token.trim()) ? null : token.trim();
    }

    /** 调上游 /api/guben/view，失败返回 null */
    private JSONObject callUpstream(Map<String, Object> body, String token)
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GUBEN_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "history-book/1.0 (+guben backfill)")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                log.warn("古本上游返回 {}: {}", response.statusCode(), response.body());
                return null;
            }
            JSONObject json = JSON.parseObject(response.body());
            JSONObject data = json == null ? null : json.getJSONObject("data");
            if (data == null || StringUtils.isEmpty(data.getString("image_name")))
            {
                log.warn("古本上游数据为空: {}", response.body());
                return null;
            }
            return data;
        }
        catch (Exception e)
        {
            log.warn("古本上游请求异常: {}", e.getMessage());
            return null;
        }
    }
}
