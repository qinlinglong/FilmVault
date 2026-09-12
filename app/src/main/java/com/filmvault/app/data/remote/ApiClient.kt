package com.filmvault.app.data.remote

import android.content.Context
import com.filmvault.app.data.model.CloudItem
import com.filmvault.app.data.model.DetailMeta
import com.filmvault.app.data.model.EpisodeRef
import com.filmvault.app.data.model.HistoryItem
import com.filmvault.app.data.model.MagnetItem
import com.filmvault.app.data.model.MovieItem
import com.filmvault.app.data.model.PlayLine
import com.filmvault.app.data.model.Resources
import com.filmvault.app.util.Constants
import com.filmvault.app.util.FileCookieJar
import com.filmvault.app.util.PowSolver
import com.filmvault.app.util.SiteSettingsStore
import com.filmvault.app.util.hostOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 原生网络层（OkHttp）。负责：
 *  1. PoW 反爬验证（/res/pow）
 *  2. 登录态（app_auth cookie）
 *  3. 列表 / 搜索 / 详情 / 资源 / 历史 / 收藏 等接口调用与解析
 *
 * 所有解析均为原生 Kotlin 实现，不依赖 WebView。
 */
class ApiClient(context: Context, private val siteSettings: SiteSettingsStore) {

    private val cookieJar = FileCookieJar(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .header("Accept", "application/json, text/html, */*")
                .build()
            chain.proceed(request)
        }
        .build()
    private val verificationMutex = Mutex()
    private var verificationGeneration = 0L

    /** 当前站点地址（运行时从配置读取，支持随时切换）。 */
    private val baseUrl: String get() = siteSettings.siteUrlNow
    /** 当前站点 host（用于按域名隔离 cookie）。 */
    private val host: String get() = hostOf(baseUrl)

    private suspend fun executeWithVerification(request: () -> okhttp3.Response): okhttp3.Response {
        val observedGeneration = verificationGeneration
        var response = request()
        if (response.code == 419) {
            response.close()
            verificationMutex.withLock {
                // 并发请求收到同一轮 419 时，只让第一路重新计算 PoW。
                if (verificationGeneration == observedGeneration) {
                    cookieJar.clearCookie("browser_verified", host)
                    solvePow()
                    verificationGeneration++
                }
            }
            response = request()
        }
        return response
    }

    /**
     * 站点安全验证页有时会以 HTTP 200 返回，不能只依赖 419 判断验证失效。
     * 统一读取响应文本并识别验证页，避免把 HTML 验证页当成空列表/空详情。
     */
    private suspend fun requestTextWithVerification(request: () -> okhttp3.Response): String {
        var attempt = 0
        while (true) {
            try {
                return requestTextWithVerificationOnce(request)
            } catch (e: IOException) {
                attempt++
                if (attempt >= 3) throw e
                delay(700L * attempt)
            }
        }
    }

    private suspend fun requestTextWithVerificationOnce(request: () -> okhttp3.Response): String {
        var observedGeneration = verificationGeneration
        var response = executeWithVerification(request)
        var text = response.use { it.body?.string().orEmpty() }
        repeat(2) {
            if (!isVerificationPage(text)) return text
            verificationMutex.withLock {
                // 首页会并发请求三个分类；同一轮只允许第一路刷新验证。
                if (verificationGeneration == observedGeneration) {
                    cookieJar.clearCookie("browser_verified", host)
                    solvePow()
                    verificationGeneration++
                }
            }
            response = executeWithVerification(request)
            text = response.use { it.body?.string().orEmpty() }
            observedGeneration = verificationGeneration
        }
        return text
    }

    private fun isVerificationPage(text: String): Boolean =
        text.contains("<title>浏览器安全验证</title>", ignoreCase = true) ||
            text.contains("class=\"pow-scope\"", ignoreCase = true) ||
            Regex("\\\"code\\\"\\s*:\\s*419").containsMatchIn(text)

    /** 确保已完成 PoW 验证（拿到 browser_verified）。 */
    private suspend fun ensureVerified() = withContext(Dispatchers.IO) {
        if (cookieJar.hasCookie("browser_verified", host)) return@withContext
        verificationMutex.withLock {
            if (!cookieJar.hasCookie("browser_verified", host)) solvePow()
        }
    }

    /** 求解并提交流水验证。 */
    private fun solvePow() {
        val challenge = client.newCall(
            Request.Builder().url("${baseUrl}/res/pow").build()
        ).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            json.parseToJsonElement(body).jsonObject
        }
        val n = challenge["N"]?.jsonPrimitive?.content ?: return
        val x = challenge["x"]?.jsonPrimitive?.content ?: return
        val t = challenge["t"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
        val y = PowSolver.solve(n, x, t)

        val form = FormBody.Builder().add("y", y).build()
        client.newCall(
            Request.Builder().url("${baseUrl}/res/pow").post(form).build()
        ).execute().use { /* 服务器返回 browser_verified cookie */ }
    }

    /** 登录。成功返回 true。 */
    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        ensureVerified()
        val form = FormBody.Builder()
            .add("username", email)
            .add("password", password)
            // 与站点登录页 login-*.js 保持一致：cookietime 是秒数，且需要附带站点提交标记。
            .add("cookietime", "10506240")
            .add("siteid", "1")
            .add("dosubmit", "1")
            .add("code", "")
            .build()
        val resp = executeWithVerification { client.newCall(
            Request.Builder().url("${baseUrl}/user/login").post(form).build()
        ).execute() }
        resp.use {
            val ok = cookieJar.hasCookie("app_auth")
            return@withContext ok
        }
    }

    /** 是否已登录（持有当前站点的 app_auth） */
    fun isLoggedIn(): Boolean = cookieJar.hasCookie("app_auth", host)

    /** 退出登录：清掉当前站点的本地 cookie（其它站点会话保留）。 */
    fun logout() {
        cookieJar.clearForHost(host)
    }

    /** 获取分类列表（电影/剧集/动漫）。 */
    suspend fun getList(
        dir: String,
        page: Int = 1,
        query: Map<String, String> = emptyMap()
    ): Pair<List<MovieItem>, Int> = withContext(Dispatchers.IO) {
        ensureVerified()
        val urlBuilder = "${baseUrl}/res/$dir".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
        query.forEach { (k, v) -> if (v.isNotBlank()) urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build()
        val text = requestTextWithVerification {
            client.newCall(Request.Builder().url(url).build()).execute()
        }
        parseListText(text, dir)
    }

    /** 搜索。 */
    suspend fun search(q: String, page: Int = 1, type: String = "", mode: String = "1"): Pair<List<MovieItem>, Int> = withContext(Dispatchers.IO) {
        ensureVerified()
        val url = "${baseUrl}/res/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("type", type)
            .addQueryParameter("mode", mode)
            .build()
        val text = requestTextWithVerification {
            client.newCall(Request.Builder().url(url).build()).execute()
        }
        parseListText(text, "mv")
    }

    /** 获取网页端独立热门排行（/hits/{dir}/{period}）。 */
    suspend fun getHome(): Map<String, List<MovieItem>> = withContext(Dispatchers.IO) {
        ensureVerified()
        val html = requestTextWithVerification {
            client.newCall(Request.Builder().url(baseUrl).build()).execute()
        }
        parseHomeHtml(html)
    }

    private fun parseHomeHtml(html: String): Map<String, List<MovieItem>> {
        val marker = "_obj.inlist="
        val start = html.indexOf(marker)
        if (start < 0) return emptyMap()
        val arrayStart = html.indexOf('[', start + marker.length)
        if (arrayStart < 0) return emptyMap()
        var depth = 0
        var end = -1
        for (i in arrayStart until html.length) {
            when (html[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { end = i + 1; break } }
            }
        }
        if (end < 0) return emptyMap()
        val sections = runCatching { json.parseToJsonElement(html.substring(arrayStart, end)).jsonArray }.getOrNull() ?: return emptyMap()
        return sections.mapNotNull { element ->
            val section = element.jsonObject
            val dir = section["ty"]?.jsonPrimitive?.content ?: return@mapNotNull null
            dir to parseInlist(section, dir)
        }.toMap()
    }

    /** 获取网页端独立热门排行（/hits/{dir}/{period}）。 */
    suspend fun getHot(dir: String, period: String = "day"): List<MovieItem> = withContext(Dispatchers.IO) {
        ensureVerified()
        val request = Request.Builder()
            .url("${baseUrl}/hits/$dir/$period")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        val html = requestTextWithVerification { client.newCall(request).execute() }
        parseHotHtml(html, dir)
    }

    /** 解析列存 inlist -> 影片列表 + 总页数 */
    private fun parseList(resp: Response, dir: String): Pair<List<MovieItem>, Int> {
        val text = resp.body?.string().orEmpty()
        return parseListText(text, dir)
    }

    private fun parseListText(text: String, dir: String): Pair<List<MovieItem>, Int> {
        if (text.isBlank()) return emptyList<MovieItem>() to 1
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList<MovieItem>() to 1
        val pages = root["page"]?.jsonObject?.get("pages")?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val inlist = root["inlist"]?.jsonObject ?: return emptyList<MovieItem>() to pages
        return parseInlist(inlist, dir) to pages
    }

    private fun parseInlist(inlist: JsonObject, dir: String): List<MovieItem> {
        val iArr = inlist["i"]?.jsonArray ?: return emptyList()
        // 分类接口使用紧凑字段 t/a；搜索接口使用可读字段 title/name/year。
        val tArr = inlist["t"]?.jsonArray
            ?: inlist["title"]?.jsonArray
            ?: inlist["name"]?.jsonArray
        val aArr = inlist["a"]?.jsonArray
        val yearArr = inlist["year"]?.jsonArray
        val dArr = inlist["d"]?.jsonArray
        val imArr = inlist["im"]?.jsonArray
        val qArr = inlist["q"]?.jsonArray
        val gArr = inlist["g"]?.jsonArray

        val result = mutableListOf<MovieItem>()
        for (k in iArr.indices) {
            val id = iArr[k].jsonPrimitive.content
            val title = tArr?.getOrNull(k)?.jsonPrimitive?.content ?: ""
            val itemDir = dArr?.getOrNull(k)?.jsonPrimitive?.content
                ?.takeIf { it == "mv" || it == "tv" || it == "ac" } ?: dir
            val aItem = aArr?.getOrNull(k)?.jsonArray
            val year = aItem?.getOrNull(0)?.jsonPrimitive?.content?.toIntOrNull()
                ?: yearArr?.getOrNull(k)?.jsonPrimitive?.content?.toIntOrNull()
            val regionCode = aItem?.getOrNull(1)?.jsonPrimitive?.content?.toIntOrNull()
            val genreCodes = if (aItem != null && aItem.size > 2) {
                aItem.subList(2, aItem.size).mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
            } else emptyList()
            val rating = dArr?.getOrNull(k)?.jsonPrimitive?.content?.toDoubleOrNull()
            val imdb = imArr?.getOrNull(k)?.jsonPrimitive?.content?.toDoubleOrNull()
            val quality = qArr?.getOrNull(k)?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val status = gArr?.getOrNull(k)?.jsonPrimitive?.content
            result.add(
                MovieItem(
                    id = id, dir = itemDir, title = title, year = year,
                    rating = rating, imdb = imdb, quality = quality,
                    status = status, regionCode = regionCode, genreCodes = genreCodes
                )
            )
        }
        return result
    }

    private fun parseHotHtml(html: String, dir: String): List<MovieItem> {
        val result = mutableListOf<MovieItem>()
        val cardPattern = Regex(
            """<a href="/$dir/([^"]+)"[^>]*title="([^"]+)".*?<div class="tag">(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        cardPattern.findAll(html).forEach { match ->
            val id = match.groupValues[1]
            val title = decodeHtml(match.groupValues[2])
            val tag = decodeHtml(match.groupValues[3].replace(Regex("<[^>]+>"), "").trim())
            val year = Regex("\\b(19|20)\\d{2}\\b").find(tag)?.value?.toIntOrNull()
            result += MovieItem(id = id, dir = dir, title = title, year = year)
        }
        return result.distinctBy { "${it.dir}/${it.id}" }
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    /** 获取详情元数据：请求 /{dir}/{id} HTML，提取内嵌 JSON。 */
    suspend fun getDetailMeta(dir: String, id: String, fallbackTitle: String): DetailMeta =
        withContext(Dispatchers.IO) {
            ensureVerified()
            val html = requestTextWithVerification {
                client.newCall(Request.Builder().url("${baseUrl}/$dir/$id").build()).execute()
            }
            parseDetailMeta(html, dir, id, fallbackTitle)
        }

    private fun parseDetailMeta(html: String, dir: String, id: String, fallbackTitle: String): DetailMeta {
        val objStr = extractJsonObjectContaining(html, "\"summary\"") ?: extractJsonObjectContaining(html, "\"diqu\"")
        if (objStr == null) {
            return DetailMeta(id = id, dir = dir, title = fallbackTitle)
        }
        return try {
            val o = json.parseToJsonElement(objStr).jsonObject
            fun str(key: String): String? = o[key]?.jsonPrimitive?.content
            fun strList(key: String): List<String> =
                (o[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            val xle = (o["xle"] as? JsonObject)
            val eps = if (xle != null) {
                val u = (xle["u"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                val t = (xle["t"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                u.mapIndexed { idx, uid -> EpisodeRef(uid, t.getOrNull(idx) ?: "") }
            } else emptyList()
            DetailMeta(
                id = id, dir = dir,
                title = str("title") ?: str("name") ?: fallbackTitle,
                originalName = str("ename") ?: str("name"),
                year = str("year")?.toIntOrNull(),
                releaseDate = str("stime"),
                duration = str("times"),
                rating = str("score")?.toDoubleOrNull() ?: str("rating")?.toDoubleOrNull(),
                region = strList("diqu"),
                language = strList("yuyan"),
                genres = strList("type"),
                summary = str("summary"),
                cast = strList("zhuyan"),
                episodes = eps,
            )
        } catch (_: Exception) {
            DetailMeta(id = id, dir = dir, title = fallbackTitle)
        }
    }

    /** 在 HTML 中提取包含 marker 的最内层 JSON 对象（括号匹配）。 */
    private fun extractJsonObjectContaining(html: String, marker: String): String? {
        val idx = html.indexOf(marker)
        if (idx < 0) return null
        // 向左找到包含该 marker 的 '{'
        var depth = 0
        var start = -1
        for (j in idx downTo 0) {
            when (html[j]) {
                '}' -> depth++
                '{' -> {
                    if (depth == 0) { start = j; break }
                    depth--
                }
            }
        }
        if (start < 0) return null
        // 向右找到匹配的 '}'
        var d2 = 0
        var end = -1
        for (j in start until html.length) {
            when (html[j]) {
                '{' -> d2++
                '}' -> {
                    d2--
                    if (d2 == 0) { end = j; break }
                }
            }
        }
        if (end < 0) return null
        return html.substring(start, end + 1)
    }

    /** 获取播放/下载资源总览。 */
    suspend fun getResources(dir: String, id: String): Resources = withContext(Dispatchers.IO) {
        ensureVerified()
        val text = requestTextWithVerification {
            client.newCall(Request.Builder().url("${baseUrl}/res/downurl/$dir/$id").build()).execute()
        }
        parseResources(text, dir, id)
    }

    /** 解析在线播放页中的真实 m3u8/mp4 直链，供 App 内播放器使用。 */
    suspend fun resolvePlayUrl(lineId: String, episode: Int): String? = withContext(Dispatchers.IO) {
        ensureVerified()
        val html = requestTextWithVerification {
            client.newCall(Request.Builder().url("${baseUrl}/py/$lineId/$episode").build()).execute()
        }
        val playerObject = extractJsonObjectContaining(html, "\"url\"") ?: return@withContext null
        runCatching {
            json.parseToJsonElement(playerObject).jsonObject["url"]?.jsonPrimitive?.content
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResources(text: String, dir: String, id: String): Resources {
        val root = json.parseToJsonElement(text).jsonObject
        val downlist = root["downlist"]?.jsonObject ?: return Resources(emptyList(), emptyList(), emptyList())
        val hex = downlist["hex"]?.jsonPrimitive?.content ?: ""
        val typeObj = downlist["type"]?.jsonObject
        val typeLabels = (typeObj?.get("a") as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        val typeCodes = (typeObj?.get("b") as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        val codeToLabel = typeCodes.zip(typeLabels).toMap()

        val list = downlist["list"]?.jsonObject
        val mArr = (list?.get("m") as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
        val tArr = (list?.get("t") as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
        val sArr = (list?.get("s") as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
        val eArr = (list?.get("e") as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
        val pArr = (list?.get("p") as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
        val nArr = (list?.get("n") as? JsonArray) ?: JsonArray(emptyList<JsonElement>())

        val magnets = (0 until mArr.size).map { k ->
            val hash = mArr[k].jsonPrimitive.content
            val fileName = tArr.getOrNull(k)?.jsonPrimitive?.content ?: ""
            val size = sArr.getOrNull(k)?.jsonPrimitive?.content ?: ""
            val seeds = eArr.getOrNull(k)?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val qCode = pArr.getOrNull(k)?.jsonPrimitive?.content ?: ""
            val added = nArr.getOrNull(k)?.jsonPrimitive?.content ?: ""
            MagnetItem(
                index = k, hash = hash, fileName = fileName, size = size,
                seeds = seeds, qualityCode = qCode,
                qualityLabel = codeToLabel[qCode], added = added
            )
        }

        // 在线播放线路
        val playlist = (root["playlist"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
        val playLines = (0 until playlist.size).mapNotNull { k ->
            val item = playlist[k].jsonObject
            val pid = item["i"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = item["t"]?.jsonPrimitive?.content ?: "线路${k + 1}"
            val eps = (item["list"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
            PlayLine(id = pid, name = name, episodes = eps)
        }

        // 网盘资源
        // 无网盘资源时站点返回 panlist: []，有资源时才是对象。
        val panlist = root["panlist"] as? JsonObject
        val clouds = if (panlist != null) {
            val pId = (panlist["id"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
            val pName = (panlist["name"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
            val pUrl = (panlist["url"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
            val pType = (panlist["type"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
            val pUser = (panlist["user"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
            val pTname = (panlist["tname"] as? JsonArray) ?: JsonArray(emptyList<JsonElement>())
            (0 until pId.size).map { k ->
                val tnameIdx = (pType.getOrNull(k)?.jsonPrimitive?.content?.toIntOrNull() ?: 1) - 1
                CloudItem(
                    index = k,
                    name = pName.getOrNull(k)?.jsonPrimitive?.content ?: "",
                    url = pUrl.getOrNull(k)?.jsonPrimitive?.content ?: "",
                    type = pType.getOrNull(k)?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    user = pUser.getOrNull(k)?.jsonPrimitive?.content,
                    category = pTname.getOrNull(tnameIdx.coerceAtLeast(0))?.jsonPrimitive?.content,
                )
            }
        } else emptyList()

        return Resources(magnets = magnets, clouds = clouds, playLines = playLines)
    }

    /** 观看历史。 */
    suspend fun getHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        ensureVerified()
        val text = requestTextWithVerification {
            client.newCall(Request.Builder().url("${baseUrl}/res/historys").build()).execute()
        }
        parseHistory(text)
    }

    private fun parseHistory(text: String): List<HistoryItem> {
        if (text.isBlank() || text == "[]") return emptyList()
        return try {
            val arr = json.parseToJsonElement(text).jsonArray
            arr.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val dir = o["dir"]?.jsonPrimitive?.content ?: "mv"
                val title = o["title"]?.jsonPrimitive?.content ?: ""
                val ep = o["s"]?.jsonPrimitive?.content?.toIntOrNull()
                HistoryItem(id = id, dir = dir, title = title, episode = ep)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 收藏：添加 / 删除 */
    suspend fun addFavorite(dir: String, id: String): Boolean = toggleFavorite("add", dir, id)
    suspend fun delFavorite(dir: String, id: String): Boolean = toggleFavorite("del", dir, id)

    private suspend fun toggleFavorite(action: String, dir: String, id: String): Boolean =
        withContext(Dispatchers.IO) {
            ensureVerified()
            val resp = executeWithVerification { client.newCall(
                Request.Builder().url("${baseUrl}/res/favorite/$action/$dir/$id").build()
            ).execute() }
            resp.use { it.isSuccessful }
        }

    companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        fun emptyBody() = "".toRequestBody("text/plain".toMediaType())
    }
}
