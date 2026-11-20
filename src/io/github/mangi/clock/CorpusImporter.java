package io.github.mangi.clock;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CorpusImporter {
    interface ImportCallback {
        void onComplete(CorpusStore.Result result);
    }

    interface ExportCallback {
        void onComplete(ExportResult result);
    }

    static final class ExportResult {
        final boolean ok;
        final int count;

        ExportResult(boolean ok, int count) {
            this.ok = ok;
            this.count = count;
        }
    }

    private CorpusImporter() {
    }

    static void importDocument(Context context, final Uri uri, final int tier,
                               final Set<String> knownSet, final ImportCallback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CorpusStore.Result result = doImport(appContext, uri, tier, knownSet);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onComplete(result);
                    }
                });
            }
        }, "corpus-import").start();
    }

    static void exportDocument(Context context, final Uri uri, final ExportCallback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ExportResult result = doExport(appContext, uri);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onComplete(result);
                    }
                });
            }
        }, "corpus-export").start();
    }

    private static CorpusStore.Result doImport(Context context, Uri uri, int tier,
                                               Set<String> knownSet) {
        if (tier != 1 && tier != 2) {
            return failure("导入类型不明");
        }
        StringBuilder reason = new StringBuilder();
        byte[] data = readBytes(context, uri, reason);
        if (data == null) {
            return failure(reason.length() == 0 ? "读取失败" : reason.toString());
        }
        List<CorpusStore.Entry> parsed = CorpusStore.parseImport(data, reason);
        if (parsed == null) {
            return failure(reason.length() == 0 ? "解析失败" : reason.toString());
        }

        List<CorpusStore.Entry> entries = new ArrayList<>();
        List<String> tierProblems = new ArrayList<>();
        int mismatchCount = 0;
        for (int i = 0; i < parsed.size(); i++) {
            CorpusStore.Entry entry = parsed.get(i);
            if (entry.tier == tier) {
                entries.add(entry);
            } else {
                mismatchCount++;
            }
        }

        CorpusStore.Result result;
        if (entries.isEmpty()) {
            result = new CorpusStore.Result();
            result.ok = false;
        } else {
            String batchName = tier == 1 ? "导入 · 精准" : "导入 · 写意";
            result = new CorpusStore(context.getFilesDir())
                    .importEntries(entries, batchName, knownSet);
        }
        result.skippedBad += mismatchCount;
        if (mismatchCount > 0) {
            // 一条人话就够了：告诉用户该点哪个按钮，而不是逐条报「第 N 条 tier 应为 2」
            String want = tier == 1 ? "精准句" : "写意句";
            String got = tier == 1 ? "写意句" : "精准句";
            tierProblems.add("这个文件里是" + got + "，请用上面的「" + want + "」导入");
        }
        if (!tierProblems.isEmpty()) {
            result.problems.addAll(0, tierProblems);
            while (result.problems.size() > 5) {
                result.problems.remove(result.problems.size() - 1);
            }
        }
        return result;
    }

    private static byte[] readBytes(Context context, Uri uri, StringBuilder reason) {
        InputStream input = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            input = context.getApplicationContext().getContentResolver().openInputStream(uri);
            if (input == null) {
                reason.append("读取失败");
                return null;
            }
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > CorpusStore.MAX_FILE_BYTES) {
                    reason.append("文件太大");
                    return null;
                }
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        } catch (Throwable throwable) {
            reason.append("读取失败");
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static CorpusStore.Result failure(String message) {
        CorpusStore.Result result = new CorpusStore.Result();
        result.ok = false;
        // 整份文件的问题不是「某条不合格」，直接放消息，别让 skippedBad 变成 1
        result.problems.add(message);
        return result;
    }

    private static ExportResult doExport(Context context, Uri uri) {
        CorpusStore store = new CorpusStore(context.getFilesDir());
        int total = store.totalCount(store.load());
        OutputStream output = null;
        boolean success = false;
        try {
            output = context.getApplicationContext().getContentResolver()
                    .openOutputStream(uri, "wt");
            if (output != null) {
                success = store.export(output);
            }
        } catch (Throwable throwable) {
            success = false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable throwable) {
                    success = false;
                }
            }
        }
        return new ExportResult(success, total);
    }
}
