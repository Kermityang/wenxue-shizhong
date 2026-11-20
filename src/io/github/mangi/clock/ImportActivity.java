package io.github.mangi.clock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImportActivity extends Activity {
    private static final float FS_DISPLAY = 64f;
    private static final float FS_SECTION = 34f;
    private static final float FS_BODY = 30f;
    private static final float FS_SUB = 24f;
    private static final float FS_MICRO = 22f;

    private static final String[][] SEGMENTS = {
            {"凌晨", "0", "1", "2", "3", "4", "5"},
            {"清晨", "4", "5", "6", "7", "8"},
            {"上午", "8", "9", "10"},
            {"晌午", "11", "12", "13"},
            {"午后", "12", "13", "14", "15", "16"},
            {"傍晚", "17", "18", "19"},
            {"夜里", "19", "20", "21", "22", "23", "0", "1", "2", "3", "4", "5"},
            {"一更", "19", "20"},
            {"二更", "21", "22"},
            {"三更", "23", "0", "1"},
            {"四更", "1", "2", "3"},
            {"五更", "3", "4", "5"}
    };

    private boolean dark = true;
    private int backgroundColor;
    private int titleColor;
    private int bodyColor;
    private int hintColor;
    private int strokeOffColor;

    private EditText sentenceInput;
    private LinearLayout batchList;
    private TextView batchSummary;
    private EditText authorInput;
    private EditText titleInput;
    private TextView recognitionView;
    private Button addButton;
    private Button preciseChooseButton;
    private Button imageryChooseButton;
    private Button exportButton;
    private boolean importInProgress;
    private TimeParse.Result parsed;
    private boolean waitingForSegment;
    private CorpusStore.Entry pendingEntry;
    private final Set<String> knownSet = new HashSet<>();

    private static final int REQ_OPEN_PRECISE = 1001;
    private static final int REQ_OPEN_IMAGERY = 1002;
    private static final int REQ_SAVE_EXPORT = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = getSharedPreferences(SettingsActivity.PREFERENCES, MODE_PRIVATE)
                .getBoolean("dark", true);
        applyTheme();
        getWindow().setStatusBarColor(backgroundColor);
        if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        loadKnownSet();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor);
        root.setPadding(72, 48, 72, 32);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = textView("\u2039", 52, titleColor);
        back.setContentDescription("\u8fd4\u56de");
        back.setPadding(0, 0, 28, 0);
        back.setMinWidth(120);
        back.setMinHeight(140);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    finish();
                } catch (Throwable ignored) {
                }
            }
        });
        bar.addView(back);
        TextView title = textView("\u5bfc\u5165\u8bed\u6599", 48, titleColor);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        addSpace(root, 14);
        root.addView(textView("看中哪句，随手记在这里。", FS_MICRO, hintColor));
        addSpace(root, 48);

        root.addView(sectionTitle("手动添加"));
        addSpace(root, 20);
        sentenceInput = createInput("把句子粘在这里", true);
        root.addView(sentenceInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 190));
        addSpace(root, 16);
        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        authorInput = createInput("作者，可留空", false);
        sourceRow.addView(authorInput, new LinearLayout.LayoutParams(0, 92, 1f));
        View sourceGap = new View(this);
        sourceRow.addView(sourceGap, new LinearLayout.LayoutParams(24, 92));
        titleInput = createInput("书名，不用加《》", false);
        sourceRow.addView(titleInput, new LinearLayout.LayoutParams(0, 92, 1f));
        root.addView(sourceRow);
        addSpace(root, 18);
        recognitionView = textView("", FS_MICRO, hintColor);
        root.addView(recognitionView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 20);
        addButton = outlineButton("添加到语料");
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onAddClicked();
            }
        });
        root.addView(addButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 82));
        addSpace(root, 52);

        addImportShell(root, "批量导入 · 精准句",
                "写明了具体钟点，可整理成 JSON 后一次导入。", REQ_OPEN_PRECISE);
        addSpace(root, 44);
        addImportShell(root, "批量导入 · 写意句",
                "只写了时辰或天光，可整理成 JSON 后一次导入。", REQ_OPEN_IMAGERY);
        addSpace(root, 52);

        LinearLayout batchHeader = new LinearLayout(this);
        batchHeader.setOrientation(LinearLayout.HORIZONTAL);
        batchHeader.setGravity(Gravity.CENTER_VERTICAL);
        batchHeader.addView(sectionTitle("已导入"), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        batchSummary = textView("", FS_SUB, hintColor);
        batchHeader.addView(batchSummary);
        root.addView(batchHeader);
        addSpace(root, 16);
        batchList = new LinearLayout(this);
        batchList.setOrientation(LinearLayout.VERTICAL);
        root.addView(batchList);
        addSpace(root, 24);
        exportButton = outlineButton("导出全部");
        exportButton.setEnabled(false);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startExport();
            }
        });
        root.addView(exportButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 82));

        sentenceInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                updateRecognition();
            }
        });
        updateRecognition();

        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void applyTheme() {
        if (dark) {
            backgroundColor = Color.rgb(17, 17, 20);
            titleColor = Color.rgb(238, 233, 224);
            bodyColor = Color.rgb(198, 192, 180);
            hintColor = Color.rgb(126, 121, 112);
            strokeOffColor = Color.rgb(92, 88, 81);
        } else {
            backgroundColor = Color.rgb(245, 242, 234);
            titleColor = Color.rgb(30, 28, 25);
            bodyColor = Color.rgb(75, 71, 64);
            hintColor = Color.rgb(141, 134, 121);
            strokeOffColor = Color.rgb(189, 182, 168);
        }
    }

    /** 「\u2039」只有字形那么宽，手指点着费劲：用 TouchDelegate 把热区撑到约 120x144，外观完全不动 */
    private void enlargeTouch(final View view, final int extra) {
        final View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        if (parent == null) {
            return;
        }
        parent.post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.graphics.Rect rect = new android.graphics.Rect();
                    view.getHitRect(rect);
                    if (rect.width() <= 0 || rect.height() <= 0) {
                        return;
                    }
                    rect.inset(-extra, -extra);
                    parent.setTouchDelegate(new android.view.TouchDelegate(rect, view));
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private TextView sectionTitle(String text) {
        return textView(text, FS_SECTION, titleColor);
    }

    private TextView textView(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        view.setTextColor(color);
        return view;
    }

    private EditText createInput(String hint, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(hintColor);
        input.setTextColor(bodyColor);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, FS_BODY);
        input.setBackground(outline(strokeOffColor));
        input.setPadding(24, 18, 24, 18);
        if (multiline) {
            input.setSingleLine(false);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            input.setSingleLine(true);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
        return input;
    }

    private GradientDrawable outline(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(6);
        background.setStroke(2, color);
        background.setColor(Color.TRANSPARENT);
        return background;
    }

    private Button outlineButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, FS_BODY);
        button.setTextColor(titleColor);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setBackground(outline(titleColor));
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void addImportShell(LinearLayout root, String title, String description,
                                final int requestCode) {
        root.addView(sectionTitle(title));
        addSpace(root, 12);
        root.addView(textView(description, FS_MICRO, hintColor));
        addSpace(root, 20);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = outlineButton("复制 AI 提示词");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyPrompt(requestCode == REQ_OPEN_IMAGERY
                        ? "prompt_imagery.txt" : "prompt_precise.txt");
            }
        });
        buttons.addView(copy, new LinearLayout.LayoutParams(
                0, 82, 1f));
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(16, 1));
        Button choose = outlineButton("选择文件");
        if (requestCode == REQ_OPEN_PRECISE) {
            preciseChooseButton = choose;
        } else if (requestCode == REQ_OPEN_IMAGERY) {
            imageryChooseButton = choose;
        }
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startImport(requestCode);
            }
        });
        buttons.addView(choose, new LinearLayout.LayoutParams(
                0, 82, 1f));
        root.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 把 assets 里的提示词整段复制到剪贴板 */
    private void copyPrompt(String assetName) {
        String text = readAssetText(assetName);
        if (text == null || text.trim().length() == 0) {
            Toast.makeText(this, "提示词读取失败", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
                return;
            }
            cm.setPrimaryClip(android.content.ClipData.newPlainText("prompt", text));
            Toast.makeText(this, "已复制，粘贴给任意 AI 即可", Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String readAssetText(String name) {
        java.io.InputStream input = null;
        try {
            input = getAssets().open(name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable throwable) {
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

    private void startImport(int requestCode) {
        if (importInProgress) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // 只用 */*：实测加 EXTRA_MIME_TYPES 反而把 .json 全过滤掉了
        // （这台机器的 DocumentsProvider 给 .json 的 MIME 不在白名单内），
        // 宁可让用户看到所有文件、选错时由解析器报「不是合法的 JSON」
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        intent.putExtra(Intent.EXTRA_TITLE, "clock-corpus-" + stamp + ".json");
        startActivityForResult(intent, REQ_SAVE_EXPORT);
    }

    private void setImportInProgress(boolean inProgress) {
        importInProgress = inProgress;
        if (preciseChooseButton != null) {
            preciseChooseButton.setEnabled(!inProgress);
        }
        if (imageryChooseButton != null) {
            imageryChooseButton.setEnabled(!inProgress);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN_PRECISE && requestCode != REQ_OPEN_IMAGERY
                && requestCode != REQ_SAVE_EXPORT) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        if (requestCode == REQ_SAVE_EXPORT) {
            CorpusImporter.exportDocument(getApplicationContext(), uri,
                    new CorpusImporter.ExportCallback() {
                        @Override
                        public void onComplete(CorpusImporter.ExportResult result) {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Toast.makeText(ImportActivity.this,
                                    result.ok ? "已导出 " + result.count + " 句" : "导出失败",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
            return;
        }

        int tier = requestCode == REQ_OPEN_PRECISE ? 1 : 2;
        setImportInProgress(true);
        CorpusImporter.importDocument(getApplicationContext(), uri, tier, knownSet,
                new CorpusImporter.ImportCallback() {
                    @Override
                    public void onComplete(CorpusStore.Result result) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setImportInProgress(false);
                        String message;
                        if (result.ok) {
                            // 成功（可能部分成功）：说清楚进了几条，有问题再补一句
                            message = result.summary();
                            if (!result.problems.isEmpty()) {
                                message += "\n" + result.problems.get(0);
                            }
                        } else {
                            // 整体失败：只讲原因，不叠「导入 0 条，重复跳过 N 条」
                            message = result.problems.isEmpty() ? "导入失败" : result.problems.get(0);
                        }
                        Toast.makeText(ImportActivity.this, message, Toast.LENGTH_LONG).show();
                        if (result.ok) {
                            refreshBatches();
                        }
                    }
                });
    }

    private void updateRecognition() {
        parsed = TimeParse.parse(sentenceInput.getText().toString());
        pendingEntry = null;
        if (parsed != null && parsed.time != null && parsed.time.length() > 0) {
            waitingForSegment = false;
            recognitionView.setText("识别到「" + safe(parsed.h) + "」→ 落在 " + parsed.time);
        } else if (parsed != null && parsed.seg != null && !parsed.seg.isEmpty()) {
            waitingForSegment = false;
            recognitionView.setText("识别到「" + safe(parsed.h) + "」→ 落在 "
                    + joinHours(parsed.seg));
        } else {
            waitingForSegment = true;
            recognitionView.setText("这句里没找到时间词，添加时需要选一个时段");
        }
    }

    private String joinHours(List<String> hours) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < hours.size(); i++) {
            if (i > 0) {
                text.append("、");
            }
            text.append(hours.get(i)).append(" 点");
        }
        return text.toString();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    /** 重新读一遍用户语料，重画批次列表 */
    private void refreshBatches() {
        if (batchList == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<CorpusStore.Batch> loaded;
                try {
                    loaded = new CorpusStore(getFilesDir()).load();
                } catch (Throwable throwable) {
                    loaded = null;
                }
                final List<CorpusStore.Batch> batches = loaded;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed() || batchList == null) {
                            return;
                        }
                        showBatches(batches);
                    }
                });
            }
        }, "corpus-list").start();
    }

    private void showBatches(List<CorpusStore.Batch> batches) {
        batchList.removeAllViews();
        int total = 0;
        int nonEmpty = 0;
        if (batches != null) {
            for (CorpusStore.Batch batch : batches) {
                total += batch.entries.size();
                // 汇总里的「N 批」只算有句子的批次：空的「手动添加」会在列表里
                // 显示成 0 句，但不该让批数变成 2
                if (!batch.entries.isEmpty()) {
                    nonEmpty++;
                }
            }
        }
        if (exportButton != null) {
            exportButton.setEnabled(total > 0);
        }
        if (batchSummary != null) {
            // 总数为 0 时不写「1 批 · 0 句」，那看着像还有东西
            batchSummary.setText(total == 0 ? "" : nonEmpty + " 批 · " + total + " 句");
        }
        boolean anyVisible = false;
        if (batches != null) {
            for (CorpusStore.Batch batch : batches) {
                if (visibleBatch(batch)) {
                    anyVisible = true;
                    break;
                }
            }
        }
        if (!anyVisible) {
            batchList.addView(textView("还没有导入过句子", FS_MICRO, hintColor));
            return;
        }
        for (final CorpusStore.Batch batch : batches) {
            boolean manual = CorpusStore.MANUAL_ID.equals(batch.id);
            if (!manual && batch.entries.isEmpty()) {
                // 批量导入留下的空批次没有意义（多半是历史遗留），不显示；
                // 「手动添加」的空批次要留着，那是固定的记录位置
                continue;
            }

            // 批次标题行：手动添加写名字，批量导入写「名字 · 日期时间」
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 26, 0, 26);
            String label = batch.name.length() == 0 ? "导入" : batch.name;
            if (!manual) {
                label = label + " · " + formatStamp(batch.at);
            }
            row.addView(textView(label, FS_BODY, bodyColor),
                    new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(textView(batch.entries.size() + " 句", FS_SUB, hintColor));
            if (!manual) {
                row.addView(makeRemoveView(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        confirmDeleteBatch(batch);
                    }
                }));
            }
            batchList.addView(row);

            // 手动添加的句子逐条列出原文，长句截断；删除按单条
            if (manual) {
                for (final CorpusStore.Entry entry : batch.entries) {
                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(LinearLayout.HORIZONTAL);
                    item.setGravity(Gravity.CENTER_VERTICAL);
                    item.setPadding(24, 14, 0, 14);
                    item.addView(textView(clipSentence(entry.s), FS_SUB, hintColor),
                            new LinearLayout.LayoutParams(0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    item.addView(makeRemoveView(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            confirmDeleteEntry(entry);
                        }
                    }));
                    batchList.addView(item);
                }
            }

            View line = new View(this);
            line.setBackgroundColor(strokeOffColor);
            batchList.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    /** 统一的删除热区：字形 24 号，点击区 96x96 */
    private TextView makeRemoveView(View.OnClickListener listener) {
        TextView remove = textView("\u00d7", FS_SUB, hintColor);
        remove.setGravity(Gravity.CENTER);
        remove.setMinWidth(96);
        remove.setMinHeight(96);
        remove.setPadding(0, 0, 0, 0);
        remove.setOnClickListener(listener);
        return remove;
    }

    /** 列表里只显示前 16 个字，避免长句把整行撑爆 */
    private String clipSentence(String sentence) {
        if (sentence == null) {
            return "";
        }
        String flat = sentence.replace("\n", " ");
        if (flat.length() <= 16) {
            return flat;
        }
        return flat.substring(0, 16) + "\u2026";
    }

    private void confirmDeleteEntry(final CorpusStore.Entry entry) {
        new AlertDialog.Builder(this)
                .setMessage("\u5220\u9664\u8fd9\u4e00\u53e5\uff1f")
                .setPositiveButton("\u5220\u9664", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            new CorpusStore(getFilesDir()).deleteEntry(CorpusStore.MANUAL_ID, entry.s);
                        } catch (Throwable ignored) {
                        }
                        refreshBatches();
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    /** 「手动添加」批次永远显示（哪怕是 0 句）；其他批次空了就不显示 */
    private boolean visibleBatch(CorpusStore.Batch batch) {
        return CorpusStore.MANUAL_ID.equals(batch.id) || !batch.entries.isEmpty();
    }

    private String formatStamp(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        return new java.text.SimpleDateFormat("M\u6708d\u65e5 HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date(seconds * 1000L));
    }

    private void confirmDeleteBatch(final CorpusStore.Batch batch) {
        new AlertDialog.Builder(this)
                .setMessage("\u5220\u9664\u8fd9\u4e00\u6279\uff1f\u5171 "
                        + batch.entries.size() + " \u53e5")
                .setPositiveButton("\u5220\u9664", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            new CorpusStore(getFilesDir()).deleteBatch(batch.id);
                        } catch (Throwable ignored) {
                        }
                        refreshBatches();
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBatches();
    }

    /** 作者可空；书名自动补《》，拼成「作者　《书名》」 */
    private String composeSource() {
        String author = authorInput.getText().toString().trim();
        String title = titleInput.getText().toString().trim();
        if (title.length() == 0) {
            return "";
        }
        if (!title.startsWith("《")) {
            title = "《" + title + "》";
        }
        if (author.length() == 0) {
            return title;
        }
        return author + "　" + title;
    }

    private void onAddClicked() {
        String sentence = sentenceInput.getText().toString().trim();
        String source = composeSource();
        if (sentence.length() == 0) {
            Toast.makeText(this, "还没填句子", Toast.LENGTH_SHORT).show();
            return;
        }
        if (source.length() == 0) {
            Toast.makeText(this, "书名要填", Toast.LENGTH_SHORT).show();
            return;
        }

        TimeParse.Result result = TimeParse.parse(sentence);
        CorpusStore.Entry entry = entryFrom(sentence, source, result);
        if (entry != null) {
            addManual(entry);
            return;
        }
        if (waitingForSegment) {
            pendingEntry = new CorpusStore.Entry(
                    sentence, source, "", "", 2, "segment", null);
            showSegmentDialog();
        }
    }

    private CorpusStore.Entry entryFrom(String sentence, String source, TimeParse.Result result) {
        if (result != null && result.time != null && result.time.length() > 0) {
            return new CorpusStore.Entry(sentence, source, safe(result.h), result.time,
                    1, "minute", null);
        }
        if (result != null && result.seg != null && !result.seg.isEmpty()) {
            int[] hours = new int[result.seg.size()];
            for (int i = 0; i < result.seg.size(); i++) {
                hours[i] = Integer.parseInt(result.seg.get(i));
            }
            return new CorpusStore.Entry(sentence, source, safe(result.h), "",
                    2, "segment", hours);
        }
        return null;
    }

    private void showSegmentDialog() {
        String[] labels = new String[SEGMENTS.length];
        for (int i = 0; i < SEGMENTS.length; i++) {
            StringBuilder label = new StringBuilder(SEGMENTS[i][0]).append("（");
            for (int j = 1; j < SEGMENTS[i].length; j++) {
                if (j > 1) {
                    label.append("、");
                }
                label.append(SEGMENTS[i][j]).append(" 点");
            }
            labels[i] = label.append("）").toString();
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选一个时段")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (pendingEntry == null) {
                            return;
                        }
                        int[] hours = new int[SEGMENTS[which].length - 1];
                        for (int i = 1; i < SEGMENTS[which].length; i++) {
                            hours[i - 1] = Integer.parseInt(SEGMENTS[which][i]);
                        }
                        CorpusStore.Entry entry = pendingEntry;
                        pendingEntry = null;
                        entry.seg = hours;
                        addManual(entry);
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pendingEntry = null;
                    }
                })
                .create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                pendingEntry = null;
            }
        });
        dialog.show();
    }

    private void addManual(final CorpusStore.Entry entry) {
        addButton.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CorpusStore.Result result = new CorpusStore(getFilesDir())
                        .addManual(entry, knownSet);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        addButton.setEnabled(true);
                        if (result.ok && result.imported == 1) {
                            Toast.makeText(ImportActivity.this, "已添加到语料",
                                    Toast.LENGTH_SHORT).show();
                            sentenceInput.setText("");
                            authorInput.setText("");
                            titleInput.setText("");
                            refreshBatches();
                        } else {
                            String problem = result.problems.isEmpty()
                                    ? "添加失败" : result.problems.get(0);
                            Toast.makeText(ImportActivity.this, problem,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void loadKnownSet() {
        try (InputStream input = getAssets().open("clock.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            String text = new String(output.toByteArray(), "UTF-8");
            JSONArray array;
            if (text.trim().startsWith("{")) {
                array = new JSONObject(text).optJSONArray("entries");
            } else {
                array = new JSONArray(text);
            }
            if (array == null) {
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    knownSet.add(CorpusStore.normalize(item.optString("s", "")));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void addSpace(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, height));
    }
}
