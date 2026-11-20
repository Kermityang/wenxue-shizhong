package io.github.mangi.clock;

import android.app.Activity;
import android.content.Intent;
import android.app.AlertDialog;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    static final String PREFERENCES = "clock";
    static final String[] BACKGROUND_KEYS = {"black", "blue", "brown", "moon", "ink"};
    private static final String[] BACKGROUND_NAMES = {"黑色", "蓝色", "棕色", "月色", "墨水屏"};
    static final int[] BACKGROUND_COLORS = {
            Color.rgb(0, 0, 0), Color.rgb(18, 38, 63), Color.rgb(51, 38, 26),
            Color.rgb(233, 229, 218), Color.WHITE
    };
    private static final int[] TIMEOUTS = {15, 30, 60, 0};
    private static final String[] TIMEOUT_NAMES = {"15 分钟", "30 分钟", "60 分钟", "常亮"};

    private SharedPreferences preferences;
    private TextView likedCount;
    private TextView blockedCount;
    private final GradientDrawable[] swatches = new GradientDrawable[BACKGROUND_KEYS.length];

    // 字号只有 5 档，页面上任何文字都从这五档里取
    private static final float FS_DISPLAY = 64f;   // 页面标题
    private static final float FS_SECTION = 34f;   // 区块标题
    private static final float FS_BODY = 30f;      // 行标签、档位名、按钮
    private static final float FS_SUB = 24f;       // 说明、右侧值、条数
    private static final float FS_MICRO = 22f;     // 副名、页脚

    /** 设置页自己的深浅色：晚上看黑底，白天切成米白底 */
    private boolean dark = true;
    private int backgroundColor;
    private int titleColor;
    private int bodyColor;
    private int hintColor;
    private int strokeOffColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        dark = preferences.getBoolean("dark", true);
        applyTheme();
        getWindow().setStatusBarColor(backgroundColor);
        if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // 小屏内容会超出屏幕，整页可滚；底部「返回时钟」就不会被切掉
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor);
        root.setPadding(64, 48, 64, 32);

        TextView title = textView("文学时钟", FS_DISPLAY, titleColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 20);

        root.addView(textView("在时钟页面上滑进这里，点一下退出时钟", FS_MICRO, hintColor),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 48);

        root.addView(sectionTitle("档位"));
        addSpace(root, 12);
        root.addView(textView("钟面上的句子，由你决定它离此刻有多远。", FS_MICRO, hintColor));
        addSpace(root, 20);
        root.addView(createLevelChoices());
        addSpace(root, 44);

        root.addView(sectionTitle("外观"));
        addSpace(root, 22);
        root.addView(createDarkRow());
        addSpace(root, 30);
        root.addView(createBackgroundChoices());
        addSpace(root, 18);
        root.addView(textView(
                "墨水屏：纯白底纯黑字，不做彩色强调；翻页不播动画，直接换字",
                FS_MICRO, hintColor));
        addSpace(root, 52);

        root.addView(sectionTitle("字体"));
        addSpace(root, 10);
        root.addView(divider());
        root.addView(createFontRow());
        root.addView(divider());
        addSpace(root, 48);

        root.addView(sectionTitle("亮屏时长"));
        addSpace(root, 16);
        TextView timeoutNote = textView(
                "到点自动退出时钟页，之后由系统决定何时息屏（常亮则一直亮着）", FS_MICRO, hintColor);
        timeoutNote.setLineSpacing(0f, 1.3f);
        root.addView(timeoutNote, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 20);
        root.addView(createTimeoutChoices());
        addSpace(root, 32);
        addSpace(root, 36);
        root.addView(createCorpusTitle());
        addSpace(root, 20);
        root.addView(createListRow("喜欢名单", "liked"));
        addSpace(root, 16);
        root.addView(createListRow("屏蔽名单", "blocked"));
        addSpace(root, 16);
        root.addView(createImportRow());
        addSpace(root, 28);

        Button back = new Button(this);
        back.setText("返回时钟");
        back.setTextSize(TypedValue.COMPLEX_UNIT_PX, FS_BODY);
        back.setTextColor(titleColor);
        back.setAllCaps(false);
        back.setStateListAnimator(null);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.RECTANGLE);
        backBg.setCornerRadius(6);
        backBg.setStroke(2, titleColor);
        backBg.setColor(Color.TRANSPARENT);
        back.setBackground(backBg);
        back.setPadding(0, 26, 0, 26);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        root.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        addSpace(root, 28);
        TextView author = textView("作者 Kris", FS_MICRO, hintColor);
        author.setGravity(Gravity.CENTER);
        root.addView(author, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 8);
        TextView version = textView("版本 " + versionName(), FS_MICRO, hintColor);
        version.setGravity(Gravity.CENTER);
        root.addView(version, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

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

    private View createFontRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 26, 0, 26);
        TextView title = textView("正文字体", FS_BODY, titleColor);
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(textView(fontLabel(preferences.getString("font_id", "builtin")), FS_SUB, hintColor));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFontDialog();
            }
        });
        return row;
    }

    private String fontLabel(String id) {
        if (id == null || "builtin".equals(id)) {
            return "霞鹜文楷";
        }
        String[][] names = {{"mashanzheng", "马善政毛笔楷书"}, {"zhimangxing", "志莽行书"},
                {"liujianmaocao", "刘建毛草"}, {"sourcehanserif", "思源宋体"}};
        for (String[] pair : names) {
            if (pair[0].equals(id)) {
                return pair[1];
            }
        }
        return "霞鹜文楷";
    }

    private java.io.File fontFile(String id) {
        return new java.io.File(getFilesDir(), "fonts/" + id + ".ttf");
    }

    private String readAsset(String name) throws Exception {
        java.io.InputStream in = getAssets().open(name);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    private void showFontDialog() {
        try {
            final org.json.JSONArray arr = new org.json.JSONObject(readAsset("fonts.json"))
                    .getJSONArray("fonts");
            final String[] labels = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject f = arr.getJSONObject(i);
                String id = f.getString("id");
                String label = f.getString("name") + "　" + f.getString("note");
                if (!"builtin".equals(id) && fontFile(id).exists()) {
                    label = f.getString("name") + "　已下载";
                }
                if (id.equals(preferences.getString("font_id", "builtin"))) {
                    label = "✓ " + label;
                }
                labels[i] = label;
            }
            new AlertDialog.Builder(this)
                    .setTitle("字体")
                    .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            try {
                                onPickFont(arr.getJSONObject(which));
                            } catch (Throwable ignored) {
                            }
                        }
                    })
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void onPickFont(final org.json.JSONObject f) {
        try {
            if (f.optBoolean("external")) {
                final String url = f.getString("url");
                new AlertDialog.Builder(this)
                        .setTitle(f.getString("name"))
                        .setMessage("完整版约 26 MB，放在 GitHub Releases。\n\n" + url)
                        .setPositiveButton("复制链接", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                        getSystemService(CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("font", url));
                                    Toast.makeText(SettingsActivity.this, "链接已复制", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("关闭", null)
                        .show();
                return;
            }
            final String id = f.getString("id");
            if (f.optBoolean("builtin") || fontFile(id).exists()) {
                preferences.edit().putString("font_id", id).apply();
                recreate();
                return;
            }
            Toast.makeText(this, "开始下载 " + f.getString("name"), Toast.LENGTH_SHORT).show();
            downloadFont(f);
        } catch (Throwable ignored) {
        }
    }

    private void downloadFont(final org.json.JSONObject f) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String name = "";
                try {
                    final String id = f.getString("id");
                    final long size = f.getLong("size");
                    final String sha = f.getString("sha256");
                    name = f.getString("name");
                    org.json.JSONArray srcs = f.getJSONArray("sources");
                    java.io.File dir = new java.io.File(getFilesDir(), "fonts");
                    dir.mkdirs();
                    for (int i = 0; i < srcs.length() && !ok; i++) {
                        java.io.File tmp = new java.io.File(dir, id + ".tmp");
                        try {
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                    new java.net.URL(srcs.getString(i)).openConnection();
                            conn.setConnectTimeout(15000);
                            conn.setReadTimeout(60000);
                            conn.setInstanceFollowRedirects(true);
                            java.io.InputStream in = conn.getInputStream();
                            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                            byte[] buf = new byte[8192];
                            int n;
                            long total = 0;
                            while ((n = in.read(buf)) > 0) {
                                out.write(buf, 0, n);
                                md.update(buf, 0, n);
                                total += n;
                                if (total > size + 4096) {
                                    break;
                                }
                            }
                            out.close();
                            in.close();
                            StringBuilder sb = new StringBuilder();
                            for (byte b : md.digest()) {
                                sb.append(String.format("%02x", b));
                            }
                            if (total == size && sb.toString().equals(sha)) {
                                java.io.File dst = new java.io.File(dir, id + ".ttf");
                                if (dst.exists()) {
                                    dst.delete();
                                }
                                ok = tmp.renameTo(dst);
                            }
                        } catch (Throwable ignored) {
                        }
                        if (!ok) {
                            tmp.delete();
                        }
                    }
                } catch (Throwable ignored) {
                }
                final boolean success = ok;
                final String label = name;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SettingsActivity.this,
                                success ? label + " 已下载，再点一次启用" : "下载失败，换个网络再试",
                                Toast.LENGTH_LONG).show();
                        if (success) {
                            showFontDialog();
                        }
                    }
                });
            }
        }).start();
    }

    private View createLevelChoices() {
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);
        int current = preferences.getInt("level", 3);
        String[] names = {"精准", "偏准", "均衡", "偏意", "写意"};
        String[] subs = {"此刻分明", "大略如斯", "虚实之间", "若有若无", "不问时辰"};
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            boolean on = current == value;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            if (on) {
                View line = new View(this);
                line.setBackgroundColor(Color.rgb(206, 86, 72));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(40, 3);
                lp.bottomMargin = 8;
                col.addView(line, lp);
            } else {
                addSpaceTo(col, 11);
            }
            TextView nameView = textView(names[i], FS_BODY, on ? titleColor : hintColor);
            nameView.setGravity(Gravity.CENTER);
            col.addView(nameView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            addSpaceTo(col, 6);
            TextView subView = textView(subs[i], FS_MICRO, on ? bodyColor : strokeOffColor);
            subView.setGravity(Gravity.CENTER);
            col.addView(subView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            col.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    preferences.edit().putInt("level", value).apply();
                    recreate();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            choices.addView(col, params);
        }
        return choices;
    }

    private void addSpaceTo(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
    }

    private View createCorpusTitle() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(sectionTitle("语料"));
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(getAssets().open("count.txt")));
            String count = reader.readLine();
            reader.close();
            if (count != null && count.trim().length() > 0) {
                TextView countView = textView(count.trim() + " 句", FS_SUB, hintColor);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 16;
                row.addView(countView, params);
            }
        } catch (Throwable ignored) {
        }
        return row;
    }

    private View createImportRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 30, 0, 30);
        row.addView(textView("导入语料", FS_BODY, bodyColor), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(textView("›", FS_SUB, hintColor));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(SettingsActivity.this, ImportActivity.class));
                } catch (Throwable ignored) {
                }
            }
        });
        return row;
    }

    private View createListRow(String label, final String type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 30, 0, 30);
        row.addView(textView(label, FS_BODY, bodyColor), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        int count = preferences.getStringSet(type,
                java.util.Collections.<String>emptySet()).size();
        TextView countView = textView(count + " 句", FS_SUB, hintColor);
        if ("liked".equals(type)) {
            likedCount = countView;
        } else {
            blockedCount = countView;
        }
        row.addView(countView);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SettingsActivity.this, ListActivity.class);
                intent.putExtra("type", type);
                startActivity(intent);
            }
        });
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (likedCount != null) {
                likedCount.setText(preferences.getStringSet("liked",
                        java.util.Collections.<String>emptySet()).size() + " 句");
            }
            if (blockedCount != null) {
                blockedCount.setText(preferences.getStringSet("blocked",
                        java.util.Collections.<String>emptySet()).size() + " 句");
            }
        } catch (Throwable ignored) {
        }
    }

    private View createDarkRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textView("深色模式", FS_BODY, bodyColor),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(createDarkSwitch(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View createDarkSwitch() {
        Switch toggle = new Switch(this);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, FS_BODY);
        toggle.setTextColor(bodyColor);
        toggle.setChecked(dark);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                preferences.edit().putBoolean("dark", checked).apply();
                recreate();
            }
        });
        return toggle;
    }

    private View createBackgroundChoices() {
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);

        for (int i = 0; i < BACKGROUND_KEYS.length; i++) {
            final int index = i;
            LinearLayout choice = new LinearLayout(this);
            choice.setOrientation(LinearLayout.VERTICAL);
            choice.setGravity(Gravity.CENTER);
            choice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    preferences.edit().putString("bg", BACKGROUND_KEYS[index]).apply();
                    updateBackgroundSelection();
                }
            });

            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(BACKGROUND_COLORS[i]);
            swatches[i] = swatch;
            View circle = new View(this);
            circle.setBackground(swatch);
            choice.addView(circle, new LinearLayout.LayoutParams(84, 84));

            TextView name = textView(BACKGROUND_NAMES[i], FS_SUB, bodyColor);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = 12;
            choice.addView(name, nameParams);

            choices.addView(choice, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        updateBackgroundSelection();
        return choices;
    }

    private void updateBackgroundSelection() {
        String selected = preferences.getString("bg", "black");
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i] != null) {
                boolean isSelected = BACKGROUND_KEYS[i].equals(selected);
                swatches[i].setStroke(6, isSelected ? titleColor : strokeOffColor);
            }
        }
    }

    private View createTimeoutChoices() {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        int selectedTimeout = preferences.getInt("timeout", 30);

        for (int i = 0; i < TIMEOUTS.length; i++) {
            final int timeout = TIMEOUTS[i];
            RadioButton option = new RadioButton(this);
            option.setId(i + 1);
            option.setText(TIMEOUT_NAMES[i]);
            option.setTextSize(TypedValue.COMPLEX_UNIT_PX, FS_SUB);
            option.setTextColor(bodyColor);
            option.setChecked(timeout == selectedTimeout);
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    preferences.edit().putInt("timeout", timeout).apply();
                }
            });
            group.addView(option, new RadioGroup.LayoutParams(0,
                    RadioGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return group;
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

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(strokeOffColor);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return line;
    }

    private void addSpace(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, height));
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知";
        }
    }
}
