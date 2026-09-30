package dev.frost819.newbv.biliapi.entity.pgc.index

import dev.frost819.newbv.biliapi.entity.pgc.PgcType

interface PgcIndexParam {
    /** 中文显示名（UI 筛选项共用）。 */
    val label: String
}

/**
 * 排序
 */
enum class IndexOrder(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    UpdateTime("更新时间", 0),
    DanmakuCount("弹幕数量", 1),
    PlayCount("播放数量", 2),
    FollowCount("追番人数", 3),
    Score("最高评分", 4),
    StartTime("开播时间", 5),
    PublishTime("上映时间", 6),
    ;

    companion object {
        fun getList(pgcType: PgcType): List<IndexOrder> =
            when (pgcType) {
                PgcType.Anime -> listOf(FollowCount, UpdateTime, Score, PlayCount, StartTime)
                PgcType.GuoChuang -> listOf(FollowCount, UpdateTime, Score, PlayCount, StartTime)
                PgcType.Movie -> listOf(PlayCount, UpdateTime, PublishTime, Score)
                PgcType.Documentary -> listOf(PlayCount, Score, UpdateTime, PublishTime, DanmakuCount)
                PgcType.Tv -> listOf(PlayCount, UpdateTime, DanmakuCount, Score, FollowCount)
                PgcType.Variety -> listOf(PlayCount, UpdateTime, PublishTime, Score, DanmakuCount)
            }
    }
}

enum class IndexOrderType(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    Desc("降序", 0),
    Asc("升序", 1),
}

/**
 * 类型
 */
enum class SeasonVersion(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    FeatureFilm("正片", 1),
    Movies("电影", 2),
    Other("其他", 3),
    ;

    companion object {
        fun getList(pgcType: PgcType): List<SeasonVersion> =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang -> listOf(All, FeatureFilm, Movies, Other)
                else -> emptyList()
            }
    }
}

/**
 * 配音
 */
enum class SpokenLanguage(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    OriginalSoundtrack("原声", 1),
    ChineseDubbing("中文配音", 2),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime -> listOf(All, OriginalSoundtrack, ChineseDubbing)
                else -> emptyList()
            }
    }
}

/**
 * 地区
 */
enum class Area(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    MainlandChina("中国大陆", 1),
    Japan("日本", 2),
    America("美国", 3),
    Britain("英国", 4),
    Other("其他", 5),
    ChinaHongKongTaiwan("中国港台", 6), // 6,7
    Korea("韩国", 8),
    France("法国", 9),
    Thailand("泰国", 10),
    Spain("西班牙", 13),
    Germany("德国", 15),
    Italy("意大利", 35),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime -> listOf(All, Japan, America, Other)
                PgcType.Movie ->
                    listOf(
                        All,
                        MainlandChina,
                        ChinaHongKongTaiwan,
                        America,
                        Japan,
                        Korea,
                        France,
                        Britain,
                        Germany,
                        Thailand,
                        Italy,
                        Spain,
                        Other,
                    )

                PgcType.Tv -> listOf(All, MainlandChina, Japan, America, Britain, Other)
                else -> emptyList()
            }
    }
}

/**
 * 状态（完结状态）
 */
enum class IsFinish(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    Finished("完结", 1),
    Serialization("连载", 0),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang -> listOf(All, Finished, Serialization)
                else -> emptyList()
            }
    }
}

/**
 * 版权
 */
enum class Copyright(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    Exclusive("独家", 3),
    Other("其他", 1), // 1,2,4
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang -> listOf(All, Exclusive, Other)
                else -> emptyList()
            }
    }
}

/**
 * 付费（付费状态）
 */
enum class SeasonStatus(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    Free("免费", 1),
    Paid("付费", 2), // 2,6
    Prime("大会员", 4), // 4,6
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang, PgcType.Movie -> listOf(All, Free, Paid, Prime)
                PgcType.Documentary, PgcType.Tv, PgcType.Variety -> listOf(All, Free, Prime)
            }
    }
}

/**
 * 季度
 */
enum class SeasonMonth(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    January("1月", 1),
    April("4月", 4),
    July("7月", 7),
    October("10月", 10),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime -> listOf(All, January, April, July, October)
                else -> emptyList()
            }
    }
}

/**
 * 出品（方）
 */
enum class Producer(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    BBC("BBC", 1),
    NHK("NHK", 2),
    SKY("SKY", 3),
    CCTV("央视", 4),
    ITV("ITV", 5),
    HistoryChannel("历史频道", 6),
    DiscoveryChannel("探索频道", 7),
    SatelliteTV("卫视", 8),
    SelfMade("自制", 9),
    ZDF("ZDF", 10),
    Cooperation("合作机构", 11),
    DomesticOther("国内其他", 12),
    ForeignOther("国外其他", 13),
    NationalGeographic("国家地理", 14),
    Sony("索尼", 15),
    Universal("环球", 16),
    Paramount("派拉蒙", 17),
    Warner("华纳", 18),
    Disney("迪士尼", 19),
    HBO("HBO", 20),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Documentary ->
                    listOf(
                        All,
                        CCTV,
                        BBC,
                        DiscoveryChannel,
                        NationalGeographic,
                        NHK,
                        HistoryChannel,
                        SatelliteTV,
                        SelfMade,
                        ITV,
                        SKY,
                        ZDF,
                        Cooperation,
                        DomesticOther,
                        ForeignOther,
                        Sony,
                        Universal,
                        Paramount,
                        Warner,
                        Disney,
                        HBO,
                    )

                else -> emptyList()
            }
    }
}

/**
 * 年份（Year）
 */
@Suppress("EnumEntryName")
enum class Year(
    override val label: String,
    val str: String,
) : PgcIndexParam {
    All("全部", "-1"),
    Year2026("2026", "[2026,2027)"),
    Year2025("2025", "[2025,2026)"),
    Year2024("2024", "[2024,2025)"),
    Year2023("2023", "[2023,2024)"),
    Year2022("2022", "[2022,2023)"),
    Year2021("2021", "[2021,2022)"),
    Year2020("2020", "[2020,2021)"),
    Year2019("2019", "[2019,2020)"),
    Year2018("2018", "[2018,2019)"),
    Year2017("2017", "[2017,2018)"),
    Year2016("2016", "[2016,2017)"),
    Year2015("2015", "[2015,2016)"),
    Year2014_2010("2014-2010", "[2010,2015)"),
    Year2009_2005("2009-2005", "[2005,2010)"),
    Year2004_2000("2004-2000", "[2000,2005)"),
    Year199x("90年代", "[1990,2000)"),
    Year198x("80年代", "[1980,1990)"),
    Earlier("更早", "[,1980)"),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang ->
                    listOf(
                        All,
                        Year2026,
                        Year2025,
                        Year2024,
                        Year2023,
                        Year2022,
                        Year2021,
                        Year2020,
                        Year2019,
                        Year2018,
                        Year2017,
                        Year2016,
                        Year2015,
                        Year2014_2010,
                        Year2009_2005,
                        Year2004_2000,
                        Year199x,
                        Year198x,
                        Earlier,
                    )

                else -> emptyList()
            }
    }
}

/**
 * 年份（发布时间）
 */
@Suppress("EnumEntryName")
enum class ReleaseDate(
    override val label: String,
    val str: String,
) : PgcIndexParam {
    All("全部", "-1"),
    Year2026("2026", "[2026-01-01 00:00:00,2027-01-01 00:00:00)"),
    Year2025("2025", "[2025-01-01 00:00:00,2026-01-01 00:00:00)"),
    Year2024("2024", "[2024-01-01 00:00:00,2025-01-01 00:00:00)"),
    Year2023("2023", "[2023-01-01 00:00:00,2024-01-01 00:00:00)"),
    Year2022("2022", "[2022-01-01 00:00:00,2023-01-01 00:00:00)"),
    Year2021("2021", "[2021-01-01 00:00:00,2022-01-01 00:00:00)"),
    Year2020("2020", "[2020-01-01 00:00:00,2021-01-01 00:00:00)"),
    Year2019("2019", "[2019-01-01 00:00:00,2020-01-01 00:00:00)"),
    Year2018("2018", "[2018-01-01 00:00:00,2019-01-01 00:00:00)"),
    Year2017("2017", "[2017-01-01 00:00:00,2018-01-01 00:00:00)"),
    Year2016("2016", "[2016-01-01 00:00:00,2017-01-01 00:00:00)"),
    Year2015("2015", "[2015-01-01 00:00:00,2016-01-01 00:00:00)"),
    Year2015_2010("2015-2010", "[2010-01-01 00:00:00,2015-01-01 00:00:00)"),
    Year2009_2005("2009-2005", "[2005-01-01 00:00:00,2010-01-01 00:00:00)"),
    Year2004_2000("2004-2000", "[2000-01-01 00:00:00,2005-01-01 00:00:00)"),
    Year199x("90年代", "[1990-01-01 00:00:00,2000-01-01 00:00:00)"),
    Year198x("80年代", "[1980-01-01 00:00:00,1990-01-01 00:00:00)"),
    Earlier("更早", "[,1980-01-01 00:00:00)"),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Movie, PgcType.Documentary, PgcType.Tv ->
                    listOf(
                        All,
                        Year2026,
                        Year2025,
                        Year2024,
                        Year2023,
                        Year2022,
                        Year2021,
                        Year2020,
                        Year2019,
                        Year2018,
                        Year2017,
                        Year2016,
                        Year2015,
                        Year2015_2010,
                        Year2009_2005,
                        Year2004_2000,
                        Year199x,
                        Year198x,
                        Earlier,
                    )

                else -> emptyList()
            }
    }
}

/**
 * 风格
 */
enum class Style(
    override val label: String,
    val id: Int,
) : PgcIndexParam {
    All("全部", -1),
    Movie("电影", -10),

    Original("原创", 10010),
    Comic("漫画改", 10011),
    Novel("小说改", 10012),
    Game("游戏改", 10013),
    Animation("动态漫", 10014),
    Puppetry("布袋戏", 10015),
    HotBlood("热血", 10016),
    TimeTravel("穿越", 10017),
    Fantasy("奇幻", 10018),
    XuanHuan("玄幻", 10019),

    Fight("战斗", 10020),
    Funny("搞笑", 10021),
    Daily("日常", 10022),
    ScienceFiction("科幻", 10023),
    Moe("萌系", 10024),
    Healing("治愈", 10025),
    School("校园", 10026),
    Children("少儿", 10027),
    InstantNoodles("泡面", 10028),
    InLove("恋爱", 10029),

    Girl("少女", 10030),
    Magic("魔法", 10031),
    Adventure("冒险", 10032),
    History("历史", 10033),
    Fiction("架空", 10034),
    Mecha("机战", 10035),
    GodDemon("神魔", 10036),
    VoiceControl("声控", 10037),
    Sports("运动", 10038),
    Inspirational("励志", 10039),

    Music("音乐", 10040),
    Reasoning("推理", 10041),
    Club("社团", 10042),
    WisdomFight("智斗", 10043),
    Tearjerker("催泪", 10044),
    Food("美食", 10045),
    Idol("偶像", 10046),
    Maiden("乙女", 10047),
    Workplace("职场", 10048),
    AncientStyle("古风", 10049),

    Plot("剧情", 10050),
    Comedy("喜剧", 10051),
    Love("爱情", 10052),
    Action("动作", 10053),
    Terror("恐怖", 10054),
    Offense("犯罪", 10055),
    Thriller("惊悚", 10056),
    Suspense("悬疑", 10057),
    War("战争", 10058),
    // 10059

    Biography("传记", 10060),
    Family("家庭", 10061),
    Opera("歌剧", 10062),
    Documentary("纪实", 10063),
    Disaster("灾难", 10064),
    Humanities("人文", 10065),
    Technology("科技", 10066),
    Explore("探险", 10067),
    Universal("通用", 10068),
    CutePet("萌宠", 10069),

    Social("社会", 10070),
    Animal("动物", 10071),
    Nature("自然", 10072),
    Medical("医疗", 10073),
    Military("军事", 10074),
    Crime("罪案", 10075),
    Mystery("神秘", 10076),
    Travel("旅行", 10077),
    MartialArts("武侠", 10078),
    Youth("青春", 10079),

    City("都市", 10080),
    AncientCostume("古装", 10081),
    SpyWar("谍战", 10082),
    Classic("经典", 10083),
    Emotion("情感", 10084),
    Myth("神话", 10085),
    Age("年代", 10086),
    Rural("农村", 10087),
    CriminalInvestigation("刑侦", 10088),
    MilitaryLife("军旅", 10089),

    Interview("访谈", 10090),
    TalkShow("脱口秀", 10091),
    RealityShow("真人秀", 10092),

    // 10093
    Selection("选秀", 10094),
    Tourism("旅游", 10095),
    Concert("演唱会", 10096),
    ParentChild("亲子", 10097),
    EveningParty("晚会", 10098),
    Cultivate("养成", 10099),

    Culture("文化", 10100),

    // 10101
    SpecialEffects("特摄", 10102),
    ShortPlay("短剧", 10103),
    ShortFilm("短片", 10104),
    ;

    companion object {
        fun getList(pgcType: PgcType) =
            when (pgcType) {
                PgcType.Anime ->
                    listOf(
                        All,
                        Original,
                        Comic,
                        Novel,
                        Game,
                        SpecialEffects,
                        Puppetry,
                        HotBlood,
                        TimeTravel,
                        Fantasy,
                        Fight,
                        Funny,
                        Daily,
                        ScienceFiction,
                        Moe,
                        Healing,
                        School,
                        Children,
                        InstantNoodles,
                        InLove,
                        Girl,
                        Magic,
                        Adventure,
                        History,
                        Fiction,
                        Mecha,
                        GodDemon,
                        VoiceControl,
                        Sports,
                        Inspirational,
                        Music,
                        Reasoning,
                        Club,
                        WisdomFight,
                        Tearjerker,
                        Food,
                        Idol,
                        Maiden,
                        Workplace,
                    )

                PgcType.GuoChuang ->
                    listOf(
                        All,
                        Original,
                        Comic,
                        Novel,
                        Game,
                        Animation,
                        Puppetry,
                        HotBlood,
                        Fantasy,
                        XuanHuan,
                        Fight,
                        Funny,
                        MartialArts,
                        Daily,
                        ScienceFiction,
                        Moe,
                        Healing,
                        Suspense,
                        School,
                        Children,
                        InstantNoodles,
                        InLove,
                        Girl,
                        Magic,
                        History,
                        Mecha,
                        GodDemon,
                        VoiceControl,
                        Sports,
                        Inspirational,
                        Music,
                        Reasoning,
                        Club,
                        WisdomFight,
                        Tearjerker,
                        Food,
                        Idol,
                        Maiden,
                        Workplace,
                        AncientStyle,
                    )

                PgcType.Movie ->
                    listOf(
                        All,
                        ShortFilm,
                        Plot,
                        Comedy,
                        Love,
                        Action,
                        Terror,
                        ScienceFiction,
                        Offense,
                        Thriller,
                        Suspense,
                        Fantasy,
                        War,
                        Animation,
                        Biography,
                        Family,
                        Opera,
                        History,
                        Adventure,
                        Documentary,
                        Disaster,
                        Comic,
                        Novel,
                    )

                PgcType.Documentary ->
                    listOf(
                        All,
                        History,
                        Food,
                        Humanities,
                        Technology,
                        Explore,
                        Universal,
                        CutePet,
                        Social,
                        Animal,
                        Nature,
                        Medical,
                        Military,
                        Disaster,
                        Crime,
                        Mystery,
                        Travel,
                        Sports,
                        Movie,
                    )

                PgcType.Variety ->
                    listOf(
                        All,
                        Music,
                        Interview,
                        TalkShow,
                        RealityShow,
                        Selection,
                        Food,
                        Tourism,
                        EveningParty,
                        Concert,
                        Emotion,
                        Comedy,
                        ParentChild,
                        Culture,
                        Workplace,
                        CutePet,
                        Cultivate,
                    )

                PgcType.Tv ->
                    listOf(
                        All,
                        Plot,
                        Emotion,
                        Funny,
                        Suspense,
                        City,
                        Family,
                        AncientCostume,
                        History,
                        Fantasy,
                        Youth,
                        War,
                        MartialArts,
                        Inspirational,
                        ShortPlay,
                        ScienceFiction,
                    )
            }
    }
}
