package io.github.mangi.clock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户自建语料的存储。全部批次放在一个文件里，写入走「临时文件 → fsync → 备份旧文件 → 改名」，
 * 读坏了先回退 .bak，再坏了当空（但绝不因此覆盖原文件）。
 *
 * 文件格式：{"schema_version":3,"batches":[{"id","at","name","entries":[...]}]}
 * 条目格式与内置语料一致：s / b / h / time / tier / scope / seg
 */
public class CorpusStore {

    public static final String FILE_NAME = "user_corpus.json";
    public static final int SCHEMA_VERSION = 3;
    /** 用户语料总条数上限。内置语料 4937 条不算在内，这里是「自己导入的」配额 */
    public static final int MAX_TOTAL = 5000;
    /** 单批上限。原为 500，导致「导出上千条再导入」被拒；放到 1000 保证导出文件可回导 */
    public static final int MAX_BATCH = 1000;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final String MANUAL_ID = "manual";
    public static final String MANUAL_NAME = "手动添加";

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    // ────────────────────────── 数据类 ──────────────────────────

    public static class Entry {
        public String s = "";
        public String b = "";
        public String h = "";
        public String time = "";
        public String scope = "";
        public int tier;
        public int[] seg;

        public Entry() {
        }

        public Entry(String s, String b, String h, String time, int tier, String scope, int[] seg) {
            this.s = s;
            this.b = b;
            this.h = h;
            this.time = time;
            this.tier = tier;
            this.scope = scope;
            this.seg = seg;
        }
    }

    public static class Batch {
        public String id = "";
        public String name = "";
        public long at;
        public List<Entry> entries = new ArrayList<>();

        public int count() {
            return entries.size();
        }
    }

    /** 一次读写的结果：给界面显示回执用 */
    public static class Result {
        public boolean ok = true;
        public int imported;      // 成功写入条数
        public int skippedDup;    // 因重复跳过
        public int skippedBad;    // 因格式不合格丢弃
        public final List<String> problems = new ArrayList<>();   // 前几条可读原因

        public void problem(String message) {
            skippedBad++;
            if (problems.size() < 5) {
                problems.add(message);
            }
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("导入 ").append(imported).append(" 条");
            if (skippedDup > 0) {
                sb.append("，重复跳过 ").append(skippedDup).append(" 条");
            }
            if (skippedBad > 0) {
                sb.append("，不合格 ").append(skippedBad).append(" 条");
            }
            return sb.toString();
        }
    }

    /** 每次操作都是 new CorpusStore(...)，实例锁没用，必须是类级静态锁 */
    private static final Object LOCK = new Object();

    private boolean corrupted = false;
    private boolean correctedFromBackup = false;

    private final File dir;

    public CorpusStore(File filesDir) {
        this.dir = filesDir;
    }

    public File file() {
        return new File(dir, FILE_NAME);
    }

    private File backupFile() {
        return new File(dir, FILE_NAME + ".bak");
    }

    private File tempFile() {
        return new File(dir, FILE_NAME + ".tmp");
    }

    /**
     * 解析导入文件。返回 null 表示整份不可用，reason 里写给人看的原因。
     * 支持两种形态：{"schema_version":3,"entries":[...]} 和 AI 直出的纯数组。
     * 这里只做「能不能解析」，字段校验留给 importEntries（那里才有准确的行号）。
     */
    public static List<Entry> parseImport(byte[] data, StringBuilder reason) {
        if (reason == null) {
            reason = new StringBuilder();
        }
        if (data == null || data.length == 0) {
            reason.append("文件是空的");
            return null;
        }
        if (data.length > MAX_FILE_BYTES) {
            reason.append("文件超过 ").append(MAX_FILE_BYTES / 1024 / 1024).append(" MB");
            return null;
        }
        try {
            String text = new String(data, StandardCharsets.UTF_8).trim();
            JSONArray array;
            if (text.startsWith("[")) {
                array = new JSONArray(text);
            } else if (text.startsWith("{")) {
                JSONObject root = new JSONObject(text);
                int version = root.optInt("schema_version", -1);
                if (version != SCHEMA_VERSION) {
                    reason.append("schema_version 应为 ").append(SCHEMA_VERSION)
                            .append("，实际是 ").append(version);
                    return null;
                }
                array = root.optJSONArray("entries");
                if (array == null) {
                    reason.append("缺少 entries 字段");
                    return null;
                }
            } else {
                reason.append("不是合法的 JSON");
                return null;
            }
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Entry entry = toEntry(array.optJSONObject(i));
                if (entry != null) {
                    out.add(entry);
                }
            }
            if (out.isEmpty()) {
                reason.append("文件里没有可用的句子");
                return null;
            }
            return out;
        } catch (Throwable throwable) {
            reason.append("解析失败：").append(throwable.getClass().getSimpleName());
            return null;
        }
    }

    // ────────────────────────── 读 ──────────────────────────

    /** 读全部批次。主文件坏了读 .bak；都坏了返回空表，但会在 Result 里留下标记。 */
    public List<Batch> load() {
        synchronized (LOCK) {
            corrupted = false;
            File primary = file();
            List<Batch> batches = parseFile(primary, true);
            if (batches != null) {
                return batches;
            }
            if (corrupted && primary.exists()) {
                // 主文件在、但解析不了：改名保留证据，别让后面的写入把它覆盖掉
                File broken = new File(dir, FILE_NAME + ".corrupt");
                if (broken.exists() && !broken.delete()) {
                    // 删不掉旧的也不阻塞，最多覆盖不了
                }
                if (!primary.renameTo(broken)) {
                    // 改名失败就留在原地，但 corrupted 标记仍然有效，写入会被拦住
                }
            }
            List<Batch> backup = parseFile(backupFile(), false);
            if (backup != null) {
                correctedFromBackup = !corrupted;
                return backup;
            }
            return new ArrayList<>();
        }
    }

    /** 上一次 load 是否发生过「文件损坏」（主文件读不出来） */
    public boolean lastLoadCorrupted() {
        return corrupted;
    }

    /** 文件不存在或都读不出来时返回 null（区分「空表」和「坏了」） */
    private List<Batch> parseFile(File source, boolean isPrimary) {
        if (source == null || !source.exists() || source.length() == 0) {
            return null;
        }
        try {
            InputStream in = new FileInputStream(source);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            in.close();
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(text);
            JSONArray array = root.optJSONArray("batches");
            if (array == null) {
                return null;
            }
            List<Batch> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                Batch batch = new Batch();
                batch.id = item.optString("id", "");
                batch.name = item.optString("name", "");
                batch.at = item.optLong("at", 0L);
                JSONArray entries = item.optJSONArray("entries");
                if (entries != null) {
                    for (int k = 0; k < entries.length(); k++) {
                        Entry entry = toEntry(entries.optJSONObject(k));
                        if (entry != null) {
                            batch.entries.add(entry);
                        }
                    }
                }
                if (batch.id.length() > 0) {
                    list.add(batch);
                }
            }
            return list;
        } catch (Throwable ignored) {
            if (isPrimary) {
                corrupted = true;
            }
            return null;
        }
    }

    private static Entry toEntry(JSONObject object) {
        if (object == null) {
            return null;
        }
        String s = object.optString("s", "");
        if (s.length() == 0) {
            return null;
        }
        Entry entry = new Entry();
        entry.s = s;
        entry.b = object.optString("b", "");
        entry.h = object.optString("h", "");
        entry.time = object.optString("time", "");
        entry.scope = object.optString("scope", "");
        entry.tier = object.optInt("tier", 0);
        JSONArray seg = object.optJSONArray("seg");
        if (seg != null && seg.length() > 0) {
            entry.seg = new int[seg.length()];
            for (int i = 0; i < seg.length(); i++) {
                entry.seg[i] = normalizeHour(seg.opt(i));
            }
        }
        return entry;
    }

    /** seg 元素兼容 "3" / 3 / "03"，统一成 0–23 的整数；非法值返回 -1 由调用方过滤 */
    private static int normalizeHour(Object raw) {
        if (raw == null) {
            return -1;
        }
        String text = String.valueOf(raw).trim();
        if (text.length() == 0) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(text);
            if (hour < 0 || hour > 23) {
                return -1;
            }
            return hour;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ────────────────────────── 写 ──────────────────────────

    /**
     * 先写 .tmp、flush + fsync，再把现有文件改名成 .bak，最后把 .tmp 改名成正式文件。
     * 任何一步失败都保持原文件不动。
     */
    private boolean save(List<Batch> batches) {
        synchronized (LOCK) {
            return doSave(batches);
        }
    }


    private boolean doSave(List<Batch> batches) {
        File target = file();
        File temp = tempFile();
        FileOutputStream out = null;
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            for (Batch batch : batches) {
                JSONObject item = new JSONObject();
                item.put("id", batch.id);
                item.put("name", batch.name);
                item.put("at", batch.at);
                JSONArray entries = new JSONArray();
                for (Entry entry : batch.entries) {
                    entries.put(fromEntry(entry));
                }
                item.put("entries", entries);
                array.put(item);
            }
            root.put("batches", array);

            out = new FileOutputStream(temp);
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;

            if (target.exists()) {
                File backup = backupFile();
                if (backup.exists() && !backup.delete()) {
                    // 备份删不掉也不阻塞写入，最多留着上一版
                }
                if (!target.renameTo(backup)) {
                    return false;
                }
            }
            if (!temp.renameTo(target)) {
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (temp.exists() && !target.exists()) {
                // 没写成就留着下次覆盖，不删也不影响
            }
        }
    }

    private JSONObject fromEntry(Entry entry) throws Exception {
        JSONObject object = new JSONObject();
        object.put("s", entry.s);
        object.put("b", entry.b);
        object.put("h", entry.h == null ? "" : entry.h);
        object.put("time", entry.time == null ? "" : entry.time);
        object.put("tier", entry.tier);
        object.put("scope", entry.scope == null ? "" : entry.scope);
        if (entry.seg != null && entry.seg.length > 0) {
            JSONArray seg = new JSONArray();
            for (int hour : entry.seg) {
                if (hour >= 0 && hour <= 23) {
                    seg.put(String.valueOf(hour));
                }
            }
            object.put("seg", seg);
        } else {
            object.put("seg", new JSONArray());
        }
        return object;
    }

    // ────────────────────────── 校验 ──────────────────────────

    /** 返回 null 表示合格，否则是可读的拒绝原因 */
    public String validate(Entry entry) {
        if (entry == null) {
            return "空条目";
        }
        String s = entry.s == null ? "" : entry.s.trim();
        if (s.length() == 0) {
            return "句子为空";
        }
        if (s.length() > 200) {
            return "句子超过 200 字";
        }
        if (entry.b == null || entry.b.trim().length() == 0) {
            return "缺出处";
        }
        if (entry.tier == 1) {
            if (!"minute".equals(entry.scope)) {
                return "精准句 scope 必须是 minute";
            }
            String time = entry.time == null ? "" : entry.time.trim();
            if (!TIME_PATTERN.matcher(time).matches()) {
                return "time 必须是 HH:MM（现在是「" + time + "」）";
            }
        } else if (entry.tier == 2) {
            if (!"segment".equals(entry.scope) && !"wide".equals(entry.scope)) {
                return "写意句 scope 必须是 segment 或 wide";
            }
            boolean hasSeg = false;
            if (entry.seg != null) {
                for (int hour : entry.seg) {
                    if (hour >= 0 && hour <= 23) {
                        hasSeg = true;
                        break;
                    }
                }
            }
            if (!hasSeg) {
                return "写意句必须有 seg";
            }
        } else {
            return "tier 只能是 1 或 2";
        }
        return null;
    }

    /** 归一化：只留字，用于去重比较（标点、空白、全半角差异不算新句） */
    public static String normalize(String sentence) {
        if (sentence == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(sentence.length());
        for (int i = 0; i < sentence.length(); i++) {
            char c = sentence.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    // ────────────────────────── 业务操作 ──────────────────────────

    /**
     * 批量导入。known 是内置语料的句子集合（已归一化），用于跨库去重。
     */
        public Result importEntries(List<Entry> incoming, String name, Set<String> known) {
        synchronized (LOCK) {
            return doImportEntries(incoming, name, known);
        }
    }

private Result doImportEntries(List<Entry> incoming, String name, Set<String> known) {
        Result result = new Result();
        if (incoming == null || incoming.isEmpty()) {
            result.ok = false;
            result.problem("文件里没有句子");
            return result;
        }
        List<Batch> batches = load();
        int current = totalCount(batches);
        if (current >= MAX_TOTAL) {
            result.ok = false;
            result.problem("用户语料已经满 " + MAX_TOTAL + " 条");
            return result;
        }

        Set<String> seen = new HashSet<>();
        if (known != null) {
            seen.addAll(known);
        }
        for (Batch batch : batches) {
            for (Entry entry : batch.entries) {
                seen.add(normalize(entry.s));
            }
        }

        String baseName = (name == null || name.trim().length() == 0) ? "导入" : name.trim();
        long stamp = System.currentTimeMillis() / 1000L;
        List<Batch> added = new ArrayList<>();
        Batch target = null;
        int seq = 0;

        for (Entry entry : incoming) {
            if (current + result.imported >= MAX_TOTAL) {
                result.problem("达到 " + MAX_TOTAL + " 条上限，后面的没导入");
                break;
            }
            String error = validate(entry);
            if (error != null) {
                result.problem("第 " + (result.skippedBad + result.skippedDup + result.imported + 1) + " 条：" + error);
                continue;
            }
            String key = normalize(entry.s);
            if (!seen.add(key)) {
                result.skippedDup++;
                continue;
            }
            // 超过单批上限就另起一批，而不是整份拒绝——导出上万条的文件也要能回导
            if (target == null || target.entries.size() >= MAX_BATCH) {
                seq++;
                target = new Batch();
                target.id = "b" + System.currentTimeMillis() + "-" + seq;
                target.name = seq == 1 ? baseName : baseName + " " + seq;
                target.at = stamp;
                added.add(target);
            }
            entry.s = entry.s.trim();
            entry.b = entry.b.trim();
            target.entries.add(entry);
            result.imported++;
        }

        if (added.isEmpty()) {
            result.ok = false;
            if (result.problems.isEmpty()) {
                // 直接放消息，不走 problem()——那是「不合格」计数，这里是「全重复」
                result.problems.add("全部重复，没有新句子");
            }
            return result;
        }
        batches.addAll(added);
        if (!save(batches)) {
            result.ok = false;
            result.problem("写入失败，原数据未改动");
            return result;
        }
        return result;
    }

    /** 手动加一条：归到固定的「手动添加」批次里 */
        public Result addManual(Entry entry, Set<String> known) {
        synchronized (LOCK) {
            return doAddManual(entry, known);
        }
    }

private Result doAddManual(Entry entry, Set<String> known) {
        Result result = new Result();
        String error = validate(entry);
        if (error != null) {
            result.ok = false;
            result.problem(error);
            return result;
        }
        List<Batch> batches = load();
        int current = totalCount(batches);
        if (current >= MAX_TOTAL) {
            result.ok = false;
            result.problem("用户语料已经满 " + MAX_TOTAL + " 条");
            return result;
        }
        Set<String> seen = new HashSet<>();
        if (known != null) {
            seen.addAll(known);
        }
        Batch manual = null;
        for (Batch batch : batches) {
            for (Entry item : batch.entries) {
                seen.add(normalize(item.s));
            }
            if (MANUAL_ID.equals(batch.id)) {
                manual = batch;
            }
        }
        String key = normalize(entry.s);
        if (!seen.add(key)) {
            result.ok = false;
            result.problem("这句已经在语料里了");
            return result;
        }
        if (manual == null) {
            manual = new Batch();
            manual.id = MANUAL_ID;
            manual.name = MANUAL_NAME;
            manual.at = System.currentTimeMillis() / 1000L;
            batches.add(0, manual);
        }
        entry.s = entry.s.trim();
        entry.b = entry.b.trim();
        manual.entries.add(entry);
        if (!save(batches)) {
            result.ok = false;
            result.problem("写入失败，原数据未改动");
            return result;
        }
        result.imported = 1;
        return result;
    }

        public boolean deleteBatch(String id) {
        synchronized (LOCK) {
            return doDeleteBatch(id);
        }
    }

private boolean doDeleteBatch(String id) {
        if (id == null || id.length() == 0) {
            return false;
        }
        List<Batch> batches = load();
        boolean removed = false;
        for (int i = batches.size() - 1; i >= 0; i--) {
            if (id.equals(batches.get(i).id)) {
                batches.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            return false;
        }
        if (batches.isEmpty()) {
            // 全删完了就留一个空 shell，免得文件消失后 load() 又要走回退逻辑
            return save(batches);
        }
        return save(batches);
    }

        public boolean deleteEntry(String batchId, String sentence) {
        synchronized (LOCK) {
            return doDeleteEntry(batchId, sentence);
        }
    }

private boolean doDeleteEntry(String batchId, String sentence) {
        if (batchId == null || sentence == null) {
            return false;
        }
        List<Batch> batches = load();
        boolean removed = false;
        for (int b = batches.size() - 1; b >= 0; b--) {
            Batch batch = batches.get(b);
            if (!batchId.equals(batch.id)) {
                continue;
            }
            for (int i = batch.entries.size() - 1; i >= 0; i--) {
                if (sentence.equals(batch.entries.get(i).s)) {
                    batch.entries.remove(i);
                    removed = true;
                }
            }
            // 删空的批次不留壳子，否则界面上永远挂一行「0 句」
            if (batch.entries.isEmpty()) {
                batches.remove(b);
            }
        }
        if (!removed) {
            return false;
        }
        return save(batches);
    }

    public int totalCount(List<Batch> batches) {
        int total = 0;
        if (batches != null) {
            for (Batch batch : batches) {
                total += batch.entries.size();
            }
        }
        return total;
    }

    /** 全部批次摊平导出；不保留批次边界，口径是「句子集合与字段值一致」 */
    public boolean export(OutputStream output) {
        List<Batch> batches = load();
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            for (Batch batch : batches) {
                for (Entry entry : batch.entries) {
                    array.put(fromEntry(entry));
                }
            }
            root.put("entries", array);
            output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
