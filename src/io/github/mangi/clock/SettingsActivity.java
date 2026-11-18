package io.github.mangi.clock;

import android.app.Activity;
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
    private final GradientDrawable[] swatches = new GradientDrawable[BACKGROUND_KEYS.length];

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

        // 小屏内容会超出屏幕，整页可滚；底部「返回时钟」就不会被切掉
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor);
        root.setPadding(64, 48, 64, 32);

        TextView title = textView("文学时钟", 68, titleColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 20);

        root.addView(textView("在时钟页面上滑进这里，点一下退出时钟", 28, hintColor),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 48);

        root.addView(createAppearanceRow());
        addSpace(root, 40);

        root.addView(sectionTitle("背景色"));
        addSpace(root, 28);
        root.addView(createBackgroundChoices());
        addSpace(root, 20);
        root.addView(textView("墨水屏：纯白底纯黑字，不做彩色强调", 26, hintColor));
        addSpace(root, 36);

        root.addView(sectionTitle("亮屏时长"));
        addSpace(root, 16);
        TextView timeoutNote = textView(
                "到点自动退出时钟页，之后由系统决定何时息屏（常亮则一直亮着）", 26, hintColor);
        timeoutNote.setLineSpacing(0f, 1.3f);
        root.addView(timeoutNote, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 20);
        root.addView(createTimeoutChoices());
        addSpace(root, 32);

        Button back = new Button(this);
        back.setText("返回时钟");
        back.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32);
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
        TextView author = textView("作者 Kris", 26, hintColor);
        author.setGravity(Gravity.CENTER);
        root.addView(author, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpace(root, 8);
        TextView version = textView("版本 " + versionName(), 26, hintColor);
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
            titleColor = Color.rgb(238, 234, 226);
            bodyColor = Color.rgb(238, 234, 226);
            hintColor = Color.rgb(120, 116, 110);
            strokeOffColor = Color.rgb(51, 51, 56);
        } else {
            backgroundColor = Color.rgb(246, 244, 239);
            titleColor = Color.rgb(27, 26, 24);
            bodyColor = Color.rgb(38, 36, 31);
            hintColor = Color.rgb(139, 133, 122);
            strokeOffColor = Color.rgb(216, 210, 198);
        }
    }

    private View createAppearanceRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionTitle("外观");
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(createDarkSwitch(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View createDarkSwitch() {
        Switch toggle = new Switch(this);
        toggle.setText("深色模式");
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30);
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
            choice.addView(circle, new LinearLayout.LayoutParams(96, 96));

            TextView name = textView(BACKGROUND_NAMES[i], 28, bodyColor);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = 18;
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
            option.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
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
        return textView(text, 36, titleColor);
    }

    private TextView textView(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        view.setTextColor(color);
        return view;
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
