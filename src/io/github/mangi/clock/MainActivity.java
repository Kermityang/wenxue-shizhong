package io.github.mangi.clock;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ScaleXSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[][] LINES = {
            {"人闲桂花落，\n夜静春山空。", "王维　《鸟鸣涧》"},
            {"夜半钟声到客船。", "张继　《枫桥夜泊》"},
            {"月上柳梢头，\n人约黄昏后。", "欧阳修　《生查子》"}
    };

    private static final class Entry {
        final String time;
        final String s;
        final String h;
        final String b;

        Entry(String time, String s, String h, String b) {
            this.time = time;
            this.s = s;
            this.h = h;
            this.b = b;
        }
    }

    private static final class Palette {
        final int background;
        final int quote;
        final int book;
        final int clock;
        final int date;
        final int highlight;
        final boolean boldHighlight;

        Palette(int background, int quote, int book, int clock, int date, int highlight) {
            this(background, quote, book, clock, date, highlight, false);
        }

        Palette(int background, int quote, int book, int clock, int date, int highlight,
                boolean boldHighlight) {
            this.background = background;
            this.quote = quote;
            this.book = book;
            this.clock = clock;
            this.date = date;
            this.highlight = highlight;
            this.boldHighlight = boldHighlight;
        }
    }

    private static final Palette BLACK = new Palette(
            Color.rgb(0, 0, 0), Color.rgb(238, 234, 226), Color.rgb(146, 142, 136),
            Color.rgb(150, 146, 140), Color.rgb(96, 94, 90), Color.rgb(206, 86, 72));
    private static final Palette BLUE = new Palette(
            Color.rgb(18, 38, 63), Color.rgb(232, 236, 242), Color.rgb(152, 168, 186),
            Color.rgb(150, 164, 182), Color.rgb(96, 112, 132), Color.rgb(228, 122, 96));
    private static final Palette BROWN = new Palette(
            Color.rgb(51, 38, 26), Color.rgb(238, 230, 216), Color.rgb(170, 152, 132),
            Color.rgb(168, 152, 130), Color.rgb(112, 98, 82), Color.rgb(222, 120, 88));
    /** 黑白墨水屏：纯白底、纯黑字，不做彩色强调（红色在墨水屏上只剩灰） */
    private static final Palette INK = new Palette(
            Color.WHITE, Color.BLACK, Color.rgb(64, 64, 64),
            Color.rgb(48, 48, 48), Color.rgb(112, 112, 112), Color.BLACK, true);
    private static final Palette MOON = new Palette(
            Color.rgb(233, 229, 218), Color.rgb(35, 32, 27), Color.rgb(107, 101, 92),
            Color.rgb(85, 81, 74), Color.rgb(126, 120, 110), Color.rgb(168, 58, 44));

    private Handler handler;
    private SharedPreferences preferences;
    private Palette palette = BLACK;
    private FrameLayout rootView;
    private LinearLayout quoteLayout;
    private TextView timeView;
    private TextView dateView;
    private TextView quoteView;
    private TextView bookView;
    private HashMap<String, List<Entry>> byTime;
    private List<Entry> entries;
    private Entry shown;
    private float typeBase;
    private float sourceBase;
    private float shortSide;
    private int baseTopMargin;
    private LinearLayout clockLayout;
    private boolean landscape;
    private String highlight = "";
    private String quoteText = "";
    private float baseQuoteSize = 40f;
    private float quoteScale = 1f;
    private int shrinkSteps = 0;
    private float quoteLineMult = 1.75f;
    private float currentQuoteSize = 60f;
    private String usedDate = "";
    private final java.util.Set<String> usedToday = new java.util.HashSet<>();
    private String lastMinuteKey = "";
    private Entry lastChoice;
    private boolean adjusting = false;
    private boolean bookMode = false;
    private boolean indentMode = false;
    private int quoteTopPad;
    private int quoteBottomPad;
    private FrameLayout quoteContainer;
    private int screenHeightPx;
    private int lastQuoteWidth;
    private int contentAvail;
    private int timeoutMinutes = 30;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            flipPage();
            handler.postDelayed(this, delayToNextMinute());
        }
    };

    private final Runnable autoExit = new Runnable() {
        @Override
        public void run() {
            // 到点先把「常亮」摘掉，再退出，交给系统按自己的超时息屏
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            preferences = getSharedPreferences(SettingsActivity.PREFERENCES, MODE_PRIVATE);
            palette = paletteFor(preferences.getString("bg", "black"));
            timeoutMinutes = preferences.getInt("timeout", 30);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setBackgroundDrawable(new ColorDrawable(palette.background));
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            // 铺到刘海/摄像头挖孔区域里，否则换浅色背景时那里会留一条黑
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(layoutParams);
            }
            // LAYOUT_* 让窗口铺到状态栏/导航栏底下，否则换浅色背景时那一条还是黑的
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            landscape = screenWidth > screenHeight;
            int shortSide = Math.min(screenWidth, screenHeight);
            this.shortSide = shortSide;
            int longSide = Math.max(screenWidth, screenHeight);
            // 字号跟着屏幕短边走；横屏行更短、更该放大，竖屏也略放大一点
            typeBase = shortSide * (landscape ? 1.16f : 1.08f);
            sourceBase = shortSide * (landscape ? 0.041f : 0.034f);
            Typeface wenKai = loadWenKai();
            entries = loadEntries();
            byTime = new HashMap<>();
            if (entries != null) {
                for (Entry entry : entries) {
                    List<Entry> bucket = byTime.get(entry.time);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        byTime.put(entry.time, bucket);
                    }
                    bucket.add(entry);
                }
            }

            rootView = new FrameLayout(this);
            rootView.setBackgroundColor(palette.background);
            getWindow().getDecorView().setBackgroundColor(palette.background);

            quoteLayout = new LinearLayout(this);
            quoteLayout.setOrientation(LinearLayout.VERTICAL);
            quoteLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            quoteLayout.setCameraDistance(12000 * getResources().getDisplayMetrics().density);
            quoteLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int blockHeight = bottom - top;
                    int height = screenHeightPx > 0 ? screenHeightPx : 0;
                    if (blockHeight <= 0 || height <= 0 || shrinkSteps >= 3) {
                        return;
                    }
                    int band = height - quoteTopPad - quoteBottomPad;
                    if (blockHeight > band) {
                        shrinkSteps++;
                        float size = quoteView.getTextSize() * 0.92f;
                        quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                        applyQuoteText(size);
                    }
                }
            });
            int basePad = (int) (screenWidth * (landscape ? 0.075f : 0.062f));
            contentAvail = screenWidth - 2 * basePad;
            // 正文只拉伸到可用宽度的 98.5%，右边天然多留 1.5%；把右内边距减掉这 1.5%，
            // 左右留白才真正相等（这是「右边看着空」的来源）
            int rightPad = basePad - (int) (contentAvail * 0.015f);
            quoteLayout.setPadding(basePad, 0, rightPad, 0);

            // 引文块放进铺满全屏的容器：底部固定给时钟留位，gravity=CENTER 由框架居中。
            // 目标中心 = 屏高 × quoteCenterRatio()，上边距按它反推（方屏 50%，长直板约 42%）。
            quoteContainer = new FrameLayout(this);
            // 给时钟留的高度先按估算给个初值，布局出来后用实测的时钟块高度校正
            quoteBottomPad = (int) (shortSide * 0.264f);
            applyQuoteInsets();
            quoteContainer.addView(quoteLayout, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            rootView.addView(quoteContainer, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            // 实测宽度一确定（含旋转后重新布局）就按真实宽度重排一次，横屏才不会每行短一截
            rootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (quoteView == null) {
                        return;
                    }
                    int width = quoteView.getWidth();
                    if (width > 0 && width != lastQuoteWidth) {
                        lastQuoteWidth = width;
                        render();
                    }
                }
            });

            quoteView = new TextView(this);
            quoteView.setTypeface(wenKai);
            quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, typeBase * 0.060f);
            quoteView.setTextColor(palette.quote);
            quoteView.setLineSpacing(0f, 1.9f);
            quoteView.setGravity(Gravity.CENTER);
            quoteLayout.addView(quoteView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            bookView = new TextView(this);
            bookView.setTypeface(Typeface.SERIF);
            bookView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sourceBase);
            bookView.setTextColor(palette.book);
            bookView.setLetterSpacing(0.03f);
            bookView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bookParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bookParams.topMargin = (int) (shortSide * (landscape ? 0.062f : 0.089f));
            quoteLayout.addView(bookView, bookParams);

            clockLayout = new LinearLayout(this);
            clockLayout.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            clockParams.rightMargin = (int) (shortSide * 0.067f);
            clockParams.bottomMargin = (int) (shortSide * 0.097f);
            rootView.addView(clockLayout, clockParams);
            clockLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int rootHeight = rootView != null ? rootView.getHeight() : 0;
                    if (rootHeight <= 0 || clockLayout.getTop() <= 0) {
                        return;
                    }
                    // 预留 = 屏底到时钟块顶的高度 + 一点空隙，横竖屏都用真实值
                    int reserve = rootHeight - clockLayout.getTop() + (int) (shortSide * 0.022f);
                    if (Math.abs(reserve - quoteBottomPad) > 2) {
                        quoteBottomPad = reserve;
                        applyQuoteInsets();
                    }
                }
            });

            timeView = new TextView(this);
            timeView.setTypeface(wenKai);
            timeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * 0.074f);
            timeView.setTextColor(palette.clock);
            clockLayout.addView(timeView);

            dateView = new TextView(this);
            dateView.setTypeface(wenKai);
            dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * 0.0292f);
            dateView.setTextColor(palette.date);
            clockLayout.addView(dateView);

            // 点一下 = 退出时钟；上滑 = 进设置
            final GestureDetector detector = new GestureDetector(this,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent event) {
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent from, MotionEvent to,
                                               float velocityX, float velocityY) {
                            if (from == null || to == null) {
                                return false;
                            }
                            float dy = to.getY() - from.getY();
                            float dx = to.getX() - from.getX();
                            if (Math.abs(dy) > 120f || Math.abs(dx) > 160f) {
                                openSettings();
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public boolean onSingleTapUp(MotionEvent event) {
                            finish();
                            return true;
                        }
                    });
            rootView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    return detector.onTouchEvent(event);
                }
            });

            setContentView(rootView);
            handler = new Handler(Looper.getMainLooper());
            render();
            handler.postDelayed(minuteTick, delayToNextMinute());
        } catch (Throwable throwable) {
            showError(throwable);
        }
    }

    private Palette paletteFor(String key) {
        if ("blue".equals(key)) {
            return BLUE;
        }
        if ("brown".equals(key)) {
            return BROWN;
        }
        if ("moon".equals(key)) {
            return MOON;
        }
        if ("ink".equals(key)) {
            return INK;
        }
        return BLACK;
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, SettingsActivity.class));
        } catch (Throwable ignored) {
        }
    }

    /** 距离下一个整分还有多少毫秒（多等 30ms，确保系统时钟已经进位） */
    private long delayToNextMinute() {
        long now = System.currentTimeMillis();
        return 60000L - now % 60000L + 30L;
    }

    private void flipPage() {
        if (quoteLayout == null || quoteLayout.getWidth() == 0) {
            render();
            return;
        }
        quoteLayout.setPivotX(quoteLayout.getWidth() / 2f);
        quoteLayout.setPivotY(quoteLayout.getHeight() / 2f);
        quoteLayout.setRotationY(0f);
        quoteLayout.animate().cancel();
        quoteLayout.animate()
                .rotationY(-88f)
                .setDuration(240)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        render();
                        quoteLayout.setRotationY(88f);
                        quoteLayout.animate()
                                .rotationY(0f)
                                .setDuration(320)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                })
                .start();
    }

    private Typeface loadWenKai() {
        try {
            return Typeface.createFromAsset(getAssets(), "LXGWWenKai-Light.ttf");
        } catch (Throwable ignored) {
            return Typeface.SERIF;
        }
    }

    private List<Entry> loadEntries() {
        try (InputStream input = getAssets().open("clock.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            JSONArray array = new JSONArray(new String(output.toByteArray(), "UTF-8"));
            List<Entry> loaded = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                loaded.add(new Entry(object.getString("time"), object.getString("s"),
                        object.optString("h", ""), object.getString("b")));
            }
            return loaded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Entry pickEntry(Calendar calendar) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        String key = String.format(Locale.US, "%02d:%02d", hour, minute);
        loadUsed(calendar);
        // 同一分钟内重新渲染（旋转、换配色、回前台、进程重建）都复用同一句，绝不换句
        String savedMinute = preferences.getString("pick_minute", "");
        if (key.equals(savedMinute)) {
            String savedText = preferences.getString("pick_sentence", "");
            List<Entry> bucket = byTime.get(key);
            if (bucket != null) {
                for (Entry entry : bucket) {
                    if (entry.s.equals(savedText)) {
                        return entry;
                    }
                }
            }
        }
        Entry chosen = null;
        List<Entry> exact = byTime.get(key);
        if (exact != null && !exact.isEmpty()) {
            int size = exact.size();
            int start = calendar.get(Calendar.DAY_OF_MONTH) % size;
            for (int k = 0; k < size; k++) {
                Entry candidate = exact.get((start + k) % size);
                if (!usedToday.contains(candidate.s)) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null) {
                // 候选今天都用过了：宁可重复本分钟的这一句，也绝不跨分钟借——
                // 每句都自带时间词，借来的句子会和屏幕上的时间对不上
                chosen = exact.get(start);
            }
        } else {
            // 这一分钟没有语料（正常不会发生）：只取时间最近的句子
            chosen = nearestAny(hour * 60 + minute);
        }
        if (chosen != null) {
            preferences.edit().putString("pick_minute", key)
                    .putString("pick_sentence", chosen.s).apply();
            if (usedToday.add(chosen.s)) {
                java.util.HashSet<String> copy = new java.util.HashSet<>(usedToday);
                preferences.edit().putStringSet("used_today", copy).apply();
            }
        }
        return chosen;
    }

    /** 就近找一条今天还没出现过的句子（按时间距离，绝不倒退到已过去的时刻太远） */
    private Entry nearestUnused(int nowMin) {
        Entry best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Entry entry : entries) {
            if (usedToday.contains(entry.s)) {
                continue;
            }
            int dist = minuteDistance(entry, nowMin);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        return best;
    }

    private Entry nearestAny(int nowMin) {
        Entry best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Entry entry : entries) {
            int dist = minuteDistance(entry, nowMin);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        return best;
    }

    private static int minuteDistance(Entry entry, int nowMin) {
        try {
            int t = Integer.parseInt(entry.time.substring(0, 2)) * 60
                    + Integer.parseInt(entry.time.substring(3, 5));
            return ((t - nowMin) % 1440 + 1440) % 1440;
        } catch (Throwable ignored) {
            return Integer.MAX_VALUE;
        }
    }

    /** 读取「今天已经展示过的句子」；跨日自动清空 */
    private void loadUsed(Calendar calendar) {
        String today = String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
        if (today.equals(usedDate)) {
            return;
        }
        String savedDate = preferences.getString("used_date", "");
        usedToday.clear();
        if (today.equals(savedDate)) {
            java.util.Set<String> saved = preferences.getStringSet("used_today", null);
            if (saved != null) {
                usedToday.addAll(saved);
            }
        } else {
            preferences.edit().putString("used_date", today)
                    .putStringSet("used_today", new java.util.HashSet<String>()).apply();
        }
        usedDate = today;
    }


    private static boolean isSame(Entry a, Entry b) {
        return a.time.equals(b.time) && a.s.equals(b.s) && a.b.equals(b.b);
    }

    private void render() {
        Calendar calendar = Calendar.getInstance();
        Entry entry = pickEntry(calendar);
        shown = entry;
        String text;
        String book;
        String mark = "";
        if (entry != null) {
            text = entry.s;
            book = entry.b;
            mark = entry.h == null ? "" : entry.h;
        } else {
            int minute = calendar.get(Calendar.MINUTE);
            text = LINES[minute % LINES.length][0];
            book = LINES[minute % LINES.length][1];
        }
        quoteText = text;
        highlight = mark;

        int textLen = text.length();
        float ratio;
        if (textLen <= 20) {
            ratio = 0.072f;
        } else if (textLen <= 40) {
            ratio = 0.058f;
        } else if (textLen <= 70) {
            ratio = 0.047f;
        } else {
            ratio = 0.038f;
        }
        float quoteSize = typeBase * ratio;
        float mult = textLen > 40 ? 1.55f : 1.75f;
        quoteLineMult = mult;
        bookMode = isBookText(text, book);
        quoteView.setLineSpacing(0f, mult);
        quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, quoteSize);
        baseQuoteSize = quoteSize;
        currentQuoteSize = quoteSize;
        quoteScale = 1f;
        shrinkSteps = 0;
        // 块太高就逐档缩字号，保证整块落在「时钟上方」那块地里
        float room = quoteRoomHeight();
        indentMode = bookMode;
        Laid laid = layoutQuote(quoteText, quoteSize, bookMode, indentMode);
        if (laid.lines <= 1) {
            // 只有一行时行距 1.75 会在句子下方留出大半行空白，出处被顶得很远；压到 1.15
            mult = 1.15f;
            quoteLineMult = mult;
            quoteView.setLineSpacing(0f, mult);
        }
        while (estimateHeight(laid.lines, quoteSize, mult) > room && quoteSize > baseQuoteSize * 0.72f) {
            quoteSize *= 0.92f;
            quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, quoteSize);
            laid = layoutQuote(quoteText, quoteSize, bookMode, indentMode);
        }
        applyLaid(laid);
        layOutSource(formatSource(book));

        timeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(calendar.getTime()));
        String[] weekdays = {"日", "一", "二", "三", "四", "五", "六"};
        String date = (calendar.get(Calendar.MONTH) + 1) + "月"
                + calendar.get(Calendar.DAY_OF_MONTH) + "日 周"
                + weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1];
        dateView.setText(date);
    }

    /** 把 "作者　《书名》" 排成引文出处：「——作者《书名》」 */
    private String formatSource(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int split = text.indexOf("　《");
        String author = "";
        String title = text;
        if (split > 0) {
            author = text.substring(0, split).trim();
            title = text.substring(split + 1).trim();
        }
        if (!title.startsWith("《")) {
            title = "《" + title + "》";
        }
        return author.isEmpty() ? "——" + title : "——" + author + title;
    }

    /** 排好的引文：带显式换行的文本，以及每个显示字符对应的原文下标（高亮用，换行处是 -1） */
    private static final class Laid {
        final String text;
        final int[] map;
        final int lines;
        final float widest;

        Laid(String text, int[] map, int lines, float widest) {
            this.text = text;
            this.map = map;
            this.lines = lines;
            this.widest = widest;
        }
    }

    /** 不许出现在行首的收尾标点：遇到它就把前一个字一起带到下一行（尽量不留下孤零零的标点） */
    private static final String CLOSE_PUNCT = "，。、；：？！）】》」』”’…—·,.!?;:%)]}";
    /** 不许留在行尾的起首标点 */
    private static final String OPEN_PUNCT = "（【《「『“‘([{";

    /** 按「目标中心」和「给时钟预留的高度」算出容器上下留白 */
    private void applyQuoteInsets() {
        if (quoteContainer == null || shortSide <= 0f) {
            return;
        }
        int height = screenHeightPx > 0 ? screenHeightPx
                : getResources().getDisplayMetrics().heightPixels;
        quoteTopPad = (int) Math.max(0f, 2f * height * quoteCenterRatio() - height + quoteBottomPad);
        quoteContainer.setPadding(0, quoteTopPad, 0, quoteBottomPad);
    }

    /** 把每一行的高度钉死成同一个值，避免个别行（有无字距 span、标点不同）行高漂移 */
    private static final class FixedLineHeightSpan implements android.text.style.LineHeightSpan {
        private final int height;

        FixedLineHeightSpan(int height) {
            this.height = height;
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv,
                                 int lineHeight, android.graphics.Paint.FontMetricsInt fm) {
            int center = (fm.ascent + fm.descent) / 2;
            fm.ascent = center - height / 2;
            fm.descent = center + height / 2;
            fm.top = fm.ascent;
            fm.bottom = fm.descent;
        }
    }

    /** 只改字间距、不变字形的 span：中文两端对齐的正规做法 */
    private static final class LetterSpacingSpan extends android.text.style.MetricAffectingSpan {
        private final float spacing;

        LetterSpacingSpan(float spacing) {
            this.spacing = spacing;
        }

        @Override
        public void updateDrawState(android.text.TextPaint paint) {
            paint.setLetterSpacing(spacing);
        }

        @Override
        public void updateMeasureState(android.text.TextPaint paint) {
            paint.setLetterSpacing(spacing);
        }
    }

    /** 出处排版：太长就缩字号，缩到 0.7 倍仍放不下就在书名号/括号处折成两行，避免被屏幕切掉 */
    private void layOutSource(String raw) {
        String text = raw == null ? "" : raw;
        float size = sourceBase;
        float avail = quoteWidth();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface typeface = bookView != null ? bookView.getTypeface() : Typeface.SERIF;
        if (typeface != null) {
            paint.setTypeface(typeface);
        }
        paint.setTextSize(size);
        float width = paint.measureText(text);
        if (width > avail && width > 0f) {
            float fit = avail / width;
            if (fit >= 0.7f) {
                size *= fit;
            } else {
                size *= 0.7f;
                text = breakSource(text);
            }
        }
        bookView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        bookView.setText(text);
    }

    /** 在中间附近找一个体面的断点（书名号、括号、间隔号之后），找不到就硬折 */
    private static String breakSource(String text) {
        int middle = text.length() / 2;
        int best = -1;
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '、' || c == '，' || c == '·' || c == '（' || c == '《' || c == '）'
                    || c == '》' || c == ' ') {
                if (best < 0 || Math.abs(i - middle) < Math.abs(best - middle)) {
                    best = i + 1;
                }
            }
        }
        if (best <= 0 || best >= text.length()) {
            best = middle;
        }
        return text.substring(0, best) + "\n" + text.substring(best);
    }

    /** 小说、散文按书本排（≥两句，或超过 40 字）；短诗仍按句读居中 */
    /** 明显是小说/笔记的出处：这类一律按书页排，不管句子长短 */
    private static final String[] NOVELS = {"水浒", "西游记", "三国演义", "红楼梦", "儒林外史", "金瓶梅",
            "聊斋", "说岳", "镜花缘", "封神演义", "儿女英雄传", "老残游记", "官场现形记", "二十年目睹",
            "三侠五义", "醒世恒言", "喻世明言", "警世通言", "初刻拍案惊奇", "二刻拍案惊奇", "拍案惊奇",
            "东周列国", "隋唐演义", "说唐", "济公", "阅微草堂", "子不语", "夜雨秋灯",
            "倚天屠龙记", "神雕侠侣", "射雕英雄传", "天龙八部", "笑傲江湖", "鹿鼎记", "连城诀",
            "碧血剑", "侠客行", "雪山飞狐", "飞狐外传", "书剑恩仇录", "白马啸西风"};

    /** 宋人笔记、史志、风土记：叙事体，按书页排 */
    private static final String[] NOTES = {"梦粱录", "东京梦华录", "武林旧事", "东坡志林", "老学庵笔记",
            "容斋随笔", "鸡肋编", "齐东野语", "鹤林玉露", "清波杂志", "春渚纪闻", "能改斋漫录",
            "游宦纪闻", "癸辛杂识", "贵耳集", "铁围山丛谈", "世说新语", "阅微草堂", "子不语"};

    private static boolean isNotesSource(String source) {
        if (source == null) {
            return false;
        }
        for (String note : NOTES) {
            if (source.contains(note)) {
                return true;
            }
        }
        return false;
    }

    /** 一整联（五言/七言各半）就算诗，即便出自小说或笔记 */
    private static boolean isCouplet(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        while (t.endsWith("。") || t.endsWith("！") || t.endsWith("？")) {
            t = t.substring(0, t.length() - 1);
        }
        int comma = t.indexOf('，');
        if (comma <= 0 || t.indexOf('，', comma + 1) >= 0) {
            return false;
        }
        int a = comma;
        int b = t.length() - comma - 1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '，') {
                continue;
            }
            if (c == '。' || c == '；' || c == '：' || c == '！' || c == '？' || c == '、' || c == '…') {
                return false;
            }
        }
        return a == b && (a == 5 || a == 7);
    }

    private static boolean isNovelSource(String source) {
        if (source == null) {
            return false;
        }
        for (String novel : NOVELS) {
            if (source.contains(novel)) {
                return true;
            }
        }
        return false;
    }

    /** 史书、诸子、散文：即使句子短也按书页排 */
    private static final String[] PROSE = {"史记", "汉书", "后汉书", "三国志", "资治通鉴", "左传", "国语",
            "战国策", "孟子", "庄子", "荀子", "韩非", "墨子", "老子", "论语", "礼记", "周易", "尚书",
            "醉翁亭记", "水经注", "世说新语", "梦溪笔谈", "东坡志林"};

    private static boolean isProseSource(String source) {
        if (source == null) {
            return false;
        }
        for (String p : PROSE) {
            if (source.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBookText(String text, String source) {
        if (text == null) {
            return false;
        }
        // 歌词、短诗：整句不超过 14 字、最多一个逗号，且出处不是小说/史书/散文 —— 按诗排（居中、不缩进）。
        // 否则「此刻夜深晚风不再。」这种八言歌词会被当成散文，顶格加首行缩进。
        String trimmed = text.trim();
        int commas = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '，') {
                commas++;
            }
        }
        if (!isNovelSource(source) && !isNotesSource(source) && !isProseSource(source)
                && trimmed.length() <= 14 && commas <= 1) {
            return false;
        }
        if (isNovelSource(source) || isNotesSource(source)) {
            return !isCouplet(text);
        }
        if (text.length() > 40) {
            return true;
        }
        int sentences = 0;
        int clause = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？') {
                sentences++;
            }
            if (c == '，' || c == '。' || c == '；' || c == '：' || c == '！' || c == '？'
                    || c == '、' || c == '…') {
                clause = 0;
            } else {
                clause++;
                // 从句超过 7 字就是散文/小说句子（七言诗正好 7 字）；
                // 元曲这类韵文放宽到 8 字，避免把唱词排成段落
                int limit = (source != null && source.contains("元曲")) ? 8 : 7;
                if (clause > limit) {
                    return true;
                }
            }
        }
        if (sentences >= 2 && text.length() > 24) {
            return true;
        }
        // 三个以上从句、总长 24 字往上、每个从句都不长（≤8 字）：多从句的叙事句，
        // 例如「十三点零五分，阿莱恩打开外门，在石阶边磕净烟斗，随后仍站在那里…」，
        // 按诗排会把一句话拆成一行行，看着像诗其实是小说。
        int clauses = 0;
        int longest = 0;
        int current = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '，' || c == '。' || c == '；' || c == '：' || c == '！' || c == '？'
                    || c == '、' || c == '…') {
                if (current > 0) {
                    clauses++;
                    longest = Math.max(longest, current);
                    current = 0;
                }
            } else {
                current++;
            }
        }
        if (current > 0) {
            clauses++;
            longest = Math.max(longest, current);
        }
        return clauses >= 3 && text.length() >= 24 && longest <= 8;
    }

    private float bookMargin() {
        return shortSide * (landscape ? 0.062f : 0.089f);
    }

    /** 用字体度量算一行的高度，和 TextView 的实际行高对得上，不再拍系数 */
    private float lineHeight(float size, float mult) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (quoteView != null && quoteView.getTypeface() != null) {
            paint.setTypeface(quoteView.getTypeface());
        }
        paint.setTextSize(size);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float height = metrics.descent - metrics.ascent;
        if (height <= 0f) {
            height = size * 1.15f;
        }
        return height * mult;
    }

    private float estimateHeight(int lines, float size, float mult) {
        return lines * lineHeight(size, mult) + bookMargin() + lineHeight(sourceBase, 1f);
    }

    /**
     * 引文块目标中心（占屏高比例）：方屏 47% 正好；长直板屏太富余，47% 会显得吊在中间，
     * 所以屏越高越往上收，最低 42%。
     */
    private float quoteCenterRatio() {
        int height = screenHeightPx > 0 ? screenHeightPx
                : getResources().getDisplayMetrics().heightPixels;
        float aspect = shortSide > 0 ? height / shortSide : 1.1f;
        if (aspect <= 1.4f) {
            return 0.50f;
        }
        return Math.max(0.42f, 0.50f - (aspect - 1.4f) * 0.14f);
    }

    /** 引文块可用高度：屏高减去上留白和底部给时钟留的位置 */
    private float quoteRoomHeight() {
        int height = rootView != null && rootView.getHeight() > 0
                ? rootView.getHeight() : getResources().getDisplayMetrics().heightPixels;
        return Math.max(120f, height - quoteTopPad - quoteBottomPad);
    }

    /** 引文可用宽度，留 3% 余量：自己量出来的行宽只要比 TextView 内部换行宽半点，就会又冒出一个孤字 */
    /** 引文视图的实测宽度（横竖屏重建时 displayMetrics 可能还是旧值） */
    private float quoteViewWidth() {
        int width = quoteView != null ? quoteView.getWidth() : 0;
        if (width <= 0) {
            width = contentAvail;
        }
        return Math.max(60f, width);
    }

    /**
     * 装行宽度：两种模式都按 98.5% 断行。书页模式随后用横向拉伸把「差那一两个字」的空隙
     * 补满，而拉伸目标也是 98.5%（< 视图宽度），所以再大的拉伸也不会溢出、不会二次换行。
     */
    /** 版心宽度：书页扣掉行末标点悬挂的一字，其余全给文字（字符才能靠到右边界） */
    private float quotePackWidth() {
        float view = quoteViewWidth();
        if (bookMode) {
            return Math.max(80f, view - currentQuoteSize * 1.05f);
        }
        return Math.max(80f, view * 0.96f);
    }

    /** 首行可用宽度：书页首行再让出两字缩进 */
    private float quoteFirstLineWidth() {
        if (!bookMode) {
            return quotePackWidth();
        }
        return Math.max(80f, quotePackWidth() - currentQuoteSize * 2.1f);
    }

    private float quoteWidth() {
        return quotePackWidth();
    }

    /**
     * 按「句读」断行：优先在逗号、句号处换行，让每行都以标点收尾，凑不成整句时再退一步做均衡分配，
     * 保证末行不会只剩一两个字（诗的二、三句话各自成行）。
     */
    private Laid layoutQuote(String source, float size, boolean book, boolean indent) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface typeface = quoteView.getTypeface();
        if (typeface != null) {
            paint.setTypeface(typeface);
        }
        paint.setTextSize(size);
        float avail = quoteWidth();

        StringBuilder display = new StringBuilder();
        List<Integer> map = new ArrayList<>();
        int from = 0;
        for (int i = 0; i <= source.length(); i++) {
            if (i == source.length() || source.charAt(i) == '\n') {
                if (i > from) {
                    appendParagraph(display, map, source.substring(from, i), from, paint, size, avail,
                            book, indent);
                }
                from = i + 1;
            }
        }
        if (map.isEmpty()) {
            display.setLength(0);
            display.append(source);
            for (int i = 0; i < source.length(); i++) {
                map.add(i);
            }
        }
        int[] offsets = new int[map.size()];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = map.get(i);
        }
        String text = display.toString();
        String[] rows = text.split("\n", -1);
        float widest = 0f;
        for (String row : rows) {
            widest = Math.max(widest, paint.measureText(row));
        }
        return new Laid(text, offsets, rows.length, widest);
    }

    /** 在「句子中间」断开（这一行没有标点收尾）的代价 */
    private static final double BREAK_PENALTY = 25.0;
    /** 每多占一行的代价：避免为了对齐标点把一张脸排得过长 */
    private static final double LINE_PENALTY = 8.0;
    /** 末行不到三个字（孤字、孤标点）的代价 */
    private static final double WIDOW_PENALTY = 60.0;

    private void appendParagraph(StringBuilder display, List<Integer> map, String paragraph, int offset,
                                 Paint paint, float size, float avail, boolean book, boolean indent) {
        List<String> clusters = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        clusterize(paragraph, offset, clusters, starts);
        int count = clusters.size();
        if (count == 0) {
            return;
        }
        // 以「一个字宽」为单位算，笔画和标点才能放在同一把尺子上
        double[] widths = new double[count];
        boolean[] clauseEnd = new boolean[count];
        for (int i = 0; i < count; i++) {
            String cluster = clusters.get(i);
            widths[i] = paint.measureText(cluster) / size;
            clauseEnd[i] = CLOSE_PUNCT.indexOf(cluster.charAt(cluster.length() - 1)) >= 0;
        }
        double limit = avail / size;
        if (book) {
            // 书页：满行装字，行末若是收尾标点就让它悬挂到版心外（中文书籍的常规做法），
            // 这样字符的右端能对齐，只有标点伸出去一点，肉眼反而整齐
            double hang = 1.0;   // 一个字的悬挂余量
            double firstLimit = indent ? quoteFirstLineWidth() / size : limit;
            int[] lines = packBookHang(widths, starts, clusters, limit, firstLimit, 1.0);
            emitLines(display, map, clusters, starts, lines);
            return;
        }
        // 一句话里有且只有一个逗号（「是二更鼓了，冬冬二鼓。」这类）：两句各占一行，
        // 不要按宽度从中间劈开，否则会出现「冬冬二 / 鼓。」这种断法
        int commas = 0;
        int commaBreak = -1;
        for (int i = 0; i < count; i++) {
            if (clusters.get(i).endsWith("，")) {
                commas++;
                commaBreak = i + 1;
            }
        }
        if (commas == 1 && commaBreak > 0 && commaBreak < count) {
            double left = totalUnits(widths, 0, commaBreak);
            double right = totalUnits(widths, commaBreak, count);
            if (left <= limit + 0.05 && right <= limit + 0.05) {
                emitLines(display, map, clusters, starts, new int[]{commaBreak, count});
                return;
            }
        }
        double firstLimit = limit;
        int base = greedyLineCount(widths, limit);
        int maxLines = paragraph.length() <= 40 ? base + 1 : base;
        int[] breaks = null;
        double bestCost = Double.MAX_VALUE;
        for (int lines = base; lines <= maxLines; lines++) {
            double[] picked = new double[1];
            int[] candidate = dpBreaks(widths, clauseEnd, limit, firstLimit, lines, picked);
            if (candidate == null) {
                continue;
            }
            double cost = picked[0] + LINE_PENALTY * lines;
            if (cost < bestCost) {
                bestCost = cost;
                breaks = candidate;
            }
        }
        if (breaks == null) {
            breaks = packWidths(widths, limit);
        }
        emitLines(display, map, clusters, starts, breaks);
    }

    private static void emitLines(StringBuilder display, List<Integer> map, List<String> clusters,
                                  List<Integer> starts, int[] breaks) {
        int start = 0;
        for (int line = 0; line < breaks.length; line++) {
            int end = breaks[line];
            if (display.length() > 0) {
                display.append('\n');
                map.add(-1);
            }

            for (int i = start; i < end; i++) {
                String cluster = clusters.get(i);
                int base = starts.get(i);
                for (int k = 0; k < cluster.length(); k++) {
                    display.append(cluster.charAt(k));
                    map.add(base + k);
                }
            }
            start = end;
        }
    }

    private static double totalUnits(double[] widths, int from, int to) {
        double total = 0;
        for (int i = Math.max(0, from); i < to; i++) {
            total += widths[i];
        }
        return total;
    }

    /**
     * 在固定行数下找最优断点：行宽尽量均匀（贪心会把字一股脑堆在前几行，剩下孤零零一两个字），
     * 优先断在标点处，末行不足三个字要罚。
     */
    private static int[] dpBreaks(double[] widths, boolean[] clauseEnd, double limit,
                                  double firstLimit, int lines, double[] costOut) {
        int count = widths.length;
        double[] prefix = new double[count + 1];
        for (int i = 0; i < count; i++) {
            prefix[i + 1] = prefix[i] + widths[i];
        }
        double ideal = prefix[count] / lines;
        double[][] cost = new double[lines + 1][count + 1];
        int[][] back = new int[lines + 1][count + 1];
        for (int i = 0; i <= lines; i++) {
            java.util.Arrays.fill(cost[i], Double.MAX_VALUE);
            java.util.Arrays.fill(back[i], -1);
        }
        cost[0][0] = 0;
        for (int line = 1; line <= lines; line++) {
            for (int j = 1; j <= count; j++) {
                if (line == lines && j != count) {
                    continue;
                }
                if (line < lines && j == count) {
                    continue;
                }
                for (int i = 0; i < j; i++) {
                    if (cost[line - 1][i] == Double.MAX_VALUE) {
                        continue;
                    }
                    double width = prefix[j] - prefix[i];
                    double allowed = line == 1 ? firstLimit : limit;
                    if (width > allowed + 0.001) {
                        continue;
                    }
                    double extra = (width - ideal) * (width - ideal);
                    if (j < count && !clauseEnd[j - 1]) {
                        extra += BREAK_PENALTY;
                    }
                    if (line == lines && width < 3) {
                        extra += WIDOW_PENALTY;
                    }
                    double total = cost[line - 1][i] + extra;
                    if (total < cost[line][j]) {
                        cost[line][j] = total;
                        back[line][j] = i;
                    }
                }
            }
        }
        if (cost[lines][count] == Double.MAX_VALUE) {
            return null;
        }
        costOut[0] = cost[lines][count];
        int[] result = new int[lines];
        int j = count;
        for (int line = lines; line >= 1; line--) {
            result[line - 1] = j;
            j = back[line][j];
        }
        return result;
    }

    /** 把一段文字切成「不可拆」的排版单元：汉字一字一个，收尾标点并到前一个字上，起首标点并到后一个字上 */
    private static void clusterize(String paragraph, int offset,
                                   List<String> clusters, List<Integer> starts) {
        StringBuilder pending = new StringBuilder();
        int pendingStart = -1;
        int i = 0;
        while (i < paragraph.length()) {
            char ch = paragraph.charAt(i);
            if (isAsciiWord(ch)) {
                int end = i;
                while (end < paragraph.length() && isAsciiWord(paragraph.charAt(end))
                        && end - i < 12) {
                    end++;
                }
                clusters.add(pending + paragraph.substring(i, end));
                starts.add(pendingStart >= 0 ? pendingStart : offset + i);
                pending.setLength(0);
                pendingStart = -1;
                i = end;
            } else if (OPEN_PUNCT.indexOf(ch) >= 0) {
                if (pending.length() == 0) {
                    pendingStart = offset + i;
                }
                pending.append(ch);
                i++;
            } else if (CLOSE_PUNCT.indexOf(ch) >= 0 && !clusters.isEmpty()) {
                int last = clusters.size() - 1;
                clusters.set(last, clusters.get(last) + pending + ch);
                pending.setLength(0);
                pendingStart = -1;
                i++;
            } else {
                clusters.add(pending.toString() + ch);
                starts.add(pending.length() > 0 ? pendingStart : offset + i);
                pending.setLength(0);
                pendingStart = -1;
                i++;
            }
        }
        if (pending.length() > 0) {
            clusters.add(pending.toString());
            starts.add(pendingStart >= 0 ? pendingStart : offset);
        }
    }

    private static boolean isAsciiWord(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    private static int greedyLineCount(double[] widths, double limit) {
        int lines = 1;
        double current = 0;
        for (double width : widths) {
            if (current > 0 && current + width > limit) {
                lines++;
                current = 0;
            }
            current += width;
        }
        return lines;
    }

    /**
     * 书页装行：先把字填到版心宽度 limit；若下一个单元是收尾标点，且放进悬挂区（limit+hang）不超，
     * 就把它挂在行尾。字符右端因此对齐，标点凸出到版心外。
     */
    private static int[] packBookHang(double[] widths, List<Integer> starts, List<String> clusters,
                                      double limit, double firstLimit, double hang) {
        List<Integer> breaks = new ArrayList<>();
        double current = 0;
        for (int i = 0; i < widths.length; i++) {
            double allowed = breaks.isEmpty() ? firstLimit : limit;
            if (current > 0 && current + widths[i] > allowed) {
                String cluster = clusters.get(i);
                char last = cluster.charAt(cluster.length() - 1);
                boolean hangs = CLOSE_PUNCT.indexOf(last) >= 0;
                if (hangs && current + widths[i] <= allowed + hang) {
                    breaks.add(i + 1);   // 标点挂在本行末尾
                    current = 0;
                    continue;
                }
                breaks.add(i);
                current = 0;
            }
            current += widths[i];
        }
        breaks.add(widths.length);
        int[] result = new int[breaks.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = breaks.get(i);
        }
        return result;
    }

    /** 书本式装行：首行给缩进留出两字，其余行铺满 */
    private static int[] packBook(double[] widths, double limit, double firstLimit) {
        List<Integer> breaks = new ArrayList<>();
        double current = 0;
        double lineLimit = firstLimit;
        for (int i = 0; i < widths.length; i++) {
            if (current > 0 && current + widths[i] > lineLimit) {
                breaks.add(i);
                current = 0;
                lineLimit = limit;
            }
            current += widths[i];
        }
        breaks.add(widths.length);
        int[] result = new int[breaks.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = breaks.get(i);
        }
        return result;
    }

    /** 兜底：整行装满就换行，返回每行结束处的单元下标 */
    private static int[] packWidths(double[] widths, double limit) {
        List<Integer> breaks = new ArrayList<>();
        double current = 0;
        for (int i = 0; i < widths.length; i++) {
            if (current > 0 && current + widths[i] > limit) {
                breaks.add(i);
                current = 0;
            }
            current += widths[i];
        }
        breaks.add(widths.length);
        int[] result = new int[breaks.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = breaks.get(i);
        }
        return result;
    }

    private void applyQuoteText(float size) {
        applyLaid(layoutQuote(quoteText, size, bookMode, indentMode));
    }

    private void applyLaid(Laid laid) {
        // 长句按书本排：左对齐；短诗按句读居中
        quoteView.setGravity(bookMode ? Gravity.LEFT : Gravity.CENTER);
        SpannableString spannable = new SpannableString(laid.text);
        if (highlight != null && !highlight.isEmpty()) {
            int start = quoteText.indexOf(highlight);
            if (start >= 0) {
                int end = start + highlight.length();
                int spanStart = -1;
                int spanEnd = -1;
                for (int i = 0; i < laid.map.length; i++) {
                    int source = laid.map[i];
                    if (source < 0) {
                        continue;
                    }
                    if (spanStart < 0 && source >= start) {
                        spanStart = i;
                    }
                    if (source < end) {
                        spanEnd = i + 1;
                    }
                }
                if (spanStart >= 0 && spanEnd > spanStart) {
                    spannable.setSpan(new ForegroundColorSpan(palette.highlight),
                            spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (palette.boldHighlight) {
                        // 黑白墨水屏没有颜色可用，时间短语改用加粗来突出
                        spannable.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
                                spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
        float size = quoteView.getTextSize();
        Paint metrics = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (quoteView.getTypeface() != null) {
            metrics.setTypeface(quoteView.getTypeface());
        }
        metrics.setTextSize(size);
        Paint.FontMetrics fm = metrics.getFontMetrics();
        float base = fm.descent - fm.ascent;
        if (base > 0f && quoteLineMult > 0f) {
            // 行高钉死，所有行间距一致
            spannable.setSpan(new FixedLineHeightSpan((int) (base * quoteLineMult)), 0,
                    spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        quoteView.setLineSpacing(0f, 1f);

        String text = laid.text;
        int from = 0;
        while (from < text.length()) {
            int to = text.indexOf('\n', from);
            if (to < 0) {
                to = text.length();
            }
            float margin = 0f;
            if (bookMode) {
                margin = (from == 0 && indentMode) ? size * 2f : 0f;
            } else if (to > from && CLOSE_PUNCT.indexOf(text.charAt(to - 1)) >= 0) {
                // 诗行：行尾标点占满一个字宽但墨迹偏左，右移约半个字才看着居中
                margin = size * 0.9f;
            }
            if (margin > 0.5f) {
                spannable.setSpan(new android.text.style.LeadingMarginSpan.Standard(
                                (int) margin, 0), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            from = to + 1;
        }

        quoteView.setText(spannable);
        if (bookMode) {
            quoteView.post(new Runnable() {
                @Override
                public void run() {
                    if (quoteView == null || quoteView.getLayout() == null) {
                        return;
                    }
                    android.text.Layout layout = quoteView.getLayout();
                    float widest = 0f;
                    for (int i = 0; i < layout.getLineCount(); i++) {
                        widest = Math.max(widest, layout.getLineWidth(i));
                    }
                    float dx = Math.max(0f, (quoteView.getWidth() - widest) / 2f);
                    if (Math.abs(quoteView.getTranslationX() - dx) > 1f) {
                        quoteView.setTranslationX(dx);
                    }
                }
            });
        } else if (quoteView != null) {
            quoteView.setTranslationX(0f);
        }
    }

    private void applyPalette() {
        palette = paletteFor(preferences.getString("bg", "black"));
        if (rootView != null) {
            rootView.setBackgroundColor(palette.background);
            try {
                getWindow().setBackgroundDrawable(new ColorDrawable(palette.background));
            } catch (Throwable ignored) {
            }
            quoteView.setTextColor(palette.quote);
            bookView.setTextColor(palette.book);
            timeView.setTextColor(palette.clock);
            dateView.setTextColor(palette.date);
            try {
                getWindow().getDecorView().setBackgroundColor(palette.background);
            } catch (Throwable ignored) {
            }
            applyQuoteText(quoteView.getTextSize());
        }
    }

    private void scheduleAutoExit() {
        handler.removeCallbacks(autoExit);
        if (timeoutMinutes > 0) {
            handler.postDelayed(autoExit, timeoutMinutes * 60000L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (handler != null) {
            timeoutMinutes = preferences.getInt("timeout", 30);
            applyPalette();
            handler.removeCallbacks(minuteTick);
            render();
            handler.postDelayed(minuteTick, delayToNextMinute());
            scheduleAutoExit();
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.removeCallbacks(minuteTick);
            handler.removeCallbacks(autoExit);
        }
        super.onPause();
    }

    private void showError(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        TextView errorView = new TextView(this);
        errorView.setTextColor(Color.rgb(255, 85, 85));
        errorView.setTextSize(22);
        errorView.setPadding(24, 24, 24, 24);
        errorView.setText(stringWriter.toString());
        scrollView.addView(errorView);
        setContentView(scrollView);
    }

    @Override
    protected void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }
}
