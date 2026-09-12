package com.filmvault.app.data.model

/** 列表中的单部影片（列存 JSON 解析后的扁平结构） */
data class MovieItem(
    val id: String,
    val dir: String,            // mv / tv / ac
    val title: String,
    val year: Int? = null,
    val rating: Double? = null, // 站点评分 (d)
    val imdb: Double? = null,   // IMDb (im)
    val quality: List<String> = emptyList(), // 画质 (q)
    val status: String? = null, // 状态 (g)
    val regionCode: Int? = null,// 地区码 (a[1])
    val genreCodes: List<Int> = emptyList(), // 类型码 (a[2..])
) {
    val posterUrl: String
        get() = "/img/$dir/$id/256.webp"
    val posterLargeUrl: String
        get() = "/img/$dir/$id/256.webp"
}

/** 详情元数据（从 /{dir}/{id} HTML 内嵌 JSON 解析） */
data class DetailMeta(
    val id: String,
    val dir: String,
    val title: String,
    val originalName: String? = null,
    val year: Int? = null,
    val releaseDate: String? = null,
    val duration: String? = null,
    val rating: Double? = null,
    val region: List<String> = emptyList(),
    val language: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val summary: String? = null,
    val cast: List<String> = emptyList(),
    val episodes: List<EpisodeRef> = emptyList(),
) {
    val posterUrl: String get() = "/img/$dir/$id/256.webp"
}

data class EpisodeRef(
    val id: String,
    val title: String,
)

/** 磁力资源 */
data class MagnetItem(
    val index: Int,
    val hash: String,        // info-hash（40 位 hex）
    val fileName: String,
    val size: String,
    val seeds: Int,
    val qualityCode: String,
    val qualityLabel: String?,
    val added: String,
) {
    val magnetUri: String get() = "magnet:?xt=urn:btih:$hash"
}

/** 网盘资源 */
data class CloudItem(
    val index: Int,
    val name: String,
    val url: String,
    val type: Int,
    val user: String?,
    val category: String?,  // 网盘类型名（如 迅雷网盘/百度网盘）
)

/** 在线播放线路 */
data class PlayLine(
    val id: String,          // 用于拼接 /py/{id}/{n}
    val name: String,
    val episodes: List<String>, // 各集/清晰度名称
)

/** 资源总览 */
data class Resources(
    val magnets: List<MagnetItem>,
    val clouds: List<CloudItem>,
    val playLines: List<PlayLine>,
)

/** 观看历史 */
data class HistoryItem(
    val id: String,
    val dir: String,
    val title: String,
    val episode: Int? = null,
)

/** 登录结果 */
data class LoginResult(val success: Boolean, val message: String? = null)
