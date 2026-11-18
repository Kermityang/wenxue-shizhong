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
            {"他接到庄园的电话，\n是在一点零六分。\n窗外的雪，停了。", "P.D. 詹姆斯　《圣诞老人之死》"},
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

        Palette(int background, int quote, int book, int clock, int date, int highlight) {
            this.background = background;
            this.quote = quote;
            this.book = book;
            this.clock = clock;
            this.date = date;
            this.highlight = highlight;
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
            Color.rgb(48, 48, 48), Color.rgb(112, 112, 112), Color.BLACK);
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
    private boolean adjusting = false;
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
            int sidePadding = (int) (screenWidth * (landscape ? 0.075f : 0.062f));
            quoteLayout.setPadding(sidePadding, 0, sidePadding, 0);
            FrameLayout.LayoutParams quoteParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            if (landscape) {
                // 横屏又长又扁，整块垂直居中最好看
                quoteParams.gravity = Gravity.CENTER;
            } else {
                baseTopMargin = (int) (screenHeight * 0.30f);
                quoteParams.topMargin = baseTopMargin;
            }
            rootView.addView(quoteLayout, quoteParams);
            // 按句读断行后有的短句会多占一行，块变高了要能自己往上让，别压到右下角时钟
            quoteLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    fitQuoteVertically(bottom - top);
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
        List<Entry> exact = byTime.get(key);
        if (exact != null && !exact.isEmpty()) {
            // 同一条时间有多个出处时，按日期轮换，每天读到的不是同一句
            return exact.get(calendar.get(Calendar.DAY_OF_MONTH) % exact.size());
        }
        // 这一分钟语料里没有对应段落（正常不会发生）：只往前找最近的一条，绝不倒退到已经过去的时刻，
        // 也不和上一分钟重复，否则到点翻页会翻出一模一样的文字
        int nowMin = hour * 60 + minute;
        int bestDist = Integer.MAX_VALUE;
        int secondDist = Integer.MAX_VALUE;
        Entry best = null;
        Entry second = null;
        for (Entry entry : entries) {
            try {
                int t = Integer.parseInt(entry.time.substring(0, 2)) * 60
                        + Integer.parseInt(entry.time.substring(3, 5));
                int d = ((t - nowMin) % 1440 + 1440) % 1440;
                if (d < bestDist) {
                    secondDist = bestDist;
                    second = best;
                    bestDist = d;
                    best = entry;
                } else if (d < secondDist) {
                    secondDist = d;
                    second = entry;
                }
            } catch (Throwable ignored) {
            }
        }
        if (shown != null && best != null && isSame(best, shown) && second != null) {
            return second;
        }
        return best;
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
        quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, quoteSize);
        quoteView.setLineSpacing(0f, textLen > 40 ? 1.55f : 1.75f);
        Laid laid = layoutQuote(quoteText, quoteSize);
        // 短诗撑到 72% 行宽（五言、对联这类两行且明显偏窄的才放大）；再大就显得不精致了
        float fill = 1f;
        if (laid.lines >= 2 && laid.lines <= 3 && laid.widest > 0f) {
            float target = quoteWidth() * 0.72f;
            if (laid.widest < target * 0.97f) {
                fill = Math.min(1.4f, target / laid.widest);
            }
        }
        if (fill > 1.03f) {
            Laid filled = layoutQuote(quoteText, quoteSize * fill);
            if (filled.lines <= 3 && filled.widest <= quoteWidth()) {
                quoteSize *= fill;
                quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, quoteSize);
                laid = filled;
            } else {
                fill = 1f;
            }
        }
        baseQuoteSize = quoteSize;
        quoteScale = 1f;
        applyLaid(laid);
        // 正文字号放大了，出处跟着放大一点，别显得孤零零一小行
        bookView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sourceBase * (fill > 1.03f ? 1.2f : 1f));
        bookView.setText(formatSource(book));

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

    /** 竖屏下块太长时整块上移，保证出处还落在时钟上方（横屏本来就是居中，不动） */
    private void fitQuoteVertically(int height) {
        if (landscape || height <= 0 || clockLayout == null || clockLayout.getTop() <= 0) {
            return;
        }
        ViewGroup.LayoutParams raw = quoteLayout.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        int limit = clockLayout.getTop() - (int) (shortSide * 0.022f);
        int top = baseTopMargin;
        int floor = (int) (shortSide * 0.055f);
        if (top + height > limit) {
            top = Math.max(floor, limit - height);
        }
        if (params.topMargin != top) {
            params.topMargin = top;
            quoteLayout.setLayoutParams(params);
        }
        // 最长的那几条（含硬换行的长段落）上移到顶仍放不下：逐档缩字号，别让它压住时钟
        if (!adjusting && top + height > limit && height > limit - floor && quoteScale > 0.78f) {
            adjusting = true;
            quoteScale *= 0.9f;
            float size = baseQuoteSize * quoteScale;
            quoteView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            applyQuoteText(size);
            adjusting = false;
        }
    }

    /** 引文可用宽度，留 3% 余量：自己量出来的行宽只要比 TextView 内部换行宽半点，就会又冒出一个孤字 */
    private float quoteWidth() {
        int width = rootView != null && rootView.getWidth() > 0
                ? rootView.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int padding = quoteLayout.getPaddingLeft() + quoteLayout.getPaddingRight();
        return Math.max(60f, (width - padding) * 0.97f);
    }

    /**
     * 按「句读」断行：优先在逗号、句号处换行，让每行都以标点收尾，凑不成整句时再退一步做均衡分配，
     * 保证末行不会只剩一两个字（诗的二、三句话各自成行）。
     */
    private Laid layoutQuote(String source, float size) {
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
                    appendParagraph(display, map, source.substring(from, i), from, paint, size, avail);
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
                                 Paint paint, float size, float avail) {
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
        int base = greedyLineCount(widths, limit);
        int maxLines = paragraph.length() <= 40 ? base + 1 : base;
        int[] breaks = null;
        double bestCost = Double.MAX_VALUE;
        for (int lines = base; lines <= maxLines; lines++) {
            double[] picked = new double[1];
            int[] candidate = dpBreaks(widths, clauseEnd, limit, lines, picked);
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
        int start = 0;
        for (int line = 0; line < breaks.length; line++) {
            int end = breaks[line];
            if (display.length() > 0) {
                display.append('\n');
                map.add(-1);
            }
            for (int i = start; i < end; i++) {
                String cluster = clusters.get(i);
                int base0 = starts.get(i);
                for (int k = 0; k < cluster.length(); k++) {
                    display.append(cluster.charAt(k));
                    map.add(base0 + k);
                }
            }
            start = end;
        }
    }

    /**
     * 在固定行数下找最优断点：行宽尽量均匀（贪心会把字一股脑堆在前几行，剩下孤零零一两个字），
     * 优先断在标点处，末行不足三个字要罚。
     */
    private static int[] dpBreaks(double[] widths, boolean[] clauseEnd, double limit, int lines,
                                  double[] costOut) {
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
                    if (width > limit + 0.001) {
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
        applyLaid(layoutQuote(quoteText, size));
    }

    private void applyLaid(Laid laid) {
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
                }
            }
        }
        quoteView.setText(spannable);
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
