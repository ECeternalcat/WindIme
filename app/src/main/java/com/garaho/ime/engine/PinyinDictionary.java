package com.garaho.ime.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact embedded pinyin dictionary used by {@link T9PinyinEngine}.
 *
 * <p>Keys are pinyin phrases with syllables separated by {@code ' '}
 * (apostrophe): single characters use the bare syllable (e.g. {@code "ni"});
 * words concatenate syllables (e.g. {@code "ni'hao"}). Values are ordered by
 * descending frequency so the segmenter can present the most-likely candidate
 * first.
 *
 * <p>This ships as the Phase-1 fallback. Phase 4 swaps in the rime-ice-t9
 * binary dictionary via {@code librime.so}; the lookup surface stays identical.
 */
public final class PinyinDictionary {

    private static final Map<String, List<String>> ENTRIES;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        single(m, "a", "啊", "阿", "呵");
        single(m, "ai", "爱", "哎", "矮", "唉", "哀", "癌");
        single(m, "an", "安", "按", "案", "岸", "暗", "俺");
        single(m, "ang", "昂");
        single(m, "ao", "奥", "傲", "澳", "凹", "袄");
        single(m, "ba", "把", "吧", "爸", "八", "巴", "拔", "靶");
        single(m, "bai", "白", "百", "拜", "柏", "摆");
        single(m, "ban", "半", "办", "班", "般", "搬", "版", "板");
        single(m, "bang", "帮", "棒", "邦", "榜", "膀");
        single(m, "bao", "报", "包", "保", "宝", "抱", "暴", "薄");
        single(m, "bei", "被", "北", "背", "倍", "杯", "备", "辈");
        single(m, "ben", "本", "笨", "奔");
        single(m, "beng", "崩", "蹦", "绷");
        single(m, "bi", "比", "必", "笔", "毕", "避", "鼻", "逼", "币");
        single(m, "bian", "便", "变", "边", "编", "辩", "遍");
        single(m, "biao", "表", "标", "彪");
        single(m, "bie", "别", "憋", "瘪");
        single(m, "bin", "宾", "滨", "斌");
        single(m, "bing", "并", "病", "兵", "冰", "丙");
        single(m, "bo", "波", "播", "伯", "薄", "博", "拨");
        single(m, "bu", "不", "步", "部", "补", "布", "簿");

        single(m, "ca", "擦");
        single(m, "cai", "才", "菜", "财", "材", "裁", "采");
        single(m, "can", "参", "餐", "残", "惨", "灿");
        single(m, "cang", "仓", "藏", "苍");
        single(m, "cao", "草", "操", "曹");
        single(m, "ce", "册", "侧", "测", "策");
        single(m, "cen", "岑");
        single(m, "ceng", "层", "曾");
        single(m, "cha", "查", "茶", "插", "差", "察");
        single(m, "chai", "拆", "柴");
        single(m, "chan", "产", "禅", "颤", "缠");
        single(m, "chang", "长", "场", "常", "唱", "厂", "尝", "偿");
        single(m, "chao", "超", "朝", "吵", "炒", "潮", "抄");
        single(m, "che", "车", "彻", "撤");
        single(m, "chen", "陈", "晨", "臣", "沉", "趁", "衬");
        single(m, "cheng", "成", "称", "城", "程", "承", "乘", "诚");
        single(m, "chi", "吃", "池", "迟", "持", "尺", "齿", "赤");
        single(m, "chong", "充", "冲", "虫", "崇", "宠");
        single(m, "chou", "抽", "愁", "丑", "臭", "仇");
        single(m, "chu", "出", "处", "除", "初", "楚", "触", "厨");
        single(m, "chuai", "揣");
        single(m, "chuan", "传", "船", "穿", "喘", "串");
        single(m, "chuang", "创", "床", "窗", "闯");
        single(m, "chui", "吹", "垂", "锤", "炊");
        single(m, "chun", "春", "纯", "蠢", "唇");
        single(m, "chuo", "戳", "辍");
        single(m, "ci", "次", "此", "词", "刺", "慈", "磁", "辞");
        single(m, "cong", "从", "聪", "匆", "葱");
        single(m, "cou", "凑");
        single(m, "cu", "粗", "促", "醋", "簇");
        single(m, "cuan", "窜", "篡");
        single(m, "cui", "催", "脆", "翠", "崔");
        single(m, "cun", "村", "存", "寸");
        single(m, "cuo", "错", "措", "搓", "挫");

        single(m, "da", "大", "打", "达", "答", "搭");
        single(m, "dai", "代", "带", "待", "戴", "袋", "逮");
        single(m, "dan", "但", "单", "担", "蛋", "淡", "胆");
        single(m, "dang", "当", "党", "档", "挡");
        single(m, "dao", "到", "道", "导", "岛", "倒", "刀", "盗");
        single(m, "de", "的", "得", "德");
        single(m, "dei", "得");
        single(m, "den", "扽");
        single(m, "deng", "等", "灯", "登", "凳");
        single(m, "di", "第", "低", "地", "底", "弟", "滴", "帝", "敌");
        single(m, "dia", "嗲");
        single(m, "dian", "点", "电", "店", "典", "颠", "垫", "淀");
        single(m, "diao", "掉", "调", "钓", "吊", "叼");
        single(m, "die", "跌", "叠", "蝶", "谍");
        single(m, "ding", "定", "顶", "丁", "订", "钉");
        single(m, "diu", "丢");
        single(m, "dong", "东", "动", "懂", "洞", "冬", "董");
        single(m, "dou", "都", "斗", "豆", "抖", "陡");
        single(m, "du", "度", "独", "读", "毒", "督", "堵", "赌");
        single(m, "duan", "段", "短", "断", "端");
        single(m, "dui", "对", "队", "堆", "兑");
        single(m, "dun", "顿", "吨", "蹲", "盾");
        single(m, "duo", "多", "朵", "夺", "躲", "舵");

        single(m, "e", "饿", "额", "恶", "鹅", "俄", "蛾");
        single(m, "ei", "诶");
        single(m, "en", "恩", "嗯");
        single(m, "eng", "鞥");
        single(m, "er", "而", "二", "耳", "儿", "尔");

        single(m, "fa", "发", "法", "罚", "乏", "伐");
        single(m, "fan", "反", "饭", "犯", "烦", "凡", "繁", "返");
        single(m, "fang", "放", "方", "房", "防", "访", "纺");
        single(m, "fei", "非", "飞", "费", "废", "肥", "匪");
        single(m, "fen", "分", "份", "奋", "纷", "粉", "坟");
        single(m, "feng", "风", "封", "丰", "逢", "峰", "锋", "凤", "奉");
        single(m, "fo", "佛");
        single(m, "fou", "否");
        single(m, "fu", "福", "服", "父", "夫", "付", "负", "复", "府", "副");

        single(m, "ga", "嘎");
        single(m, "gai", "该", "改", "盖", "概", "溉");
        single(m, "gan", "干", "感", "敢", "甘", "杆", "肝");
        single(m, "gang", "刚", "岗", "钢", "纲", "港");
        single(m, "gao", "高", "告", "搞", "稿", "糕", "膏");
        single(m, "ge", "个", "哥", "歌", "格", "革", "隔", "阁");
        single(m, "gei", "给");
        single(m, "gen", "根", "跟", "艮");
        single(m, "geng", "更", "耕", "梗");
        single(m, "gong", "工", "公", "共", "功", "宫", "供", "弓");
        single(m, "gou", "够", "狗", "购", "构", "钩", "沟");
        single(m, "gu", "古", "故", "顾", "股", "骨", "鼓", "谷");
        single(m, "gua", "挂", "瓜", "刮", "寡");
        single(m, "guai", "怪", "乖", "拐");
        single(m, "guan", "关", "观", "管", "官", "馆", "灌", "惯");
        single(m, "guang", "光", "广", "逛");
        single(m, "gui", "贵", "规", "鬼", "桂", "柜", "归");
        single(m, "gun", "滚", "棍");
        single(m, "guo", "国", "过", "果", "锅", "裹");

        single(m, "ha", "哈");
        single(m, "hai", "还", "孩", "海", "害", "骇");
        single(m, "han", "汉", "寒", "韩", "含", "喊", "汗");
        single(m, "hang", "行", "航", "杭");
        single(m, "hao", "好", "号", "豪", "浩", "耗", "毫");
        single(m, "he", "和", "河", "喝", "合", "贺", "赫", "核");
        single(m, "hei", "黑", "嘿");
        single(m, "hen", "很", "恨", "痕", "狠");
        single(m, "heng", "横", "恒", "哼");
        single(m, "hong", "红", "宏", "洪", "虹", "轰");
        single(m, "hou", "后", "候", "厚", "侯", "喉");
        single(m, "hu", "户", "胡", "湖", "虎", "互", "护", "糊");
        single(m, "hua", "话", "花", "化", "华", "画", "滑");
        single(m, "huai", "坏", "怀", "淮");
        single(m, "huan", "还", "换", "环", "缓", "欢", "幻", "患");
        single(m, "huang", "黄", "荒", "慌", "皇", "凰", "煌");
        single(m, "hui", "会", "回", "灰", "辉", "汇", "慧", "惠");
        single(m, "hun", "混", "婚", "魂", "昏");
        single(m, "huo", "火", "或", "活", "货", "获", "祸");

        single(m, "ji", "几", "机", "及", "级", "极", "即", "集", "急", "计", "记", "季", "寄", "济", "继");
        single(m, "jia", "家", "加", "假", "价", "架", "佳", "嘉");
        single(m, "jian", "见", "间", "建", "简", "件", "键", "坚", "减", "检", "健");
        single(m, "jiang", "江", "将", "讲", "奖", "蒋", "匠", "降");
        single(m, "jiao", "教", "叫", "交", "脚", "角", "较", "觉", "焦");
        single(m, "jie", "家", "解", "姐", "介", "界", "结", "节", "杰", "接", "阶", "届", "借");
        single(m, "jin", "进", "金", "近", "今", "紧", "尽", "仅", "禁");
        single(m, "jing", "经", "京", "精", "境", "警", "静", "竞", "敬");
        single(m, "jiong", "窘");
        single(m, "jiu", "就", "九", "久", "旧", "酒", "救", "纠");
        single(m, "ju", "局", "据", "句", "聚", "具", "距", "巨", "剧");
        single(m, "juan", "卷", "捐", "倦", "圈");
        single(m, "jue", "觉", "决", "绝", "角", "爵");
        single(m, "jun", "军", "均", "君", "俊");

        single(m, "ka", "卡", "咖", "咯");
        single(m, "kai", "开", "凯", "慨");
        single(m, "kan", "看", "刊", "勘", "坎");
        single(m, "kang", "抗", "康", "慷", "炕");
        single(m, "kao", "考", "靠", "拷", "烤");
        single(m, "ke", "可", "课", "克", "客", "科", "颗", "刻", "棵");
        single(m, "kei", "剋");
        single(m, "ken", "肯", "恳", "啃");
        single(m, "keng", "坑");
        single(m, "kong", "空", "恐", "孔", "控");
        single(m, "kou", "口", "扣");
        single(m, "ku", "苦", "哭", "酷", "库", "枯");
        single(m, "kua", "跨", "夸", "垮");
        single(m, "kuai", "快", "块", "筷", "会");
        single(m, "kuan", "宽", "款");
        single(m, "kuang", "狂", "矿", "筐", "况");
        single(m, "kui", "亏", "溃", "愧");
        single(m, "kun", "困", "昆", "捆");
        single(m, "kuo", "扩", "括", "阔");

        single(m, "la", "拉", "啦", "蜡", "腊");
        single(m, "lai", "来", "赖", "莱");
        single(m, "lan", "蓝", "兰", "览", "懒", "烂", "栏");
        single(m, "lang", "浪", "狼", "郎", "朗");
        single(m, "lao", "老", "劳", "牢", "落", "姥");
        single(m, "le", "了", "乐", "勒");
        single(m, "lei", "累", "类", "雷", "泪", "垒");
        single(m, "leng", "冷");
        single(m, "li", "里", "理", "力", "立", "李", "例", "离", "利", "历", "厉", "丽");
        single(m, "lia", "俩");
        single(m, "lian", "连", "联", "练", "炼", "恋", "链", "廉");
        single(m, "liang", "两", "量", "凉", "梁", "良", "亮");
        single(m, "liao", "了", "料", "疗", "辽", "聊");
        single(m, "lie", "列", "烈", "猎", "裂", "劣");
        single(m, "lin", "林", "临", "邻", "磷", "淋");
        single(m, "ling", "领", "令", "灵", "龄", "凌", "铃", "岭");
        single(m, "liu", "六", "留", "流", "刘", "柳");
        single(m, "long", "龙", "笼", "隆", "弄");
        single(m, "lou", "楼", "漏", "搂");
        single(m, "lu", "路", "陆", "录", "鲁", "露", "炉", "鹿");
        single(m, "luan", "乱", "卵", "峦");
        single(m, "lun", "论", "轮", "伦");
        single(m, "luo", "落", "罗", "洛", "螺", "骆");
        single(m, "lv", "绿", "律", "率", "旅", "虑", "滤");
        single(m, "lve", "略", "掠");

        single(m, "ma", "吗", "妈", "马", "码", "骂", "麻");
        single(m, "mai", "买", "卖", "麦", "迈");
        single(m, "man", "满", "慢", "曼", "蛮", "瞒");
        single(m, "mang", "忙", "茫", "盲", "芒");
        single(m, "mao", "毛", "猫", "帽", "贸", "矛");
        single(m, "me", "么");
        single(m, "mei", "没", "美", "妹", "梅", "媒", "煤", "枚");
        single(m, "men", "们", "门", "闷");
        single(m, "meng", "梦", "猛", "蒙", "盟");
        single(m, "mi", "米", "密", "迷", "蜜", "秘", "靡");
        single(m, "mian", "面", "免", "棉", "绵", "勉");
        single(m, "miao", "秒", "苗", "妙", "描");
        single(m, "mie", "灭", "蔑");
        single(m, "min", "民", "敏", "闽");
        single(m, "ming", "明", "名", "鸣", "铭");
        single(m, "miu", "谬");
        single(m, "mo", "摩", "磨", "魔", "末", "莫", "墨", "默", "摸");
        single(m, "mou", "某", "谋");
        single(m, "mu", "母", "木", "目", "幕", "慕", "牧", "墓");

        single(m, "na", "那", "拿", "哪", "钠", "娜");
        single(m, "nai", "奶", "耐", "乃", "奈");
        single(m, "nan", "男", "难", "南", "楠");
        single(m, "nang", "囊");
        single(m, "nao", "闹", "脑", "恼", "挠");
        single(m, "ne", "呢");
        single(m, "nei", "内");
        single(m, "nen", "嫩");
        single(m, "neng", "能");
        single(m, "ni", "你", "尼", "拟", "逆", "腻", "妮", "泥");
        single(m, "nian", "年", "念", "念", "粘", "碾");
        single(m, "niang", "娘");
        single(m, "niao", "鸟", "尿");
        single(m, "nie", "捏", "聂", "孽");
        single(m, "nin", "您");
        single(m, "ning", "宁", "凝", "柠");
        single(m, "niu", "牛", "纽", "扭");
        single(m, "nong", "农", "弄", "浓");
        single(m, "nou", "耨");
        single(m, "nu", "努", "怒", "奴");
        single(m, "nuan", "暖");
        single(m, "nun", "黁");
        single(m, "nuo", "诺", "挪", "糯");
        single(m, "nv", "女");
        single(m, "nve", "虐");

        single(m, "o", "哦", "噢");
        single(m, "ou", "欧", "偶", "鸥", "藕");

        single(m, "pa", "怕", "爬", "趴", "帕");
        single(m, "pai", "排", "拍", "牌", "派");
        single(m, "pan", "盘", "判", "盼", "叛", "攀");
        single(m, "pang", "旁", "胖", "庞");
        single(m, "pao", "跑", "炮", "泡", "刨");
        single(m, "pei", "配", "陪", "培", "赔", "佩");
        single(m, "pen", "盆", "喷");
        single(m, "peng", "碰", "朋", "棚", "膨", "蓬");
        single(m, "pi", "批", "皮", "脾", "疲", "劈", "匹");
        single(m, "pian", "片", "偏", "骗", "篇");
        single(m, "piao", "票", "飘", "漂");
        single(m, "pie", "撇");
        single(m, "pin", "品", "贫", "频", "拼");
        single(m, "ping", "平", "评", "瓶", "凭", "苹");
        single(m, "po", "破", "坡", "泼", "婆", "迫");
        single(m, "pou", "剖");
        single(m, "pu", "普", "铺", "朴", "扑", "谱");

        single(m, "qi", "起", "其", "期", "七", "气", "齐", "妻", "奇", "骑", "棋", "旗");
        single(m, "qia", "卡", "掐", "恰");
        single(m, "qian", "前", "钱", "千", "签", "谦", "浅", "欠");
        single(m, "qiang", "强", "枪", "墙", "腔");
        single(m, "qiao", "桥", "瞧", "悄", "敲", "巧", "翘");
        single(m, "qie", "切", "且", "茄", "怯");
        single(m, "qin", "亲", "秦", "勤", "琴", "禽");
        single(m, "qing", "请", "清", "情", "青", "轻", "晴", "倾");
        single(m, "qiong", "穷", "琼");
        single(m, "qiu", "秋", "球", "求", "囚");
        single(m, "qu", "去", "区", "曲", "取", "趣", "驱", "渠");
        single(m, "quan", "全", "权", "劝", "圈", "犬");
        single(m, "que", "确", "缺", "却", "鹊");
        single(m, "qun", "群", "裙");

        single(m, "ran", "然", "燃");
        single(m, "rang", "让", "壤", "嚷");
        single(m, "rao", "饶", "绕", "扰");
        single(m, "re", "热", "惹");
        single(m, "ren", "人", "认", "任", "仁", "忍", "韧");
        single(m, "reng", "仍", "扔");
        single(m, "ri", "日");
        single(m, "rong", "容", "荣", "融", "溶", "蓉");
        single(m, "rou", "肉", "柔", "揉");
        single(m, "ru", "入", "如", "乳", "辱", "儒");
        single(m, "rua", "挼");
        single(m, "ruan", "软", "阮");
        single(m, "rui", "锐", "瑞", "蕊");
        single(m, "run", "润", "闰");
        single(m, "ruo", "若", "弱");

        single(m, "sa", "撒", "洒");
        single(m, "sai", "赛", "塞", "腮");
        single(m, "san", "三", "散", "伞");
        single(m, "sang", "桑", "嗓", "丧");
        single(m, "sao", "扫", "嫂", "骚");
        single(m, "se", "色", "涩");
        single(m, "sen", "森");
        single(m, "seng", "僧");
        single(m, "sha", "杀", "沙", "傻", "纱", "刹");
        single(m, "shai", "筛", "晒");
        single(m, "shan", "山", "善", "单", "闪", "删", "扇");
        single(m, "shang", "上", "商", "伤", "尚", "赏");
        single(m, "shao", "少", "烧", "绍", "哨", "勺");
        single(m, "she", "社", "设", "射", "蛇", "舍", "摄");
        single(m, "shei", "谁");
        single(m, "shen", "什", "神", "深", "身", "审", "申", "慎");
        single(m, "sheng", "生", "声", "升", "胜", "省", "盛", "圣");
        single(m, "shi", "是", "时", "十", "事", "实", "市", "使", "世", "师", "诗", "石", "史", "试", "视", "失");
        single(m, "shou", "手", "受", "收", "寿", "售", "兽");
        single(m, "shu", "书", "树", "数", "属", "署", "鼠", "输", "束");
        single(m, "shua", "刷", "耍");
        single(m, "shuai", "帅", "率", "摔", "甩");
        single(m, "shuan", "栓", "拴");
        single(m, "shuang", "双", "爽");
        single(m, "shui", "水", "说", "睡", "税");
        single(m, "shun", "顺", "瞬");
        single(m, "shuo", "说", "硕", "烁");
        single(m, "si", "四", "思", "私", "死", "似", "司", "丝");
        single(m, "song", "送", "宋", "松", "颂");
        single(m, "sou", "搜", "艘");
        single(m, "su", "素", "速", "宿", "苏", "塑", "诉");
        single(m, "suan", "算", "酸", "蒜");
        single(m, "sui", "随", "岁", "碎", "遂", "穗");
        single(m, "sun", "孙", "损", "笋");
        single(m, "suo", "所", "锁", "索", "缩");

        single(m, "ta", "他", "她", "它", "塔", "踏");
        single(m, "tai", "太", "台", "态", "抬", "泰");
        single(m, "tan", "谈", "弹", "坦", "叹", "贪", "摊", "潭");
        single(m, "tang", "堂", "糖", "躺", "汤", "烫", "塘");
        single(m, "tao", "套", "逃", "讨", "陶", "涛", "桃");
        single(m, "te", "特", "忒");
        single(m, "teng", "疼", "腾", "藤");
        single(m, "ti", "体", "提", "题", "替", "梯", "蹄");
        single(m, "tian", "天", "田", "甜", "添", "填");
        single(m, "tiao", "条", "跳", "调", "挑");
        single(m, "tie", "铁", "贴", "帖");
        single(m, "ting", "听", "停", "厅", "庭", "挺");
        single(m, "tong", "通", "同", "痛", "童", "统", "铜");
        single(m, "tou", "头", "投", "透");
        single(m, "tu", "图", "土", "突", "途", "涂", "吐");
        single(m, "tuan", "团");
        single(m, "tui", "推", "腿", "退");
        single(m, "tun", "吞", "屯");
        single(m, "tuo", "脱", "拖", "托", "妥");

        single(m, "wa", "娃", "挖", "瓦", "洼");
        single(m, "wai", "外", "歪");
        single(m, "wan", "完", "万", "晚", "碗", "弯", "湾", "玩");
        single(m, "wang", "网", "王", "往", "忘", "望", "旺", "汪");
        single(m, "wei", "为", "位", "未", "维", "卫", "微", "威", "危", "委");
        single(m, "wen", "问", "文", "温", "稳", "纹");
        single(m, "weng", "翁", "嗡", "瓮");
        single(m, "wo", "我", "握", "窝", "卧");
        single(m, "wu", "五", "无", "物", "武", "午", "务", "误", "屋", "吴", "乌");

        single(m, "xi", "西", "喜", "希", "息", "系", "洗", "细", "戏", "席");
        single(m, "xia", "下", "夏", "吓", "峡", "瞎");
        single(m, "xian", "现", "先", "线", "限", "险", "鲜", "闲", "献");
        single(m, "xiang", "想", "向", "像", "项", "相", "香", "乡", "详");
        single(m, "xiao", "小", "笑", "晓", "效", "校", "肖");
        single(m, "xie", "写", "些", "谢", "协", "邪", "胁", "鞋");
        single(m, "xin", "心", "新", "信", "欣", "辛");
        single(m, "xing", "行", "星", "兴", "型", "形", "性", "醒");
        single(m, "xiong", "兄", "凶", "胸", "熊", "雄");
        single(m, "xiu", "修", "秀", "休", "袖", "绣");
        single(m, "xu", "需", "许", "续", "虚", "序", "畜");
        single(m, "xuan", "选", "宣", "悬", "旋", "玄");
        single(m, "xue", "学", "雪", "血", "穴");
        single(m, "xun", "寻", "迅", "训", "讯", "巡");

        single(m, "ya", "呀", "压", "牙", "鸭", "崖", "亚", "哑");
        single(m, "yan", "眼", "言", "严", "研", "盐", "炎", "烟", "延", "颜");
        single(m, "yang", "样", "阳", "洋", "养", "羊", "央", "扬");
        single(m, "yao", "要", "药", "摇", "遥", "腰", "妖", "邀");
        single(m, "ye", "也", "夜", "叶", "业", "野", "页", "液");
        single(m, "yi", "一", "以", "已", "意", "义", "易", "医", "艺", "衣", "依", "移", "议");
        single(m, "yin", "因", "音", "银", "引", "印", "阴", "饮");
        single(m, "ying", "应", "英", "营", "影", "迎", "硬", "映", "蝇");
        single(m, "yo", "哟");
        single(m, "yong", "用", "永", "拥", "勇", "涌", "咏");
        single(m, "you", "有", "又", "由", "右", "友", "油", "游", "尤");
        single(m, "yu", "与", "于", "语", "雨", "遇", "育", "预", "余", "鱼", "玉");
        single(m, "yuan", "元", "原", "员", "圆", "远", "院", "愿", "源", "缘");
        single(m, "yue", "月", "约", "越", "跃", "岳", "悦");
        single(m, "yun", "运", "云", "韵", "允");

        single(m, "za", "杂", "砸");
        single(m, "zai", "在", "再", "载", "灾", "栽");
        single(m, "zan", "咱", "赞", "暂");
        single(m, "zang", "藏", "脏");
        single(m, "zao", "早", "造", "糟", "遭", "灶");
        single(m, "ze", "则", "责", "泽", "择");
        single(m, "zei", "贼");
        single(m, "zen", "怎");
        single(m, "zeng", "增", "赠");
        single(m, "zha", "炸", "扎", "闸", "榨");
        single(m, "zhai", "窄", "宅", "债", "摘");
        single(m, "zhan", "站", "战", "占", "展", "绽", "蘸");
        single(m, "zhang", "张", "长", "章", "涨", "障", "掌");
        single(m, "zhao", "找", "照", "招", "着", "赵", "朝");
        single(m, "zhe", "这", "着", "者", "哲", "浙");
        single(m, "zhei", "这");
        single(m, "zhen", "真", "镇", "针", "珍", "阵", "振", "震");
        single(m, "zheng", "正", "政", "整", "证", "争", "征", "睁");
        single(m, "zhi", "只", "之", "直", "制", "知", "志", "至", "指", "值", "纸", "质", "治");
        single(m, "zhong", "中", "种", "重", "终", "钟", "众");
        single(m, "zhou", "周", "州", "洲", "粥", "轴");
        single(m, "zhu", "主", "住", "助", "注", "逐", "竹", "祝", "著");
        single(m, "zhua", "抓");
        single(m, "zhuai", "拽");
        single(m, "zhuan", "转", "赚", "专", "砖");
        single(m, "zhuang", "装", "状", "撞", "壮");
        single(m, "zhui", "追", "坠", "缀");
        single(m, "zhun", "准");
        single(m, "zhuo", "捉", "桌", "卓", "灼");
        single(m, "zi", "子", "自", "字", "资", "紫", "滋");
        single(m, "zong", "总", "宗", "综", "棕");
        single(m, "zou", "走", "奏");
        single(m, "zu", "组", "足", "族", "祖", "阻", "租");
        single(m, "zuan", "钻");
        single(m, "zui", "最", "嘴", "罪", "醉");
        single(m, "zun", "尊", "遵");
        single(m, "zuo", "做", "作", "左", "坐", "昨", "座");

        phrase(m, "ni'hao", "你好");
        phrase(m, "ni'hao'ma", "你好吗");
        phrase(m, "zai'jian", "再见");
        phrase(m, "xiexie", "谢谢");
        phrase(m, "xie'xie", "谢谢");
        phrase(m, "zhong'guo", "中国");
        phrase(m, "wo'men", "我们");
        phrase(m, "ni'men", "你们");
        phrase(m, "ta'men", "他们");
        phrase(m, "shi'de", "是的");
        phrase(m, "dui'bu'qi", "对不起");
        phrase(m, "mei'guan'xi", "没关系");
        phrase(m, "shen'me", "什么");
        phrase(m, "wei'shen'me", "为什么");
        phrase(m, "zen'me", "怎么");
        phrase(m, "xian'zai", "现在");
        phrase(m, "shi'jian", "时间");
        phrase(m, "peng'you", "朋友");
        phrase(m, "gong'zuo", "工作");
        phrase(m, "xue'xi", "学习");
        phrase(m, "xi'huan", "喜欢");
        phrase(m, "zhi'dao", "知道");
        phrase(m, "ke'yi", "可以");
        phrase(m, "mei'you", "没有");
        phrase(m, "yin'wei", "因为");
        phrase(m, "suo'yi", "所以");
        phrase(m, "dan'shi", "但是");
        phrase(m, "ru'guo", "如果");
        phrase(m, "yi'jing", "已经");
        phrase(m, "yi'qian", "以前");
        phrase(m, "yi'hou", "以后");
        phrase(m, "tong'zhi", "同志");
        phrase(m, "tong'shi", "同事");
        phrase(m, "jiao'shi", "教师");
        phrase(m, "xue'sheng", "学生");
        phrase(m, "jia'ting", "家庭");
        phrase(m, "she'hui", "社会");
        phrase(m, "shi'jie", "世界");
        phrase(m, "ke'xue", "科学");
        phrase(m, "dian'nao", "电脑");
        phrase(m, "shou'ji", "手机");
        phrase(m, "dian'hua", "电话");
        phrase(m, "yin'yue", "音乐");
        phrase(m, "dian'ying", "电影");
        phrase(m, "kuai'le", "快乐");
        phrase(m, "xing'fu", "幸福");
        phrase(m, "ping'an", "平安");
        phrase(m, "jian'kang", "健康");
        phrase(m, "mei'li", "美丽");
        phrase(m, "zhu'fu", "祝福");
        phrase(m, "sheng'ri", "生日");
        phrase(m, "sheng'dan", "圣诞");

        ENTRIES = Collections.unmodifiableMap(m);
    }

    private PinyinDictionary() {
    }

    private static void single(Map<String, List<String>> m, String syllable, String... chars) {
        m.put(syllable, Collections.unmodifiableList(Arrays.asList(chars)));
    }

    private static void phrase(Map<String, List<String>> m, String phrase, String... words) {
        m.put(phrase, Collections.unmodifiableList(Arrays.asList(words)));
    }

    /**
     * @param phraseKey pinyin phrase (bare syllable or {@code '}-joined).
     * @return immutable candidate list; empty when unknown.
     */
    public static List<String> lookup(String phraseKey) {
        List<String> r = ENTRIES.get(phraseKey);
        return r == null ? Collections.<String>emptyList() : r;
    }

    /** @return {@code true} if the dictionary has any entry for the phrase. */
    public static boolean has(String phraseKey) {
        return ENTRIES.containsKey(phraseKey);
    }
}
