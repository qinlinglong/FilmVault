package com.filmvault.app.util

/**
 * 站点与接口常量。
 * 站点使用 filejin 反爬体系：每次会话需先通过 PoW 验证（/res/pow）拿到 browser_verified，
 * 登录后拿到 app_auth。所有接口均需要这两个 cookie。
 */
object Constants {
    /** 默认站点地址（用户可在 App 内重新配置 / 切换） */
    /** 不内置任何站点；首次使用需由用户在登录页填写模块化仓库地址。 */
    const val DEFAULT_BASE_URL = ""
    const val DEFAULT_IMG_BASE = "/img"
    const val REPOSITORY_URL = "https://github.com/qinlinglong/FilmVault"

    // 分类目录 -> 接口 dir
    val CATEGORIES = listOf(
        Category("mv", "电影", "/res/mv"),
        Category("tv", "剧集", "/res/tv"),
        Category("ac", "动漫", "/res/ac"),
    )

    const val PREF_FILE = "filmvault_prefs"

    // 排序方式 (sort 参数)
    val SORT_OPTIONS = listOf(
        "" to "添加时间",
        "uptime" to "更新时间",
        "date" to "上映时间",
        "score" to "评分最高",
        "number" to "评分人数",
        "numbers" to "评分总人数",
        "cscore" to "综合评分",
        "rand" to "随机",
    )

    val QUALITY_OPTIONS = listOf("" to "全部画质", "720P" to "720P", "1080P" to "1080P", "4K" to "4K", "3D" to "3D", "BD" to "BD", "HDR" to "HDR", "DV" to "DV", "原盘" to "原盘")
    val REGION_OPTIONS = listOf("" to "全部地区", "海外" to "海外", "欧美" to "欧美", "亚洲" to "亚洲", "美国" to "美国", "日本" to "日本", "韩国" to "韩国", "英国" to "英国", "法国" to "法国", "德国" to "德国", "印度" to "印度", "泰国" to "泰国", "大陆" to "大陆", "香港" to "香港", "台湾" to "台湾", "加拿大" to "加拿大", "俄罗斯" to "俄罗斯", "意大利" to "意大利", "西班牙" to "西班牙", "澳大利亚" to "澳大利亚")
    val LANGUAGE_OPTIONS = listOf("" to "全部语言", "英语" to "英语", "法语" to "法语", "国语" to "国语", "粤语" to "粤语", "日语" to "日语", "韩语" to "韩语", "泰语" to "泰语", "德语" to "德语", "俄语" to "俄语", "闽南语" to "闽南语", "丹麦语" to "丹麦语", "波兰语" to "波兰语", "瑞典语" to "瑞典语", "印地语" to "印地语", "挪威语" to "挪威语", "意大利语" to "意大利语", "无对白" to "无对白")
    val YEAR_OPTIONS = buildList {
        add("" to "全部年代")
        add("3" to "近三年")
        for (year in 2026 downTo 2017) add(year.toString() to year.toString())
        addAll(listOf("120" to "20年代", "110" to "10年代", "100" to "00年代", "90" to "90年代", "80" to "80年代", "70" to "70年代", "60" to "60年代", "1" to "更早"))
    }
    val RESOURCE_OPTIONS = listOf("" to "全部资源", "在线" to "在线播放", "磁力" to "种子", "网盘" to "网盘")
    val SEARCH_CATEGORIES = listOf("" to "全部", "1" to "电影", "2" to "剧集", "3" to "动漫", "4" to "种子", "5" to "网盘")
    val SEARCH_MODES = listOf("1" to "模糊", "2" to "适中", "3" to "精准")
}

data class Category(val dir: String, val label: String, val api: String) {
    fun poster(id: String, size: String = "256") = "${Constants.DEFAULT_IMG_BASE}/${dir}/${id}/${size}.webp"
}

/** 海报地址 */
fun posterUrl(dir: String, id: String, size: String = "256") =
    "${Constants.DEFAULT_IMG_BASE}/$dir/$id/$size.webp"

/** 从站点地址中取出 host（用于按域名隔离 cookie） */
fun hostOf(url: String): String {
    val noScheme = url.removePrefix("https://").removePrefix("http://")
    val host = noScheme.substringBefore('/').substringBefore('?')
    return host.substringBefore(':')
}
