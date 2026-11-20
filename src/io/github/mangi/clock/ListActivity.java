package io.github.mangi.clock;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ListActivity extends Activity {
    // 颜色跟随设置页的深色/浅色开关。默认值就是深色，
    // 这样即使 applyTheme 之前出了异常，界面也不会变成透明字
    private int BACKGROUND_COLOR = Color.rgb(17, 17, 20);
    private int TITLE_COLOR = Color.rgb(238, 234, 226);
    private int BODY_COLOR = Color.rgb(214, 209, 200);
    private int HINT_COLOR = Color.rgb(120, 116, 110);
    private int DIVIDER_COLOR = Color.rgb(34, 34, 38);

    private SharedPreferences preferences;
    private String type = "liked";
    private LinearLayout listLayout;
    private TextView emptyView;
    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            preferences = getSharedPreferences("clock", MODE_PRIVATE);
            applyTheme();
            getWindow().setStatusBarColor(BACKGROUND_COLOR);
            // 浅色底下用深色图标，否则白图标贴在米白底上看不见
            if (!isDark()) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
            String requestedType = getIntent().getStringExtra("type");
            type = "blocked".equals(requestedType) ? "blocked" : "liked";
            buildUi();
        } catch (Throwable ignored) {
            showFallback();
        }
    }

    private boolean isDark() {
        return preferences == null || preferences.getBoolean("dark", true);
    }

    private void applyTheme() {
        boolean dark = preferences == null || preferences.getBoolean("dark", true);
        if (dark) {
            BACKGROUND_COLOR = Color.rgb(17, 17, 20);
            TITLE_COLOR = Color.rgb(238, 234, 226);
            BODY_COLOR = Color.rgb(214, 209, 200);
            HINT_COLOR = Color.rgb(120, 116, 110);
            DIVIDER_COLOR = Color.rgb(34, 34, 38);
        } else {
            BACKGROUND_COLOR = Color.rgb(245, 242, 234);
            TITLE_COLOR = Color.rgb(30, 28, 25);
            BODY_COLOR = Color.rgb(75, 71, 64);
            HINT_COLOR = Color.rgb(141, 134, 121);
            DIVIDER_COLOR = Color.rgb(228, 222, 209);
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

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND_COLOR);
        root.setPadding(72, 48, 72, 32);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = textView("‹", 52, TITLE_COLOR);
        back.setContentDescription("返回");
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

        TextView heading = textView("blocked".equals(type) ? "屏蔽名单" : "喜欢名单",
                48, TITLE_COLOR);
        bar.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        countView = textView("", 26, HINT_COLOR);
        bar.addView(countView);
        root.addView(bar);

        TextView note = textView("blocked".equals(type)
                ? "这里的句子不会再出现"
                : "喜欢这里的句子，它会更常出现在钟面上", 26, HINT_COLOR);
        note.setPadding(0, 18, 0, 0);
        root.addView(note);

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER_COLOR);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = 28;
        root.addView(divider, dividerParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        content.addView(listLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        emptyView = textView("", 30, HINT_COLOR);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, 160, 0, 160);
        content.addView(emptyView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView tip = textView("长按句子可移除", 24, HINT_COLOR);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 24, 0, 0);
        root.addView(tip);

        setContentView(root);
        refresh();
    }

    private TextView textView(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        view.setTextColor(color);
        return view;
    }

    /** 读取名单；出错时返回 null（区别于「确实是空名单」），由调用方提示 */
    private List<String> loadItems() {
        try {
            List<String> items = new ArrayList<>();
            Set<String> saved = preferences.getStringSet(type,
                    Collections.<String>emptySet());
            if (saved != null) {
                for (String sentence : saved) {
                    if (sentence != null) {
                        items.add(sentence);
                    }
                }
            }
            Collections.sort(items);
            return items;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void refresh() {
        try {
            List<String> items = loadItems();
            if (items == null) {
                // 读不出来 ≠ 空名单，别让用户以为数据没了
                countView.setText("");
                listLayout.removeAllViews();
                emptyView.setText("名单读取失败，请稍后重试");
                emptyView.setVisibility(View.VISIBLE);
                return;
            }
            countView.setText(items.size() + " 句");
            listLayout.removeAllViews();
            emptyView.setText("blocked".equals(type)
                    ? "还没有屏蔽的句子" : "还没有喜欢的句子");
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

            for (int i = 0; i < items.size(); i++) {
                final String sentence = items.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, 34, 0, 34);

                TextView text = textView(sentence, 32, BODY_COLOR);
                text.setLineSpacing(0f, 1.35f);
                row.addView(text);
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        try {
                            new android.app.AlertDialog.Builder(ListActivity.this)
                                    .setTitle("移除这句？")
                                    .setMessage(sentence)
                                    .setPositiveButton("移除", new android.content.DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(android.content.DialogInterface dialog, int which) {
                                            try {
                                                removeSentence(sentence);
                                            } catch (Throwable ignored) {
                                                showToast("移除失败，请稍后重试");
                                            }
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        } catch (Throwable ignored) {
                            showToast("移除失败，请稍后重试");
                        }
                        return true;
                    }
                });
                listLayout.addView(row);

                if (i < items.size() - 1) {
                    View separator = new View(this);
                    separator.setBackgroundColor(DIVIDER_COLOR);
                    listLayout.addView(separator, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                }
            }
        } catch (Throwable ignored) {
            showListError();
        }
    }

    private void removeSentence(String sentence) {
        try {
            Set<String> saved = preferences.getStringSet(type,
                    Collections.<String>emptySet());
            HashSet<String> updated = new HashSet<>();
            if (saved != null) {
                updated.addAll(saved);
            }
            if (updated.remove(sentence)) {
                preferences.edit().putStringSet(type, updated).apply();
                showToast("已移除");
            }
            refresh();
        } catch (Throwable ignored) {
            showToast("移除失败，请稍后重试");
            refresh();
        }
    }

    private void showToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void showListError() {
        try {
            listLayout.removeAllViews();
            countView.setText("");
            emptyView.setText("名单显示失败，请稍后重试");
            emptyView.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {
            showFallback();
        }
    }

    private void showFallback() {
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(BACKGROUND_COLOR);
            root.setPadding(72, 48, 72, 48);
            TextView message = textView("页面暂时无法显示，请稍后重试", 30, HINT_COLOR);
            message.setGravity(Gravity.CENTER);
            root.addView(message);
            setContentView(root);
        } catch (Throwable ignored) {
        }
    }
}
