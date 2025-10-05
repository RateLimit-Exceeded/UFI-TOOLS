package com.minikano.f50_sms.modules.plugins

import android.content.Context
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.modules.auth.authenticatedRoute
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

fun Route.pluginsModule(context: Context) {
    val TAG = "[$BASE_TAG]_pluginsModule"

    val pluginSourcePrefName = "kano_ZTE_store"
    val pluginSourcePrefKey = "kano_plugin_sources"

    fun sanitizeSource(json: JSONObject, builtIn: Boolean): JSONObject? {
        val id = json.optString("id").trim()
        val name = json.optString("name").trim()
        val apiUrl = json.optString("apiUrl").trim()
        val path = json.optString("path").trim()
        val downloadUrl = json.optString("downloadUrl").trim().trimEnd('/')
        val password = json.optString("password", "").trim()

        if (id.isBlank() || name.isBlank() || apiUrl.isBlank() || path.isBlank() || downloadUrl.isBlank()) {
            return null
        }

        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("apiUrl", apiUrl)
            put("path", path)
            put("downloadUrl", downloadUrl)
            put("password", password)
            put("builtIn", builtIn)
        }
    }

    fun defaultPluginSource(): JSONObject {
        val json = JSONObject()
        json.put("id", "official")
        json.put("name", "官方源")
        json.put("apiUrl", "https://pan.kanokano.cn/api/fs/list")
        json.put("path", "/UFI-TOOLS-UPDATE/plugins/ufi-tools-plugins")
        json.put("downloadUrl", "https://pan.kanokano.cn/d/UFI-TOOLS-UPDATE/plugins/ufi-tools-plugins")
        json.put("password", "")
        json.put("builtIn", true)
        return json
    }

    fun loadPluginSources(): JSONArray {
        val result = JSONArray()
        val defaultSource = defaultPluginSource()
        result.put(defaultSource)
        val sharedPref = context.getSharedPreferences(pluginSourcePrefName, Context.MODE_PRIVATE)
        val stored = sharedPref.getString(pluginSourcePrefKey, null)
        if (!stored.isNullOrBlank()) {
            try {
                val storedArray = JSONArray(stored)
                val seenIds = mutableSetOf(defaultSource.optString("id"))
                for (i in 0 until storedArray.length()) {
                    val raw = storedArray.optJSONObject(i) ?: continue
                    val sanitized = sanitizeSource(raw, false) ?: continue
                    val sourceId = sanitized.optString("id")
                    if (seenIds.add(sourceId)) {
                        result.put(sanitized)
                    }
                }
            } catch (e: Exception) {
                KanoLog.d(TAG, "解析插件源配置失败：${e.message}")
            }
        }
        return result
    }

    fun storeCustomPluginSources(customSources: JSONArray) {
        val sharedPref = context.getSharedPreferences(pluginSourcePrefName, Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString(pluginSourcePrefKey, customSources.toString())
            apply()
        }
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
                sharedPref.edit().apply {
                    putString("kano_custom_head", text)
                    apply()
                }

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
                val defaultSource = defaultPluginSource()
                val sanitizedForStorage = JSONArray()
                val responseArray = JSONArray().apply { put(defaultSource) }
                val seenIds = mutableSetOf(defaultSource.optString("id"))

                for (i in 0 until incomingSources.length()) {
                    val raw = incomingSources.optJSONObject(i) ?: continue
                    val id = raw.optString("id")
                    if (id == defaultSource.optString("id")) {
                        // 默认源不可覆盖
                        continue
                    }
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
