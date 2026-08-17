package dev.openkeyboard.ioskeyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class T9PinyinDecoder {
    static final Result EMPTY = new Result("", Collections.emptyList());

    private static final List<String> PINYIN_SYLLABLES = Collections.unmodifiableList(Arrays.asList(
            "a", "ai", "an", "ang", "ao",
            "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
            "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
            "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
            "e", "ei", "en", "eng", "er",
            "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
            "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv",
            "m", "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            "n", "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nuo", "nv",
            "o", "ou",
            "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
            "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
            "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
            "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
            "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
            "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
            "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    ));

    private final Map<String, List<String>> syllablesByDigits = new HashMap<>();

    T9PinyinDecoder() {
        for (String syllable : PINYIN_SYLLABLES) {
            String digits = toDigits(syllable);
            List<String> list = syllablesByDigits.get(digits);
            if (list == null) {
                list = new ArrayList<>();
                syllablesByDigits.put(digits, list);
            }
            list.add(syllable);
        }
        prefer("64", "ni", "mi", "m", "n");
        prefer("426", "hao", "gan", "gao");
    }

    Result decode(String tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return EMPTY;
        }
        String cleaned = clean(tokens);
        if (cleaned.isEmpty()) {
            return EMPTY;
        }

        String[] parts = cleaned.split("'", -1);
        List<String> displayParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            displayParts.addAll(bestSegmentation(part));
        }
        String display = join(displayParts);
        if (display.isEmpty()) {
            display = cleaned.replace('\'', ' ');
        }
        return new Result(display, pinyinAlternatives(cleaned, display));
    }

    private List<String> bestSegmentation(String digits) {
        List<String> best = segment(digits, 0, new HashMap<>());
        if (best.isEmpty()) {
            List<String> initials = preferredInitialsForDigit(digits);
            if (!initials.isEmpty()) {
                return Collections.singletonList(initials.get(0));
            }
            return Collections.singletonList(digits);
        }
        return best;
    }

    private List<String> segment(String digits, int start, Map<Integer, List<String>> memo) {
        if (start >= digits.length()) {
            return Collections.emptyList();
        }
        List<String> cached = memo.get(start);
        if (cached != null) {
            return cached;
        }
        List<String> best = Collections.emptyList();
        for (int end = Math.min(digits.length(), start + 6); end > start; end--) {
            String token = digits.substring(start, end);
            List<String> syllables = syllablesByDigits.get(token);
            if (syllables == null || syllables.isEmpty()) {
                continue;
            }
            String syllable = syllables.get(0);
            if (end == digits.length()) {
                best = Collections.singletonList(syllable);
                break;
            }
            List<String> rest = segment(digits, end, memo);
            if (!rest.isEmpty()) {
                List<String> candidate = new ArrayList<>();
                candidate.add(syllable);
                candidate.addAll(rest);
                if (best.isEmpty() || score(candidate) > score(best)) {
                    best = candidate;
                }
            }
        }
        memo.put(start, best);
        return best;
    }

    private List<String> pinyinAlternatives(String cleaned, String display) {
        Set<String> result = new LinkedHashSet<>();
        String firstPart = cleaned.split("'", -1)[0];
        for (int len = Math.min(firstPart.length(), 6); len >= 1 && result.size() < 8; len--) {
            List<String> exact = syllablesByDigits.get(firstPart.substring(0, len));
            if (exact != null) {
                result.addAll(exact);
            }
        }
        if (result.isEmpty()) {
            result.addAll(preferredInitialsForDigit(firstPart));
        }
        return new ArrayList<>(result);
    }

    private List<String> preferredInitialsForDigit(String digits) {
        if (digits == null || digits.length() != 1) {
            return Collections.emptyList();
        }
        switch (digits.charAt(0)) {
            case '2':
                return Arrays.asList("a", "b", "c");
            case '3':
                return Arrays.asList("e", "d", "f");
            case '4':
                return Arrays.asList("h", "g", "i");
            case '5':
                return Arrays.asList("j", "l", "k");
            case '6':
                return Arrays.asList("n", "m", "o");
            case '7':
                return Arrays.asList("s", "q", "r", "p");
            case '8':
                return Arrays.asList("t", "u", "v");
            case '9':
                return Arrays.asList("w", "x", "y", "z");
            default:
                return Collections.emptyList();
        }
    }

    private int score(List<String> syllables) {
        int score = 0;
        for (String syllable : syllables) {
            score += syllable.length() * 10;
        }
        score -= Math.max(0, syllables.size() - 1) * 2;
        return score;
    }

    private void prefer(String digits, String... ordered) {
        List<String> existing = syllablesByDigits.get(digits);
        if (existing == null) {
            existing = new ArrayList<>();
            syllablesByDigits.put(digits, existing);
        }
        List<String> merged = new ArrayList<>();
        Collections.addAll(merged, ordered);
        for (String item : existing) {
            if (!merged.contains(item)) {
                merged.add(item);
            }
        }
        syllablesByDigits.put(digits, merged);
    }

    private String clean(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean previousSeparator = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '2' && c <= '9') {
                builder.append(c);
                previousSeparator = false;
            } else if (c == '\'' && builder.length() > 0 && !previousSeparator) {
                builder.append(c);
                previousSeparator = true;
            }
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\'') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    private static String join(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private static String toDigits(String syllable) {
        String lower = syllable.toLowerCase(Locale.US);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            builder.append(letterToDigit(lower.charAt(i)));
        }
        return builder.toString();
    }

    static String digitsForPinyin(String pinyin) {
        if (pinyin == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(pinyin.length());
        String lower = pinyin.toLowerCase(Locale.US);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                builder.append(letterToDigit(c));
            }
        }
        return builder.toString();
    }

    private static char letterToDigit(char c) {
        if (c >= 'a' && c <= 'c') {
            return '2';
        }
        if (c >= 'd' && c <= 'f') {
            return '3';
        }
        if (c >= 'g' && c <= 'i') {
            return '4';
        }
        if (c >= 'j' && c <= 'l') {
            return '5';
        }
        if (c >= 'm' && c <= 'o') {
            return '6';
        }
        if (c >= 'p' && c <= 's') {
            return '7';
        }
        if (c >= 't' && c <= 'v') {
            return '8';
        }
        return '9';
    }

    static final class Result {
        final String bestDisplayText;
        final List<String> pinyinCandidates;

        Result(String bestDisplayText, List<String> pinyinCandidates) {
            this.bestDisplayText = bestDisplayText == null ? "" : bestDisplayText;
            this.pinyinCandidates = pinyinCandidates == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(pinyinCandidates));
        }
    }
}
