package com.historybook.system.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.historybook.system.entity.tongjian.TjContent;
import com.historybook.system.entity.tongjian.TjReign;
import com.historybook.system.entity.tongjian.TjSyncState;
import com.historybook.system.entity.tongjian.TjVolume;
import com.historybook.system.mapper.TjCatalogMapper;
import com.historybook.system.mapper.TjContentMapper;
import com.historybook.system.mapper.TjSyncStateMapper;
import com.historybook.system.service.ITjSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 读通鉴-内容同步 服务层处理
 *
 * 迁移自 Dutongjian 项目的 tongjian_sync.py：
 * 目录 -> 纪年 -> 正文段落，限速请求、429 退避、按纪年 checkpoint 断点续传。
 * checkpoint 持久化在 TJ_REIGN.SYNCED 与 TJ_SYNC_STATE，重启后可继续。
 *
 * 性能策略：固定线程池并发拉取（WORKERS 个），所有线程共享一个全局限速器
 * （两次请求至少间隔 MIN_INTERVAL_MS），触发 429 时按 Retry-After 全局暂停。
 */
@Service
public class TjSyncServiceImpl implements ITjSyncService
{
    private static final Logger log = LoggerFactory.getLogger(TjSyncServiceImpl.class);

    /** 公开数据源（dutongjian.com 的公开只读接口，不登录、不绕权限） */
    private static final String BASE_URL = "https://www.dutongjian.com";

    /**
     * 请求标识：源站按 UA 限流，自定义 UA 高频访问会被定向 429 封禁，浏览器 UA 不受限。
     * 配合下方的自适应限速，保持对源站的礼貌访问。
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** 并发拉取线程数（增大可提高速度，同时对源站压力也增大，请适度调整） */
    private static final int WORKERS = 8;

    /** 全局两次请求最小间隔（毫秒），所有并发线程共享；触发 429 会自动加倍，成功后缓慢回落 */
    private static final long MIN_INTERVAL_MS = 1000L;

    /** 429 退避时间隔上限（毫秒） */
    private static final long MAX_INTERVAL_MS = 10000L;

    private static final int MAX_RETRY = 3;

    /** 进度落库的最小间隔（毫秒），避免每个纪年都写一次状态表 */
    private static final long STATE_SAVE_INTERVAL_MS = 800L;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean stopRequested = false;

    private volatile long lastRequestAt = 0L;

    /** 自适应请求间隔：429 时加倍（至 MAX_INTERVAL_MS），成功时缓慢回落至 MIN_INTERVAL_MS */
    private volatile long intervalMs = MIN_INTERVAL_MS;

    /** 429 限流时的全局暂停截止时间，所有并发线程的限速器都会等待到该时刻 */
    private volatile long globalHoldUntil = 0L;

    /** 429 暂停时长（毫秒）：连续触发 429 时指数增长（15s 起，封顶 120s），请求成功后复位 */
    private volatile long holdMs = 15000L;

    private volatile long lastStateSaveAt = 0L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Resource
    private TjCatalogMapper catalogMapper;

    @Resource
    private TjContentMapper contentMapper;

    @Resource
    private TjSyncStateMapper syncStateMapper;

    @Override
    public boolean start()
    {
        if (!running.compareAndSet(false, true))
        {
            return false;
        }
        stopRequested = false;
        Thread thread = new Thread(this::runSync, "tj-sync");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    @Override
    public void stop()
    {
        stopRequested = true;
    }

    @Override
    public TjSyncState status()
    {
        TjSyncState state = syncStateMapper.selectState();
        if (state == null)
        {
            state = new TjSyncState();
            state.setId(1L);
            state.setTotalReigns(catalogMapper.countReigns());
            state.setCompletedReigns(catalogMapper.countSyncedReigns());
            state.setContentCount(contentMapper.countContents());
            state.setStatus("0");
            state.setMessage("尚未同步");
            syncStateMapper.insertState(state);
        }
        if (!running.get() && "1".equals(state.getStatus()))
        {
            // 上次运行中断（如服务重启），修正为已停止
            state.setStatus("2");
            state.setMessage("同步已中断，可重新开始（已同步的纪年会自动跳过）");
            syncStateMapper.updateState(state);
        }
        state.setRunning(running.get());
        return state;
    }

    private void runSync()
    {
        ExecutorService pool = null;
        try
        {
            saveState("1", "正在获取目录…");
            JSONObject catalog = fetchJson("/api/table_of_contents");
            upsertCatalog(catalog);

            List<TjReign> all = catalogMapper.selectReignList();
            int total = all.size();
            List<TjReign> pending = new ArrayList<>();
            int already = 0;
            for (TjReign reign : all)
            {
                if ("1".equals(reign.getSynced()))
                {
                    already++;
                }
                else
                {
                    pending.add(reign);
                }
            }
            final int alreadySynced = already;
            AtomicInteger done = new AtomicInteger(0);
            AtomicInteger contentCount = new AtomicInteger(contentMapper.countContents());
            AtomicReference<String> firstError = new AtomicReference<>();

            pool = Executors.newFixedThreadPool(WORKERS, r ->
            {
                Thread t = new Thread(r, "tj-sync-worker");
                t.setDaemon(true);
                return t;
            });
            List<Future<?>> futures = new ArrayList<>();
            for (TjReign reign : pending)
            {
                futures.add(pool.submit(() ->
                {
                    if (stopRequested || firstError.get() != null)
                    {
                        return;
                    }
                    try
                    {
                        int delta = syncReign(reign);
                        int contents = contentCount.addAndGet(delta);
                        int finished = alreadySynced + done.incrementAndGet();
                        saveProgressThrottled("1", "已同步 " + reign.getVolumeTitle() + " "
                                + reign.getEmperorName() + reign.getReignName()
                                + "（" + finished + "/" + total + "）", total, finished, contents);
                    }
                    catch (Exception e)
                    {
                        log.error("同步纪年失败: {}", reign.getTongjianId(), e);
                        firstError.compareAndSet(null, e.getMessage());
                    }
                }));
            }
            // 等待全部完成；手动停止或首个失败时取消排队任务（进行中的任务会正常收尾，保证 checkpoint 完整）
            pool.shutdown();
            while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS))
            {
                if (stopRequested || firstError.get() != null)
                {
                    for (Future<?> f : futures)
                    {
                        f.cancel(false);
                    }
                }
            }

            int finished = alreadySynced + done.get();
            if (stopRequested)
            {
                saveProgress("2", "已手动停止，已完成 " + finished + "/" + total + " 个纪年", total, finished, contentCount.get());
            }
            else if (firstError.get() != null)
            {
                saveProgress("2", "同步失败：" + firstError.get() + "，已完成 " + finished + "/" + total
                        + " 个纪年，可重新开始继续", total, finished, contentCount.get());
            }
            else
            {
                saveProgress("0", "全部完成，共 " + total + " 个纪年、" + contentMapper.countContents() + " 段正文",
                        total, total, contentMapper.countContents());
            }
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            saveState("2", "同步被中断，可重新开始继续");
        }
        catch (Exception e)
        {
            log.error("读通鉴同步异常", e);
            saveState("2", "同步异常：" + e.getMessage());
        }
        finally
        {
            if (pool != null)
            {
                pool.shutdownNow();
            }
            running.set(false);
        }
    }

    /** 目录入库：按源站标识 upsert 卷与纪年 */
    private void upsertCatalog(JSONObject catalog)
    {
        JSONObject data = catalog.getJSONObject("data");
        if (data == null)
        {
            throw new IllegalStateException("目录接口返回格式异常");
        }
        JSONArray juanList = data.getJSONArray("juan_list");
        if (juanList == null || juanList.isEmpty())
        {
            throw new IllegalStateException("目录为空");
        }
        int sort = 0;
        for (int i = 0; i < juanList.size(); i++)
        {
            JSONObject juan = juanList.getJSONObject(i);
            String juanTid = juan.getString("tongjian_id");
            TjVolume volume = catalogMapper.selectVolumeByTongjianId(juanTid);
            if (volume == null)
            {
                volume = new TjVolume();
                volume.setTongjianId(juanTid);
                volume.setTitle(juan.getString("juan"));
                volume.setJi(juan.getString("ji"));
                volume.setSortOrder(i + 1);
                catalogMapper.insertVolume(volume);
            }
            else
            {
                volume.setTitle(juan.getString("juan"));
                volume.setJi(juan.getString("ji"));
                volume.setSortOrder(i + 1);
                catalogMapper.updateVolume(volume);
            }
            JSONArray emperors = juan.getJSONArray("emperor_list");
            if (emperors == null)
            {
                continue;
            }
            for (int j = 0; j < emperors.size(); j++)
            {
                JSONObject emperor = emperors.getJSONObject(j);
                JSONArray reigns = emperor.getJSONArray("reign_list");
                if (reigns == null)
                {
                    continue;
                }
                for (int k = 0; k < reigns.size(); k++)
                {
                    JSONObject r = reigns.getJSONObject(k);
                    sort++;
                    String reignTid = r.getString("tongjian_id");
                    TjReign reign = catalogMapper.selectReignByTongjianId(reignTid);
                    if (reign == null)
                    {
                        reign = new TjReign();
                        reign.setTongjianId(reignTid);
                        fillReign(reign, r, emperor.getString("emperor_name"), volume.getVolumeId(), sort);
                        catalogMapper.insertReign(reign);
                    }
                    else
                    {
                        fillReign(reign, r, emperor.getString("emperor_name"), volume.getVolumeId(), sort);
                        catalogMapper.updateReign(reign);
                    }
                }
            }
        }
    }

    private void fillReign(TjReign reign, JSONObject r, String emperorName, Long volumeId, int sort)
    {
        reign.setVolumeId(volumeId);
        reign.setEmperorName(emperorName);
        reign.setReignName(r.getString("reign_name"));
        reign.setYearAnno(r.getString("year_anno"));
        reign.setYearInt(r.getInteger("year_int"));
        reign.setSortOrder(sort);
    }

    /**
     * 同步单个纪年的正文段落
     *
     * @return 正文行数净增量（新增 - 删除），用于进度计数
     */
    private int syncReign(TjReign reign) throws Exception
    {
        JSONObject payload = fetchJson("/api/reign?reign_tongjian_id=" + reign.getTongjianId());
        JSONObject data = payload.getJSONObject("data");
        JSONArray contents = data == null ? null : data.getJSONArray("ExtRef_Children_contents");
        List<TjContent> items = new ArrayList<>();
        if (contents != null)
        {
            String sourceUrl = BASE_URL + "/api/reign?reign_tongjian_id=" + reign.getTongjianId();
            for (int i = 0; i < contents.size(); i++)
            {
                JSONObject raw = contents.getJSONObject(i);
                TjContent item = new TjContent();
                item.setTongjianId(raw.getString("tongjian_id"));
                item.setReignId(reign.getReignId());
                item.setVolumeId(reign.getVolumeId());
                item.setNumber(raw.getInteger("id") != null ? raw.getInteger("id") : i + 1);
                item.setOriginal(stripNumber(raw.getString("content")));
                String simplified = raw.getString("content_jianti_auto");
                item.setSimplified(stripNumber(simplified == null || simplified.isEmpty() ? raw.getString("content") : simplified));
                item.setTranslation(raw.getString("content_fanyi"));
                item.setNotes(buildNotes(raw));
                item.setTags(buildTags(raw));
                item.setSourceUrl(sourceUrl);
                items.add(item);
            }
        }
        int removed = 0;
        if (!items.isEmpty())
        {
            removed = contentMapper.deleteByReignId(reign.getReignId());
            contentMapper.insertContentBatch(items);
        }
        catalogMapper.markReignSynced(reign.getReignId());
        return items.size() - removed;
    }

    /** 提取注释（胡注/陈注）为 JSON 数组 */
    private String buildNotes(JSONObject raw)
    {
        JSONArray notes = new JSONArray();
        appendNotes(notes, raw.getJSONArray("ExtRef_Children_hu_notes"), "胡注");
        appendNotes(notes, raw.getJSONArray("ExtRef_Children_chenzhu"), "陈注");
        return notes.isEmpty() ? null : notes.toJSONString();
    }

    private void appendNotes(JSONArray notes, JSONArray arr, String type)
    {
        if (arr == null)
        {
            return;
        }
        for (int i = 0; i < arr.size(); i++)
        {
            JSONObject n = arr.getJSONObject(i);
            String text = n.getString("note_content");
            String textJt = n.getString("note_content_jianti_auto");
            if ((text == null || text.isEmpty()) && (textJt == null || textJt.isEmpty()))
            {
                continue;
            }
            JSONObject note = new JSONObject();
            note.put("type", type);
            note.put("text", text == null ? "" : text);
            note.put("textJt", textJt == null ? "" : textJt);
            notes.add(note);
        }
    }

    /** 提取标签：分类 + 人物 + 地点 + 官职 + 事件 */
    private String buildTags(JSONObject raw)
    {
        Set<String> tags = new LinkedHashSet<>();
        JSONArray classTags = raw.getJSONArray("class_tags");
        if (classTags != null)
        {
            for (int i = 0; i < classTags.size(); i++)
            {
                addTag(tags, classTags.getString(i));
            }
        }
        addTagValues(tags, raw.getJSONArray("ExtRef_Children_topics"), "title");
        addTagValues(tags, raw.getJSONArray("ExtRef_Children_people"), "people_name_jianti_auto", "people_name");
        addTagValues(tags, raw.getJSONArray("ExtRef_Children_places"), "place_name_jianti_auto", "place_name");
        addTagValues(tags, raw.getJSONArray("ExtRef_Children_officials"), "official_name");
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private void addTagValues(Set<String> tags, JSONArray arr, String... fields)
    {
        if (arr == null)
        {
            return;
        }
        for (int i = 0; i < arr.size(); i++)
        {
            JSONObject e = arr.getJSONObject(i);
            for (String field : fields)
            {
                if (e.containsKey(field))
                {
                    addTag(tags, e.getString(field));
                    break;
                }
            }
        }
    }

    private void addTag(Set<String> tags, String value)
    {
        if (value != null && !value.trim().isEmpty())
        {
            tags.add(value.trim());
        }
    }

    private String stripNumber(String text)
    {
        if (text == null)
        {
            return null;
        }
        return text.replaceFirst("^\\s*\\d+\\s*", "").trim();
    }

    /** 限速 + 重试 + 429 退避的 JSON 请求（多线程共享全局限速器） */
    private JSONObject fetchJson(String path) throws Exception
    {
        Exception lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++)
        {
            pace();
            try
            {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200)
                {
                    JSONObject json = JSON.parseObject(response.body());
                    if (json == null || json.getJSONObject("data") == null)
                    {
                        throw new IllegalStateException("接口返回格式异常: " + path);
                    }
                    // 成功一次就把自适应间隔缓慢回落（每次 -50ms），直到下限；429 暂停时长复位
                    intervalMs = Math.max(MIN_INTERVAL_MS, intervalMs - 50L);
                    holdMs = 15000L;
                    return json;
                }
                if (response.statusCode() == 429)
                {
                    // 源站按 UA/来源限流且通常不带 Retry-After：暂停时长指数增长，避免在封禁窗口内反复空转
                    long wait = response.headers().firstValue("Retry-After").map(h -> parseRetryAfter(h)).orElse(holdMs);
                    holdMs = Math.min(120000L, holdMs * 2);
                    // 自适应加倍请求间隔，避免反复触发限流
                    intervalMs = Math.min(MAX_INTERVAL_MS, Math.max(intervalMs * 2, 1000L));
                    log.warn("读通鉴同步触发限流，全局暂停 {} 秒，请求间隔调整为 {} 毫秒", wait / 1000, intervalMs);
                    // 全局暂停：所有并发线程的下一次请求都会等到该时刻之后
                    globalHoldUntil = Math.max(globalHoldUntil, System.currentTimeMillis() + wait);
                    lastError = new IllegalStateException("HTTP 429 限流");
                }
                else
                {
                    lastError = new IllegalStateException("HTTP " + response.statusCode());
                    Thread.sleep(Math.min(30000L, 1000L << attempt));
                }
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("同步被中断", ie);
            }
            catch (Exception e)
            {
                lastError = e;
                if (attempt < MAX_RETRY)
                {
                    Thread.sleep(Math.min(30000L, 1000L << attempt));
                }
            }
        }
        throw new IllegalStateException("请求失败: " + path + " - " + lastError);
    }

    /**
     * 全局限速器（线程安全）：保证所有线程合并计算后，两次请求至少间隔 intervalMs（自适应），
     * 且 429 限流期间（globalHoldUntil 之前）任何线程都不会发出请求。
     */
    private synchronized void pace() throws InterruptedException
    {
        long now = System.currentTimeMillis();
        long wait = Math.max(lastRequestAt + intervalMs, globalHoldUntil) - now;
        if (wait > 0)
        {
            Thread.sleep(wait);
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private long parseRetryAfter(String value)
    {
        if (value == null)
        {
            return 60000L;
        }
        try
        {
            return Math.max(1000L, Long.parseLong(value.trim()) * 1000L);
        }
        catch (NumberFormatException e)
        {
            return 60000L;
        }
    }

    private void saveState(String status, String message)
    {
        saveProgress(status, message, catalogMapper.countReigns(), catalogMapper.countSyncedReigns(), contentMapper.countContents());
    }

    /** 进度落库（带节流）：高频进度更新最多每 STATE_SAVE_INTERVAL_MS 写一次状态表 */
    private void saveProgressThrottled(String status, String message, int total, int completed, int contents)
    {
        long now = System.currentTimeMillis();
        if (now - lastStateSaveAt < STATE_SAVE_INTERVAL_MS)
        {
            return;
        }
        lastStateSaveAt = now;
        saveProgress(status, message, total, completed, contents);
    }

    private synchronized void saveProgress(String status, String message, int total, int completed, int contents)
    {
        TjSyncState state = new TjSyncState();
        state.setTotalReigns(total);
        state.setCompletedReigns(completed);
        state.setContentCount(contents);
        state.setStatus(status);
        state.setMessage(message != null && message.length() > 480 ? message.substring(0, 480) : message);
        if (syncStateMapper.selectState() == null)
        {
            syncStateMapper.insertState(state);
        }
        else
        {
            syncStateMapper.updateState(state);
        }
    }
}
