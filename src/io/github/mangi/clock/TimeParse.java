package io.github.mangi.clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParse {
    private static final String NUM = "零〇一二三四五六七八九十两\\d";
    private static final Pattern DIGITAL = Pattern.compile(
            "(?<![\\d:])((?:[01]?\\d|2[0-3]))[:：]([0-5]\\d)(?::[0-5]\\d)?(?![\\d:])");
    private static final Pattern BEFORE = Pattern.compile(
            "差([" + NUM + "]{1,3})分([" + NUM + "]{1,3})点");
    private static final Pattern AFTER = Pattern.compile(
            "([" + NUM + "]{1,3})点差([" + NUM + "]{1,3})分");
    private static final Pattern CLOCK = Pattern.compile(
            "(?:凌晨|清早|清晨|早晨|早上|上午|正午|中午|下午|午后|傍晚|晚上|夜里|夜间|午夜|半夜)?"
                    + "([" + NUM + "]{1,3})点(半钟|半|一刻|二刻|三刻|([" + NUM + "]{1,3})分?|整|丁|钟)?");
    private static final Pattern SHICHEN_QUARTER = Pattern.compile(
            "(子|丑|寅|卯|辰|巳|午|未|申|酉|戌|亥)(?:时|正|初)([一二三])刻");
    private static final Pattern PM = Pattern.compile("下午|午后|傍晚|晚上|夜里|夜间");
    private static final Pattern MIDNIGHT = Pattern.compile("午夜|半夜");

    /**
     * 「十三点六加仑」「三点五公里」——点后面跟的是数量单位时不是钟点。
     * 不含「分」（四点二十分是时间）、不含「刻/半」（三点半是时间）。
     */
    private static final String CLOCK_UNITS =
            "加仑公里千米米公斤斤克磅英里倍成尺寸丈元角块年岁次个件条张只匹头杯碗盘碟份";

    private static final class Candidate {
        String h;
        int start;
        int priority;
        double a;
        double b;

        Candidate(String h, int start, int priority, double a, double b) {
            this.h = h;
            this.start = start;
            this.priority = priority;
            this.a = a;
            this.b = b;
        }
    }

    private static final class Rule {
        final Pattern pattern;
        final int priority;
        final double a;
        final double b;

        Rule(String regex, int priority, double a, double b) {
            this.pattern = Pattern.compile(regex);
            this.priority = priority;
            this.a = a;
            this.b = b;
        }
    }

    private static final Rule[] RULES = {
            new Rule("平明", 115, 4, 7),
            new Rule("(?<!五)月落|落月", 90, 3, 6),
            new Rule("初更(?!帅)(?:时分|鼓打)?|更定|(?<!一)定更|戍鼓", 110, 19, 21),
            new Rule("人定初", 110, 21, 23),
            new Rule("更深|漏断|春宵|宵中|月黑|三星在天", 65, 19, 30),
            new Rule("月明|孤月|野月|月转廊|素月|山月|秋月|秋影|月亮", 60, 19, 30),
            new Rule("晨|晨起|东方未明|东方将白|霜晨|晨曦|朝辞|早莺|晨兴|朝雨|朝烟|明朝|朝饮|今晨|雪朝|终朝|朝槿|天色微明|到明|暾将出", 110, 4, 9),
            new Rule("午饭|午阴|午梦|午醉|午热|午暑|午凉|午余|午睡|午窗", 100, 11, 14),
            new Rule("午转", 100, 11, 13),
            new Rule("夕照|西落|落晖|夕晖|夕舂|山暝|残照|落霞|斜日|日夕|白日依山尽|白日曛|返景", 95, 16, 20),
            new Rule("秋光", 60, 19, 30),
            new Rule("(?<!种)子时|子牌|子正|午夜", 110, 23, 25),
            new Rule("夜半", 115, 23, 25),
            new Rule("丑时|丑牌|丑正", 110, 1, 3),
            new Rule("寅时|寅牌|寅正", 110, 3, 5),
            new Rule("卯时|卯牌|卯正|点卯", 110, 5, 7),
            new Rule("辰时|辰牌|辰正", 110, 7, 9),
            new Rule("巳时|巳牌|巳正", 110, 9, 11),
            new Rule("午时|午牌|午正|午初|日午|亭午|正午", 110, 11, 13),
            new Rule("未时|未牌|未正|未初", 110, 13, 15),
            new Rule("申时|申牌|申正|申初", 110, 15, 17),
            new Rule("酉时|酉牌|酉正|酉初", 110, 17, 19),
            new Rule("戌时|戌牌|戌正", 110, 19, 21),
            new Rule("亥时|亥牌|亥正", 110, 21, 23),
            new Rule("一更|一鼓", 110, 19, 21),
            new Rule("二更|二鼓", 110, 21, 23),
            new Rule("三更|三鼓", 110, 23, 25),
            new Rule("四更|四鼓", 110, 25, 27),
            new Rule("五更|五鼓", 110, 27, 29),
            new Rule("凌晨", 115, 0, 6),
            new Rule("黎明|拂晓|天亮", 110, 4, 7),
            new Rule("清晨|早晨|早上", 110, 4, 9),
            new Rule("上午", 115, 8, 11),
            new Rule("晌午|中午", 115, 11, 14),
            new Rule("午后|下午", 115, 12, 17),
            new Rule("傍晚|薄暮|日暮|黄昏|暮色|日薄西山", 115, 17, 20),
            new Rule("落日(?!月)|夕阳|日落|日西|日沉", 100, 16.5, 20),
            new Rule("斜阳|残阳|斜晖|阳斜", 100, 16, 20),
            new Rule("夜里|夜间|深夜|夜久|夜色|月夜|此夜|昨夜", 70, 19, 30),
            new Rule("晡时|日晡|晡(?!后)", 100, 15, 17),
            new Rule("晡后", 100, 17, 20),
            new Rule("明月|月出|月下|月斜|斜月|月色|月光", 60, 19, 30),
            new Rule("日出|曙光|晨光|晨星|晓雾|晓星沉|侵晓|昧旦|鸡鸣|鸡声|鸡既鸣|东方欲晓|欲曙|朝露", 100, 4, 8),
            new Rule("终日", 80, 8, 17),
            new Rule("昼夜", 80, 0, 1440),
            new Rule("(?<!一)日长|夏日长|昼出|昼", 50, 8, 17),
            new Rule("日中|日当午|午钟", 100, 11, 13),
            new Rule("日高", 90, 9, 12),
            new Rule("日永|迟日|暖日|赤日|出日", 90, 9, 16),
            new Rule("日斜|日之夕|日头平西|日头偏西", 95, 16, 19),
            new Rule("晚来|天色已晚|天色将晚", 95, 17, 21),
            new Rule("夕殿|暮雨", 95, 17, 21),
            new Rule("昏定", 100, 19, 21),
            new Rule("灯如昼|元夜|元宵|掌灯|灯|烛", 55, 18, 24),
            new Rule("日昃|日昝|日昳", 100, 13, 15),
            new Rule("半夜", 115, 23, 25),
            new Rule("晓", 110, 4, 9),
            new Rule("(?<!公)子夜|夜中|中宵|月午", 110, 23, 25),
            new Rule("今夜|入夜|夜晚|夜来|夜静|秋夜|黑夜|莫夜|长夜|夜阑|夜未央|夜雨|夜夜|夜深人静|三五之夜|明月夜|夜|宵", 70, 19, 30),
            new Rule("向晚", 100, 17, 21),
            new Rule("斜照", 95, 16, 20),
            new Rule("暮", 110, 17, 20),
            new Rule("晚上", 115, 17, 21),
            new Rule("晚", 100, 17, 21),
            new Rule("朝(?!廷|代|野|臣|贡|拜|见|政|奏)", 100, 4, 9),
            new Rule("旦", 115, 4, 7),
            new Rule("(?<!昏)昏(?!昏)", 100, 17, 21),
            new Rule("天明", 110, 4, 7),
            new Rule("清早", 110, 5, 9),
            new Rule("上半天", 100, 6, 12),
            new Rule("晚饭", 100, 17, 21),
            new Rule("式微|桂魄初生", 90, 17, 21),
            new Rule("月上柳梢|月亮升起|孤月上", 90, 17, 27),
            new Rule("(?<!阳)(?<!照)(?<!晖)(?<!舂)(?<!殿)夕", 105, 16, 20)
    };

    private TimeParse() {
    }

    public static final class Result {
        public boolean hasTime;
        public String h;
        public String time;
        public List<String> seg;
    }

    public static Result parse(String text) {
        Result result = new Result();
        if (text == null || text.length() == 0) {
            return result;
        }
        if (text.contains("十二下")) {
            return exact(result, "十二下", 0);
        }
        Matcher quarter = SHICHEN_QUARTER.matcher(text);
        if (quarter.find()) {
            int hour = branchHour(quarter.group(1));
            int minute = (quarter.group(2).charAt(0) - '一') * 15;
            return exact(result, quarter.group(), (hour * 60 + minute) % 1440);
        }
        Matcher digital = DIGITAL.matcher(text);
        if (digital.find()) {
            int minutes = Integer.parseInt(digital.group(1)) * 60
                    + Integer.parseInt(digital.group(2));
            return exact(result, digital.group(), minutes);
        }
        Matcher before = BEFORE.matcher(text);
        if (before.find()) {
            int delta = cn2int(before.group(1));
            int hour = cn2int(before.group(2));
            if (delta > 0 && delta < 60 && hour <= 23) {
                return exact(result, before.group(), (hour * 60 - delta + 1440) % 1440);
            }
        }
        Matcher after = AFTER.matcher(text);
        if (after.find()) {
            int hour = cn2int(after.group(1));
            int delta = cn2int(after.group(2));
            if (delta > 0 && delta < 60 && hour <= 23) {
                return exact(result, after.group(), (hour * 60 - delta + 1440) % 1440);
            }
        }
        Matcher clock = CLOCK.matcher(text);
        while (clock.find()) {
            int hour = cn2int(clock.group(1));
            String value = clock.group(2);
            // 「十三点六加仑」这种：点后面紧跟数量单位，且后缀不是明确时分写法，就不是钟点
            if (clock.end() < text.length()
                    && CLOCK_UNITS.indexOf(text.charAt(clock.end())) >= 0) {
                boolean explicitMinute = value != null
                        && (value.endsWith("分") || "半".equals(value) || "半钟".equals(value)
                        || "一刻".equals(value) || "二刻".equals(value) || "三刻".equals(value));
                if (!explicitMinute) {
                    continue;
                }
            }
            int minute = 0;
            if ("半".equals(value) || "半钟".equals(value) || "二刻".equals(value)) {
                minute = 30;
            } else if ("一刻".equals(value)) {
                minute = 15;
            } else if ("三刻".equals(value)) {
                minute = 45;
            } else if (clock.group(3) != null) {
                minute = cn2int(clock.group(3));
            }
            if (hour <= 23 && minute < 60) {
                String matched = clock.group();
                String prefix = text.substring(Math.max(0, clock.start() - 16), clock.start());
                String context = prefix + matched;
                if (MIDNIGHT.matcher(context).find() && hour == 12) {
                    hour = 0;
                } else if (PM.matcher(context).find() && hour >= 1 && hour <= 11) {
                    hour += 12;
                }
                return exact(result, matched, hour * 60 + minute);
            }
        }
        return period(text, result);
    }

    private static Result exact(Result result, String h, int minutes) {
        minutes = (minutes % 1440 + 1440) % 1440;
        result.hasTime = true;
        result.h = h;
        result.time = two(minutes / 60) + ":" + two(minutes % 60);
        result.seg = Collections.singletonList(String.valueOf(minutes / 60));
        return result;
    }

    private static Result period(String text, Result result) {
        List<Candidate> candidates = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(text);
            while (matcher.find()) {
                candidates.add(new Candidate(matcher.group(), matcher.start(),
                        rule.priority, rule.a, rule.b));
            }
        }
        if (candidates.isEmpty()) {
            return result;
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate x, Candidate y) {
                if (x.priority != y.priority) {
                    return y.priority - x.priority;
                }
                if (x.start != y.start) {
                    return x.start - y.start;
                }
                return y.h.length() - x.h.length();
            }
        });
        Candidate chosen = candidates.get(0);
        String h = chosen.h;

        if (h.contains("三更四更")) {
            return windowResult(result, h, 23, 27);
        }
        if (h.contains("二三更")) {
            return windowResult(result, h, 21, 25);
        }
        if (h.contains("一更将尽")) {
            return windowResult(result, h, 20, 21);
        }
        if (h.contains("初更已过")) {
            return windowResult(result, h, 21, 23);
        }
        if (h.contains("二更向尽")) {
            return windowResult(result, h, 22, 23);
        }
        if (h.equals("亥正")) {
            return windowResult(result, h, 22, 23);
        }
        if (h.contains("三更后") || h.contains("三更过后")) {
            return windowResult(result, h, 1, 5);
        }
        if (h.contains("晡") && h.length() == 1) {
            return windowResult(result, h, 15, 17);
        }
        if (h.equals("当午") || h.equals("向午") || h.equals("午当阶")
                || h.equals("日移午") || h.equals("午欲亭") || h.equals("午昼")
                || h.equals("昼午") || h.equals("午")) {
            return windowResult(result, h, 11, 13);
        }
        if (h.equals("日色高")) {
            return windowResult(result, h, 9, 12);
        }
        if (h.equals("太阳刚一出来")) {
            return windowResult(result, h, 4, 8);
        }
        if (h.equals("夜方浅")) {
            return windowResult(result, h, 19, 23);
        }
        if (h.equals("月未午")) {
            return windowResult(result, h, 18, 24);
        }

        if (containsAny(h, "黎明", "天亮", "平明", "昧旦", "曙", "破晓", "东方白", "天明")) {
            return windowResult(result, h, 4, 7);
        }
        if (containsAny(h, "晓", "晨", "清早", "清晨", "早晨", "早上", "朝")) {
            return windowResult(result, h, 4, 9);
        }
        if (containsAny(h, "黄昏", "日暮", "薄暮", "傍晚", "夕阳", "日落", "落日", "暮")) {
            return windowResult(result, h, 17, 20);
        }
        if (containsAny(h, "正午", "中午", "晌午", "亭午")) {
            return windowResult(result, h, 11, 14);
        }
        if (h.contains("凌晨")) {
            return windowResult(result, h, 0, 6);
        }
        if (h.equals("晚上")) {
            return windowResult(result, h, 17, 21);
        }
        if (h.equals("夜") || h.equals("昨夜") || h.equals("夜里") || h.equals("夜间")
                || h.equals("夜深") || h.equals("深夜") || h.equals("夜色")
                || h.equals("今夜") || h.equals("入夜") || h.equals("夜晚")
                || h.equals("夜来") || h.equals("夜静") || h.equals("秋夜")
                || h.equals("黑夜") || h.equals("莫夜") || h.equals("长夜")
                || h.equals("夜阑") || h.equals("夜未央") || h.equals("夜雨")
                || h.equals("夜夜") || h.equals("夜深人静") || h.equals("三五之夜")
                || h.equals("明月夜") || h.equals("一夜") || h.equals("此夜")
                || h.equals("夜久") || h.equals("宵") || h.equals("春宵")
                || h.equals("宵中") || h.equals("更深") || h.equals("三星在天")) {
            return windowResult(result, h, 19, 30);
        }
        return windowResult(result, h, chosen.a, chosen.b);
    }

    private static Result windowResult(Result result, String h, double a, double b) {
        result.hasTime = true;
        result.h = h;
        if (a == 0 && b == 1440) {
            result.seg = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                result.seg.add(String.valueOf(i));
            }
            return result;
        }
        Set<String> hours = new HashSet<>();
        for (int i = (int) a; i < (int) b; i++) {
            hours.add(String.valueOf(i % 24));
        }
        result.seg = new ArrayList<>(hours);
        Collections.sort(result.seg, new Comparator<String>() {
            @Override
            public int compare(String x, String y) {
                return Integer.parseInt(x) - Integer.parseInt(y);
            }
        });
        return result;
    }

    private static int branchHour(String branch) {
        String branches = "子丑寅卯辰巳午未申酉戌亥";
        return (branches.indexOf(branch) * 2 + 23) % 24;
    }

    private static int cn2int(String value) {
        int current = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                current = current * 10 + c - '0';
            } else if (c == '十') {
                current = (current == 0 ? 1 : current) * 10;
            } else {
                int n = "零一二三四五六七八九".indexOf(c);
                if (n >= 0) {
                    current += n;
                } else if (c == '两') {
                    current += 2;
                }
            }
        }
        return current;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
