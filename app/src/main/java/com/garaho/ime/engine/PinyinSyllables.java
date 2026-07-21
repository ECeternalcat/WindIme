package com.garaho.ime.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical Mandarin pinyin syllable table (tone-less) plus T9 digit encoding.
 *
 * <p>The syllable set is the closed vocabulary the segmenter accepts - any
 * split of a digit string whose chunks each decode to a valid syllable is a
 * candidate pinyin phrase. 'ü' is represented as {@code v} (keyboard/input
 * convention); on the T9 keypad it shares the {@code 8} key with {@code u} so
 * both spellings collapse to the same code automatically.
 */
public final class PinyinSyllables {

    private static final String[] SYLLABLE_ARRAY = {
            "a", "ai", "an", "ang", "ao",
            "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao",
            "bie", "bin", "bing", "bo", "bu",
            "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan",
            "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chuai",
            "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan",
            "cui", "cun", "cuo",
            "da", "dai", "dan", "dang", "dao", "de", "den", "deng", "di", "dia", "dian",
            "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
            "e", "ei", "en", "eng", "er",
            "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou",
            "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou",
            "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu",
            "ju", "juan", "jue", "jun",
            "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou",
            "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian",
            "liang", "liao", "lie", "lin", "ling", "liu", "long", "lou", "lu", "luan",
            "lun", "luo", "lv", "lve",
            "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian",
            "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian",
            "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan",
            "nun", "nuo", "nv", "nve",
            "o", "ou",
            "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao",
            "pie", "pin", "ping", "po", "pou", "pu",
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu",
            "qu", "quan", "que", "qun",
            "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "rua",
            "ruan", "rui", "run", "ruo",
            "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan",
            "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua",
            "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su",
            "suan", "sui", "sun", "suo",
            "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie",
            "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
            "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu",
            "xu", "xuan", "xue", "xun",
            "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you",
            "yu", "yuan", "yue", "yun",
            "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai",
            "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou",
            "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong",
            "zou", "zu", "zuan", "zui", "zun", "zuo",
    };

    private static final int[] LETTER_DIGIT = new int[128];

    static {
        String[] groups = {"abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
        for (int i = 0; i < groups.length; i++) {
            int digit = '2' + i;
            for (int j = 0; j < groups[i].length(); j++) {
                LETTER_DIGIT[groups[i].charAt(j)] = digit;
            }
        }
        LETTER_DIGIT['v'] = '8';
    }

    private static final Set<String> SYLLABLES;
    private static final Map<String, java.util.List<String>> T9_TO_SYLLABLES;

    static {
        Set<String> s = new HashSet<>(SYLLABLE_ARRAY.length * 2);
        Map<String, LinkedHashSet<String>> t9 = new HashMap<>();
        for (String raw : SYLLABLE_ARRAY) {
            s.add(raw);
            String code = t9Encode(raw);
            LinkedHashSet<String> bucket = t9.get(code);
            if (bucket == null) {
                bucket = new LinkedHashSet<>();
                t9.put(code, bucket);
            }
            bucket.add(raw);
        }
        SYLLABLES = Collections.unmodifiableSet(s);
        Map<String, java.util.List<String>> frozen = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : t9.entrySet()) {
            frozen.put(e.getKey(),
                    Collections.unmodifiableList(new java.util.ArrayList<>(e.getValue())));
        }
        T9_TO_SYLLABLES = Collections.unmodifiableMap(frozen);
    }

    private PinyinSyllables() {
    }

    public static Set<String> syllables() {
        return SYLLABLES;
    }

    public static boolean isSyllable(String s) {
        return SYLLABLES.contains(s);
    }

    public static String t9Encode(String letters) {
        StringBuilder sb = new StringBuilder(letters.length());
        for (int i = 0; i < letters.length(); i++) {
            char c = letters.charAt(i);
            int d = (c < LETTER_DIGIT.length) ? LETTER_DIGIT[c] : 0;
            if (d != 0) {
                sb.append((char) d);
            }
        }
        return sb.toString();
    }

    /**
     * @return all valid pinyin syllables whose T9 code equals {@code code};
     *         never {@code null}, empty when unknown.
     */
    public static java.util.List<String> syllablesForT9(String code) {
        java.util.List<String> r = T9_TO_SYLLABLES.get(code);
        return r == null ? java.util.Collections.<String>emptyList() : r;
    }

    /**
     * @return distinct T9 code lengths a single syllable can have (e.g.
     *         {@code 1..4}); used to bound the segmenter's branching factor.
     */
    public static int[] syllableT9Lengths() {
        return new int[]{1, 2, 3, 4, 5, 6};
    }
}
