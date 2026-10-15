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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 读通鉴-内容同步 服务层处理
 *
 * 迁移自 Dutongjian 项目的 tongjian_sync.py：
 * 目录 -> 纪年 -> 正文段落，限速请求、429 退避重试、按纪年 checkpoint 断点续传。
 * checkpoint 持久化在 TJ_REIGN.SYNCED 与 TJ_SYNC_STATE，重启后可继续。
 */
@Service
public class TjSyncServiceImpl implements ITjSyncService
{
    private static final Logger log = LoggerFactory.getLogger(TjSyncServiceImpl.class);

    /** 公开数据源（dutongjian.com 的公开只读接口，不登录、不绕权限） */
    private static final String BASE_URL = "https://www.dutongjian.com";

    /** 每次请求最小间隔（毫秒），与原项目一致的限速策略 */
    private static final long MIN_INTERVAL_MS = 5000L;

    private static final int MAX_RETRY = 3;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean stopRequested = false;

    private volatile long lastRequestAt = 0L;

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
        try
        {
            saveState("1", "正在获取目录…");
            JSONObject catalog = fetchJson("/api/table_of_contents");
            upsertCatalog(catalog);

            List<TjReign> all = catalogMapper.selectReignList();
            int total = all.size();
            for (TjReign reign : all)
            {
                if (stopRequested)
                {
                    saveState("2", "已手动停止，已完成 " + catalogMapper.countSyncedReigns() + "/" + total + " 个纪年");
                    return;
                }
                if ("1".equals(reign.getSynced()))
                {
                    continue;
                }
                try
                {
                    syncReign(reign);
                    saveState("1", "已同步 " + reign.getVolumeTitle() + " " + reign.getEmperorName() + reign.getReignName()
                            + "（" + catalogMapper.countSyncedReigns() + "/" + total + "）");
                }
                catch (Exception e)
                {
                    log.error("同步纪年失败: {}", reign.getTongjianId(), e);
                    saveState("2", "同步失败：" + e.getMessage() + "，已完成 " + catalogMapper.countSyncedReigns() + "/" + total + " 个纪年，可重新开始继续");
                    return;
                }
            }
            saveState("0", "全部完成，共 " + total + " 个纪年、" + contentMapper.countContents() + " 段正文");
        }
        catch (Exception e)
        {
            log.error("读通鉴同步异常", e);
            saveState("2", "同步异常：" + e.getMessage());
        }
        finally
        {
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

    /** 同步单个纪年的正文段落 */
    private void syncReign(TjReign reign) throws Exception
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
        if (!items.isEmpty())
        {
            contentMapper.deleteByReignId(reign.getReignId());
            contentMapper.insertContentBatch(items);
        }
        catalogMapper.markReignSynced(reign.getReignId());
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

    /** 限速 + 重试 + 429 退避的 JSON 请求 */
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
                        .header("User-Agent", "history-book/1.0 (+local sync)")
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
                    return json;
                }
                if (response.statusCode() == 429)
                {
                    long wait = parseRetryAfter(response.headers().firstValue("Retry-After").orElse(null));
                    log.warn("读通鉴同步触发限流，等待 {} 秒", wait / 1000);
                    Thread.sleep(wait);
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

    /** 保证两次请求之间至少间隔 MIN_INTERVAL_MS */
    private void pace() throws InterruptedException
    {
        long elapsed = System.currentTimeMillis() - lastRequestAt;
        if (elapsed < MIN_INTERVAL_MS)
        {
            Thread.sleep(MIN_INTERVAL_MS - elapsed);
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
        TjSyncState state = new TjSyncState();
        state.setTotalReigns(catalogMapper.countReigns());
        state.setCompletedReigns(catalogMapper.countSyncedReigns());
        state.setContentCount(contentMapper.countContents());
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
