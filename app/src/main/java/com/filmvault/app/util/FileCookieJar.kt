package com.filmvault.app.util

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

/**
 * 基于文件的持久化 CookieJar。仅持久化目标域名的鉴权/反爬 cookie，
 * 使登录态与 PoW 验证在应用重启后依然有效。
 */
class FileCookieJar(context: Context) : CookieJar {

    private val storeFile = File(context.cacheDir, "cookies.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    // 域名 -> cookies
    private val memory: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    init {
        load()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val host = url.host
            val list = memory.getOrPut(host) { mutableListOf() }
            for (cookie in cookies) {
                // 移除同名同 path 的旧 cookie
                list.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) {
                    list.add(cookie)
                }
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val host = url.host
            val result = mutableListOf<Cookie>()
            // 精确域名和父域名 cookie 都要带上
            memory[host]?.let { result += it.filter { c -> c.expiresAt > System.currentTimeMillis() } }
            val base = host.indexOf('.')
            if (base >= 0) {
                val parent = host.substring(base)
                memory[parent]?.let { result += it.filter { c -> c.expiresAt > System.currentTimeMillis() } }
            }
            return result.distinctBy { "${it.name}@${it.path}" }
        }
    }

    /** 判断当前是否持有指定名称的 cookie（用于检查 PoW / 登录态），跨域名全局判断。 */
    fun hasCookie(name: String): Boolean = synchronized(lock) {
        memory.values.any { list -> list.any { c -> c.name == name && c.expiresAt > System.currentTimeMillis() } }
    }

    /** 按域名判断：仅统计 host 及其父域名的 cookie（切换站点时不串台）。 */
    fun hasCookie(name: String, host: String): Boolean = synchronized(lock) {
        val keys = if (host.contains('.')) setOf(host, host.substring(host.indexOf('.'))) else setOf(host)
        memory.filterKeys { it in keys }.values.any { list ->
            list.any { c -> c.name == name && c.expiresAt > System.currentTimeMillis() }
        }
    }

    /** 清空全部 cookie（退出登录时调用） */
    fun clear() {
        synchronized(lock) {
            memory.clear()
            storeFile.delete()
        }
    }

    /** 仅清空指定域名（含父域名）的 cookie，其它站点会话保留。 */
    fun clearForHost(host: String) = synchronized(lock) {
        val parent = if (host.contains('.')) host.substring(host.indexOf('.')) else null
        memory.keys.removeIf { it == host || it == parent }
        persist()
    }

    /** 仅使站点反爬验证失效，保留登录态，便于服务端返回 419 时自动重新验证。 */
    fun clearCookie(name: String, host: String) = synchronized(lock) {
        memory.values.forEach { list -> list.removeAll { it.name == name && (it.domain == host || it.domain == ".${host.substringAfter('.', host)}") } }
        persist()
    }

    private fun persist() {
        try {
            val arr = buildJsonArray {
                for ((_, cookies) in memory) {
                    for (c in cookies) {
                        add(buildJsonObject {
                            put("name", JsonPrimitive(c.name))
                            put("value", JsonPrimitive(c.value))
                            put("domain", JsonPrimitive(c.domain))
                            put("path", JsonPrimitive(c.path))
                            put("expiresAt", JsonPrimitive(c.expiresAt))
                            put("secure", JsonPrimitive(c.secure))
                            put("httpOnly", JsonPrimitive(c.httpOnly))
                            put("hostOnly", JsonPrimitive(c.hostOnly))
                        })
                    }
                }
            }
            storeFile.writeText(arr.toString())
        } catch (_: Exception) {
            // 忽略持久化失败
        }
    }

    private fun load() {
        try {
            if (!storeFile.exists()) return
            val arr = json.parseToJsonElement(storeFile.readText()).jsonArray
            for (el in arr) {
                val o = el.jsonObject
                val cookie = Cookie.Builder()
                    .name(o["name"]!!.jsonPrimitive.content)
                    .value(o["value"]!!.jsonPrimitive.content)
                    .domain(o["domain"]!!.jsonPrimitive.content)
                    .path(o["path"]!!.jsonPrimitive.content)
                    .expiresAt(o["expiresAt"]!!.jsonPrimitive.content.toLong())
                    .apply {
                        if (o["secure"]?.jsonPrimitive?.content?.toBoolean() == true) secure()
                        if (o["httpOnly"]?.jsonPrimitive?.content?.toBoolean() == true) httpOnly()
                    }
                    .build()
                val host = cookie.domain.removePrefix(".")
                memory.getOrPut(host) { mutableListOf() }.add(cookie)
            }
        } catch (_: Exception) {
            // 忽略损坏的缓存
        }
    }

}
