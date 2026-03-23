package com.minikano.f50_sms.modules.plugins

import android.content.Context
import com.minikano.f50_sms.configs.AppMeta
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.modules.auth.authenticatedRoute
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.KanoRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

fun Route.pluginsModule(context: Context) {
    val TAG = "[$BASE_TAG]_pluginsModule"

    val pluginSourcePrefName = "kano_ZTE_store"
    val pluginSourcePrefKey = "kano_plugin_sources"

    fun builtInPluginSources(): JSONArray {
        val baseUrl = AppMeta.GLOBAL_SERVER_URL.trim().trimEnd('/')
        val host = try {
            URI(baseUrl).host?.takeIf { it.isNotBlank() } ?: baseUrl
        } catch (_: Exception) {
            baseUrl
        }
        val path = "/UFI-TOOLS-UPDATE/plugins/ufi-tools-plugins"
        return JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", "builtin-pan-kanokano-cn")
                    put("name", host)
                    put("downloadUrl", "$baseUrl/d$path")
                    put("apiUrl", "$baseUrl/api/fs/list")
                    put("path", path)
                    put("password", "")
                }
            )
        }
    }

    fun normalizePath(raw: String): String {
        if (raw.isBlank()) {
            return raw
        }
        val trimmed = raw.trim()
        val withLeadingSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return if (withLeadingSlash == "/") "/" else withLeadingSlash.trimEnd('/')
    }

    val slugSplitRegex = Regex("[^A-Za-z0-9]+")

    fun slugify(vararg parts: String): String? {
        val sanitizedParts = parts.flatMap { part ->
            part.split(slugSplitRegex)
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
        }
        if (sanitizedParts.isEmpty()) {
            return null
        }
        return sanitizedParts.joinToString("-")
    }

    fun slugFromUrl(url: String): String? {
        return try {
            val parsed = URI(url)
            val hostPart = parsed.host ?: ""
            val pathPart = parsed.path ?: ""
            slugify(hostPart, pathPart)
        } catch (e: Exception) {
            slugify(url)
        }
    }

    data class AlistDerivedConfig(
        val apiUrl: String,
        val path: String,
        val downloadUrl: String,
        val idHint: String?,
        val nameHint: String?,
        val passwordHint: String?
    )

    fun deriveAlistConfigFromDownload(downloadUrl: String): AlistDerivedConfig? {
        return try {
            val normalized = downloadUrl.trim().trimEnd('/')
            if (normalized.isEmpty()) {
                return null
            }
            val uri = URI(normalized)
            val scheme = uri.scheme
            val host = uri.host
            if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
                return null
            }

            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            val origin = "$scheme://$host$portPart"
            val pathSegments = uri.path?.split('/')?.filter { it.isNotBlank() } ?: emptyList()
            var prefixSegments: List<String> = emptyList()
            var alistPathSegments: List<String> = emptyList()
            val dIndex = pathSegments.indexOf("d")
            if (dIndex != -1 && dIndex < pathSegments.lastIndex) {
                prefixSegments = pathSegments.subList(0, dIndex)
                alistPathSegments = pathSegments.subList(dIndex + 1, pathSegments.size)
            } else {
                val prefixes = setOf("fs", "dav", "alist", "share", "public", "downloads")
                val pIndex = pathSegments.indexOfFirst { prefixes.contains(it.lowercase(Locale.ROOT)) }
                if (pIndex != -1 && pIndex < pathSegments.lastIndex) {
                    prefixSegments = pathSegments.subList(0, pIndex + 1)
                    alistPathSegments = pathSegments.subList(pIndex + 1, pathSegments.size)
                } else {
                    alistPathSegments = pathSegments
                }
            }

            if (alistPathSegments.isEmpty()) {
                return null
            }
            val basePath =
                if (prefixSegments.isEmpty()) "" else "/" + prefixSegments.joinToString("/")
            val derivedPath = "/" + alistPathSegments.joinToString("/")

            val slugParts = mutableListOf(host)
            slugParts.addAll(prefixSegments)
            slugParts.addAll(alistPathSegments)
            val idHint = slugify(*slugParts.toTypedArray())

            val nameHintRaw = alistPathSegments.lastOrNull()?.takeIf { it.isNotBlank() } ?: host
            val nameHint = nameHintRaw
                ?.replace('-', ' ')
                ?.replace('_', ' ')
                ?.trim()
                ?.split("\\s+".toRegex())
                ?.filter { it.isNotBlank() }
                ?.joinToString(" ") { word ->
                    val lower = word.lowercase(Locale.ROOT)
                    lower.replaceFirstChar { it.uppercaseChar() }
                }

            val queryPassword = run {
                val query = uri.rawQuery
                if (query.isNullOrBlank()) {
                    null
                } else {
                    val candidates = setOf("pw", "password", "pwd", "pass", "access_code")
                    var found: String? = null
                    for (param in query.split("&")) {
                        if (param.isBlank()) {
                            continue
                        }
                        val keyValue = param.split("=", limit = 2)
                        val rawKey = keyValue[0]
                        val rawValue = if (keyValue.size > 1) keyValue[1] else ""
                        val decodedKey =
                            URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
                                .lowercase(Locale.ROOT)
                        if (decodedKey !in candidates) {
                            continue
                        }
                        val decodedValue =
                            URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name()).trim()
                        if (decodedValue.isNotEmpty()) {
                            found = decodedValue
                            break
                        }
                    }
                    found
                }
            }

            AlistDerivedConfig(
                apiUrl = origin + basePath + "/api/fs/list",
                path = normalizePath(derivedPath),
                downloadUrl = origin + basePath + "/d/" + alistPathSegments.joinToString("/"),
                idHint = idHint,
                nameHint = nameHint,
                passwordHint = queryPassword
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to derive AList config: ${e.message}")
            null
        }
    }

    fun sanitizeSource(json: JSONObject, builtIn: Boolean): JSONObject? {
        val id = json.optString("id").trim()
        val name = json.optString("name").trim()
        val password = json.optString("password", "").trim()
        val rawApiUrl = json.optString("apiUrl").trim()
        val rawPath = json.optString("path").trim()
        val rawDownloadUrl = json.optString("downloadUrl").trim()

        val derived = if (rawDownloadUrl.isNotBlank()) {
            deriveAlistConfigFromDownload(rawDownloadUrl)
        } else {
            null
        }

        val finalDownloadUrl = (derived?.downloadUrl ?: rawDownloadUrl).trim().trimEnd('/')
        val finalApiUrl = (derived?.apiUrl ?: rawApiUrl).trim()
        val finalPath = normalizePath(derived?.path ?: rawPath)
        val finalPassword = when {
            password.isNotBlank() -> password
            !derived?.passwordHint.isNullOrBlank() -> derived?.passwordHint ?: ""
            else -> ""
        }

        if (finalDownloadUrl.isBlank()) {
            return null
        }

        if (finalApiUrl.isBlank() || finalPath.isBlank()) {
            return null
        }

        val resolvedId = if (id.isNotBlank()) {
            id
        } else {
            val generated = (derived?.idHint ?: slugFromUrl(finalDownloadUrl)).orEmpty().trim()
            val normalized = if (generated.isNotBlank()) {
                generated
            } else {
                Integer.toUnsignedString(finalDownloadUrl.hashCode(), 16)
            }
            if (normalized.startsWith("auto-")) normalized else "auto-$normalized"
        }

        if (resolvedId.isBlank()) {
            return null
        }

        val nameHint = derived?.nameHint
        val resolvedName = if (name.isNotBlank()) {
            name
        } else {
            nameHint?.takeIf { it.isNotBlank() } ?: resolvedId
        }

        if (resolvedName.isBlank()) {
            return null
        }

        return JSONObject().apply {
            put("id", resolvedId)
            put("name", resolvedName)
            put("apiUrl", finalApiUrl)
            put("path", finalPath)
            put("downloadUrl", finalDownloadUrl)
            put("password", finalPassword)
            put("builtIn", builtIn)
        }
    }

    fun loadPluginSources(): JSONArray {
        val result = JSONArray()
        val seenIds = mutableSetOf<String>()

        fun addSource(raw: JSONObject, builtIn: Boolean) {
            val sanitized = sanitizeSource(raw, builtIn) ?: return
            val sourceId = sanitized.optString("id")
            if (sourceId.isBlank()) return
            if (seenIds.add(sourceId)) {
                result.put(sanitized)
            }
        }

        // Always include built-in sources first.
        try {
            val builtIns = builtInPluginSources()
            for (i in 0 until builtIns.length()) {
                val raw = builtIns.optJSONObject(i) ?: continue
                addSource(raw, true)
            }
        } catch (e: Exception) {
            KanoLog.d(TAG, "加载内置插件源失败：${e.message}")
        }

        val sharedPref = context.getSharedPreferences(pluginSourcePrefName, Context.MODE_PRIVATE)
        val stored = sharedPref.getString(pluginSourcePrefKey, null)
        if (!stored.isNullOrBlank()) {
            try {
                val storedArray = JSONArray(stored)
                for (i in 0 until storedArray.length()) {
                    val raw = storedArray.optJSONObject(i) ?: continue
                    addSource(raw, false)
                }
            } catch (e: Exception) {
                KanoLog.d(TAG, "解析插件源配置失败：${e.message}")
            }
        }
        return result
    }

    fun storeCustomPluginSources(customSources: JSONArray) {
        val sharedPref = context.getSharedPreferences(pluginSourcePrefName, Context.MODE_PRIVATE)
        sharedPref.edit()
            .putString(pluginSourcePrefKey, customSources.toString())
            .commit()
    }

    authenticatedRoute(context){
        //保存自定义头部
        post("/api/set_custom_head") {
            try {
                val body = call.receiveText()
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val maxSizeInBytes = 1145 * 1024

                if (bodyBytes.size > maxSizeInBytes) {
                    throw Exception("自定义头部超出限制: ${bodyBytes.size / 1145}KB/1024KB")
                }

                val json = JSONObject(body)
                val text = json.optString("text", "").trim()

                val sharedPref =
                    context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
                sharedPref.edit()
                    .putString("kano_custom_head", text)
                    .commit()

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"success"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )

            } catch (e: Exception) {
                KanoLog.d(TAG, "配置出错： ${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"配置出错: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        get("/api/plugin_sources") {
            try {
                val sources = loadPluginSources()
                val responseJson = JSONObject().apply {
                    put("sources", sources)
                }
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    responseJson.toString(),
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.d(TAG, "读取插件源配置出错：${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"读取插件源配置出错"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        post("/api/plugin_sources") {
            try {
                val bodyText = call.receiveText()
                val json = JSONObject(bodyText)
                val incomingSources = json.optJSONArray("sources") ?: JSONArray()
                val sanitizedForStorage = JSONArray()
                val responseArray = JSONArray()
                val seenIds = mutableSetOf<String>()

                for (i in 0 until incomingSources.length()) {
                    val raw = incomingSources.optJSONObject(i) ?: continue
                    val sanitized = sanitizeSource(raw, false) ?: continue
                    val sourceId = sanitized.optString("id")
                    if (!seenIds.add(sourceId)) {
                        continue
                    }
                    sanitizedForStorage.put(sanitized)
                    responseArray.put(sanitized)
                }

                storeCustomPluginSources(sanitizedForStorage)

                val responseJson = JSONObject().apply {
                    put("result", "success")
                    put("sources", responseArray)
                }

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    responseJson.toString(),
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.d(TAG, "保存插件源配置出错：${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"保存插件源配置出错"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        //从插件市场获取插件
        get("/api/plugins_store") {
            var alistResponse: okhttp3.Response? = null
            try {
                val sources = loadPluginSources()
                if (sources.length() == 0) {
                    throw Exception("未配置插件源")
                }

                val requestedId = call.request.queryParameters["sourceId"]
                var targetSource = sources.getJSONObject(0)
                for (i in 0 until sources.length()) {
                    val candidate = sources.optJSONObject(i) ?: continue
                    if (candidate.optString("id") == requestedId) {
                        targetSource = candidate
                        break
                    }
                }

                val apiUrl = targetSource.optString("apiUrl")
                val downloadUrl = targetSource.optString("downloadUrl")
                val path = targetSource.optString("path")
                val password = targetSource.optString("password", "")

                if (apiUrl.isBlank() || downloadUrl.isBlank() || path.isBlank()) {
                    throw Exception("插件源配置不完整")
                }

                val requestJson = JSONObject().apply {
                    put("path", path)
                    put("password", password)
                    put("page", 1)
                    put("per_page", 0)
                    put("refresh", false)
                }

                alistResponse = KanoRequest.postJson(apiUrl, requestJson.toString())
                val alistBody = alistResponse.body?.string()

                val responseJson = JSONObject().apply {
                    put("download_url", downloadUrl.trimEnd('/'))
                    val resJson = if (!alistBody.isNullOrBlank()) JSONObject(alistBody) else JSONObject()
                    put("res", resJson)
                    put("source", targetSource)
                }

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    responseJson.toString(),
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.d(TAG, "请求出错：${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"请求出错"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            } finally {
                alistResponse?.close()
            }
        }

        // 获取带签名的直链（用于第三方 AList 受保护目录）
        get("/api/plugin_signed_link") {
            var alistResponse: okhttp3.Response? = null
            try {
                val name = call.request.queryParameters["name"]?.trim()
                if (name.isNullOrBlank()) {
                    throw Exception("missing name")
                }

                val sources = loadPluginSources()
                if (sources.length() == 0) throw Exception("no source")

                val requestedId = call.request.queryParameters["sourceId"]
                var targetSource = sources.getJSONObject(0)
                for (i in 0 until sources.length()) {
                    val candidate = sources.optJSONObject(i) ?: continue
                    if (candidate.optString("id") == requestedId) {
                        targetSource = candidate
                        break
                    }
                }

                val apiUrl = targetSource.optString("apiUrl")
                val downloadUrl = targetSource.optString("downloadUrl")
                val basePath = targetSource.optString("path")
                val password = targetSource.optString("password", "")

                if (apiUrl.isBlank() || basePath.isBlank() || downloadUrl.isBlank()) {
                    throw Exception("bad source")
                }

                val getUrl = when {
                    apiUrl.endsWith("/fs/list") -> apiUrl.removeSuffix("/fs/list") + "/fs/get"
                    apiUrl.endsWith("/api/fs/list") -> apiUrl.removeSuffix("/api/fs/list") + "/api/fs/get"
                    else -> apiUrl.replace("/list", "/get")
                }

                val fullPath = (if (basePath.startsWith("/")) basePath else "/$basePath").trimEnd('/') + "/" + name
                val reqJson = JSONObject().apply {
                    put("path", fullPath)
                    put("password", password)
                }

                alistResponse = KanoRequest.postJson(getUrl, reqJson.toString())
                val bodyStr = alistResponse.body?.string()
                val obj = if (!bodyStr.isNullOrBlank()) JSONObject(bodyStr) else JSONObject()
                val data = obj.optJSONObject("data")
                var finalUrl = data?.optString("raw_url").orEmpty()
                if (finalUrl.isBlank()) finalUrl = data?.optString("url").orEmpty()
                if (finalUrl.isBlank()) {
                    // 回退：拼接基础下载地址
                    finalUrl = downloadUrl.trimEnd('/') + "/" + name
                }

                val resp = JSONObject().apply { put("url", finalUrl) }
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(resp.toString(), ContentType.Application.Json, HttpStatusCode.OK)
            } catch (e: Exception) {
                KanoLog.d(TAG, "签名直链获取失败: ${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText("""{"error":"failed"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            } finally {
                alistResponse?.close()
            }
        }
    }

    //读取自定义头部
    get("/api/get_custom_head") {
        try {
            val sharedPref =
                context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            val text = sharedPref.getString("kano_custom_head", "") ?: ""
            val json = JSONObject(mapOf("text" to text)).toString()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                json,
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "读取自定义头部出错： ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"读取自定义头部出错"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

}
