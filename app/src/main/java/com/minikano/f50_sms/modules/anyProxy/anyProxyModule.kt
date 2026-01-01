package com.minikano.f50_sms.modules.at

import android.content.Context
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.utils.KanoLog
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.routing.Route
import io.ktor.util.toByteArray
import okhttp3.*
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject

val unsafeHeaderNames = setOf(
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers",
    "transfer-encoding", "upgrade", "host", "content-length", "expect",
    "referer", "origin", "sec-fetch-site", "sec-fetch-mode", "sec-fetch-dest", "sec-fetch-user",
    "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "via", "x-forwarded-for",
    "x-forwarded-proto", "x-real-ip", "authorization", "content-security-policy",
    "content-security-policy-report-only", "clear-site-data"
)

fun isSafeHeader(header: String): Boolean {
    return header.lowercase() !in unsafeHeaderNames
}

val forbiddenDomains = listOf(
    "ufi.ztedevice.com"
)

fun isForbiddenHost(targetUrl: String): Boolean {
    return try {
        val uri = URI(targetUrl)
        val host = uri.host ?: return true
        if (forbiddenDomains.any { it.equals(host, ignoreCase = true) }) {
            return true
        }
        val addresses = InetAddress.getAllByName(host)
        addresses.any { address ->
            address.isAnyLocalAddress || // 0.0.0.0
            address.isLoopbackAddress || // 127.0.0.1, ::1
            address.isLinkLocalAddress || // 169.254.x.x
            address.isSiteLocalAddress // 192.168.x.x, 10.x.x.x, 172.16.x.x
        }
    } catch (e: UnknownHostException) {
        true
    } catch (e: Exception) {
        true
    }
}

fun Route.anyProxyModule(context: Context) {
    val TAG = "[$BASE_TAG]_anyProxyModule"

    route("/api/proxy/{...}") {
        handle {
            val rawPath = call.request.uri.removePrefix("/api/proxy/")
            var targetUrl = rawPath.removePrefix("--")

            if (isForbiddenHost(targetUrl)) {
                call.respond(HttpStatusCode.Forbidden, "Access to target address is not allowed.")
                return@handle
            }

            val method = call.request.httpMethod.value

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .removeHeader("Accept-Encoding")
                        .addHeader("Accept-Encoding", "identity") // 避免 GZIP 解压
                        .build()
                    chain.proceed(request)
                }.build()

            // 构建请求体（如果有）
            val requestBody = if (call.request.httpMethod in listOf(
                    HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch
                )
            ) {
                val bodyBytes = call.receiveChannel().toByteArray()
                bodyBytes.toRequestBody(call.request.contentType()?.toString()?.toMediaTypeOrNull())
            } else null

            // 构建请求头
            val headersBuilder = Headers.Builder()
            for ((key, values) in call.request.headers.entries()) {
                if (key.startsWith("kano-", ignoreCase = true)) {
                    KanoLog.d(TAG,"代理请求头检测到$key=$values，已去掉前缀")
                    if(key.contains("kano-cookie", ignoreCase = true)) {
                        headersBuilder.add("Cookie", values.first())
                    }else {
                        headersBuilder.addUnsafeNonAscii(key.removePrefix("kano-"), values.first())
                    }
                } else if (isSafeHeader(key)) {
                    headersBuilder.addUnsafeNonAscii(key, values.first())
                }
            }

            // 针对 AList 直链缺少签名（sign/expire）的情况，尝试用配置的插件源换取带签名直链
            fun resolveSignedAlistUrlIfNeeded(original: String): String? {
                return try {
                    val uri = URI(original)
                    val rawQuery = uri.rawQuery ?: ""
                    if (rawQuery.contains("sign=") || rawQuery.contains("expires=") || rawQuery.contains("token=")) return null

                    val segments = uri.path?.split('/')?.filter { it.isNotBlank() } ?: emptyList()
                    val dIdx = segments.indexOf("d")
                    if (dIdx == -1 || dIdx >= segments.lastIndex) return null

                    val prefixSegs = segments.subList(0, dIdx)
                    val afterDSegs = segments.subList(dIdx + 1, segments.size)
                    if (afterDSegs.isEmpty()) return null

                    val origin = buildString {
                        append(uri.scheme).append("://").append(uri.host)
                        if (uri.port != -1) append(":").append(uri.port)
                    }
                    val prefixPath = if (prefixSegs.isEmpty()) "" else "/" + prefixSegs.joinToString("/")

                    val sp = context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
                    val stored = sp.getString("kano_plugin_sources", null)
                    val sources = mutableListOf<JSONObject>()
                    if (!stored.isNullOrBlank()) {
                        try {
                            val arr = JSONArray(stored)
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                sources.add(o)
                            }
                        } catch (_: Exception) {}
                    }

                    for (src in sources) {
                        val srcDownload = src.optString("downloadUrl").trim().trimEnd('/')
                        if (srcDownload.isBlank()) continue
                        val srcUri = try { URI(srcDownload) } catch (_: Exception) { null } ?: continue
                        val srcOrigin = buildString {
                            append(srcUri.scheme).append("://").append(srcUri.host)
                            if (srcUri.port != -1) append(":").append(srcUri.port)
                        }
                        val srcSegs = srcUri.path.split('/').filter { it.isNotBlank() }
                        val sdIdx = srcSegs.indexOf("d")
                        if (sdIdx == -1 || sdIdx >= srcSegs.lastIndex) continue
                        val srcPrefix = srcSegs.subList(0, sdIdx)
                        val srcAlistPath = srcSegs.subList(sdIdx + 1, srcSegs.size)

                        val srcPrefixPath = if (srcPrefix.isEmpty()) "" else "/" + srcPrefix.joinToString("/")
                        if (origin != srcOrigin || prefixPath != srcPrefixPath) continue

                        val cfgPath = src.optString("path").trim('/')
                        val cfgSegs = cfgPath.split('/').filter { it.isNotBlank() }
                        if (cfgSegs != srcAlistPath) continue

                        if (afterDSegs.size < cfgSegs.size) continue
                        val rest = afterDSegs.drop(cfgSegs.size).joinToString("/")
                        if (rest.isBlank()) continue

                        val apiList = src.optString("apiUrl").trim()
                        if (apiList.isBlank()) continue
                        val getUrl = when {
                            apiList.endsWith("/fs/list") -> apiList.removeSuffix("/fs/list") + "/fs/get"
                            apiList.endsWith("/api/fs/list") -> apiList.removeSuffix("/api/fs/list") + "/api/fs/get"
                            else -> apiList.replace("/list", "/get")
                        }

                        val fullPath = "/" + cfgSegs.joinToString("/") + "/" + rest
                        val pwd = src.optString("password", "")
                        val payload = JSONObject().apply {
                            put("path", fullPath)
                            put("password", pwd)
                        }

                        val client = OkHttpClient()
                        val body = payload.toString().toRequestBody("application/json;charset=utf-8".toMediaTypeOrNull())
                        val req = Request.Builder().url(getUrl).post(body).build()
                        client.newCall(req).execute().use { resp ->
                            val b = resp.body?.string()
                            if (!resp.isSuccessful || b.isNullOrBlank()) return@use
                            val obj = JSONObject(b)
                            val data = obj.optJSONObject("data")
                            val raw = data?.optString("raw_url").orEmpty()
                            val url = if (raw.isNotBlank()) raw else data?.optString("url").orEmpty()
                            if (url.isNotBlank()) throw java.lang.RuntimeException("__ALIST_SIGNED__:" + url)
                        }
                    }
                    null
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.startsWith("__ALIST_SIGNED__:")) {
                        return msg.removePrefix("__ALIST_SIGNED__:")
                    }
                    null
                }
            }

            resolveSignedAlistUrlIfNeeded(targetUrl)?.let { signed ->
                targetUrl = signed
            }

            val request = Request.Builder()
                .url(targetUrl)
                .method(method, requestBody)
                .headers(headersBuilder.build())
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body
                val statusCode = response.code
                val contentType = responseBody?.contentType()?.toString()?.let { ContentType.parse(it) }

                // 处理响应头
                response.headers.names().forEach { name ->
                    val lowerName = name.lowercase()
                    val values = response.headers.values(name)

                    if (lowerName == "set-cookie") {
                        values.forEach { rawCookie ->
                            call.response.headers.append("Kano-SetCk", rawCookie)
                            call.response.headers.append("Kano-Set-Cookie", rawCookie)
                        }
                    } else if (isSafeHeader(name)) {
                        values.forEach { value ->
                            call.response.headers.append(name, value)
                        }
                    }
                }

                val origin = call.request.header("Origin") ?: "*"
                if (origin != "*") {
                    call.response.headers.append("Access-Control-Allow-Origin", origin)
                    call.response.headers.append("Access-Control-Allow-Credentials", "true")
                    call.response.headers.append("Access-Control-Expose-Headers", "Kano-SetCk")
                }

                if (responseBody != null) {
                    if (contentType?.match(ContentType.Text.Html) == true) {
                        // HTML 模式，替换资源路径
                        val html = responseBody.string()
                        val baseUrl = targetUrl.substringBeforeLast("/").substringBefore("?")
                        val proxyPrefix = "/api/proxy/--$baseUrl"
                        val rewrittenHtml = html.replace("""(src|href)\s*=\s*["']/(.*?)["']""".toRegex()) {
                            val attr = it.groupValues[1]
                            val path = it.groupValues[2]
                            """$attr="$proxyPrefix/$path""""
                        }
                        call.respondText(rewrittenHtml, contentType, HttpStatusCode.fromValue(statusCode))
                    } else {
                        // 非 HTML，使用流式响应
                        call.respondOutputStream(contentType, HttpStatusCode.fromValue(statusCode)) {
                            responseBody.byteStream().use { input ->
                                input.copyTo(this)
                            }
                        }
                    }
                } else {
                    call.respond(HttpStatusCode.BadGateway, "Empty response body")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadGateway, "Proxy error: ${e.message}")
            }
        }
    }
}
