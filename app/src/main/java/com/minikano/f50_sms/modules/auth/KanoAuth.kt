package com.minikano.f50_sms.modules.auth

import android.content.Context
import com.minikano.f50_sms.utils.KanoUtils
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri

object KanoAuth {
    val PREFS_NAME = "kano_ZTE_store"
    val PREF_LOGIN_TOKEN = "login_token"
    val PREF_TOKEN_ENABLED = "login_token_enabled"
    val REQUEST_SECRET_KEY = "minikano_kOyXz0Ciz4V7wR0IeKmJFYFQ20jd"

    fun checkAuth(call: ApplicationCall, context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(PREF_LOGIN_TOKEN, "admin")
        val tokenEnabled = prefs.getString(PREF_TOKEN_ENABLED, "true")?.toBoolean() ?: true

        val method = call.request.httpMethod.value
        val decodedPath = call.request.path()
        val isProxyPath = decodedPath.startsWith("/api/proxy/")
        // 仅对代理下载接口做编码路径兼容（第三方源中文/特殊字符文件名场景）
        val encodedPath = if (isProxyPath) try {
            call.request.uri.substringBefore("?")
        } catch (_: Exception) { decodedPath } else decodedPath

        val apiWhiteList: List<String> = listOf(
            "/api/get_custom_head",
            "/api/version_info",
            "/api/need_token",
            "/api/get_theme",
            "/api/uploads",
            "/api/SELinux",
            "/api/proxy"
        )

        val noAuthRequired = !decodedPath.startsWith("/api/") || apiWhiteList.any { decodedPath.startsWith(it) }

        if (!tokenEnabled || noAuthRequired) return true

        val headers = call.request.headers
        val authHeader = headers["authorization"]
        val timestampStr = headers["kano-t"]
        val clientSignature = headers["kano-sign"]

        // 1) 对 /api/proxy：放宽为“令牌或签名其一满足即可”
        if (isProxyPath) {
            val tokenOk = !authHeader.isNullOrBlank() && !token.isNullOrBlank() && authHeader == KanoUtils.sha256Hex(token)
            if (tokenOk) return true

            // 允许仅凭签名通过（兼容未登录但前端已按规则计算签名的情况）
            if (!timestampStr.isNullOrBlank() && !clientSignature.isNullOrBlank()) {
                val clientTimestamp = timestampStr.toLongOrNull()
                if (clientTimestamp != null) {
                    val rawDecoded = "minikano$method$decodedPath$clientTimestamp"
                    val expectedSignatureDecoded = KanoUtils.HmacSignature(REQUEST_SECRET_KEY, rawDecoded)
                    if (expectedSignatureDecoded.equals(clientSignature, ignoreCase = true)) return true
                    if (encodedPath != decodedPath) {
                        val rawEncoded = "minikano$method$encodedPath$clientTimestamp"
                        val expectedSignatureEncoded = KanoUtils.HmacSignature(REQUEST_SECRET_KEY, rawEncoded)
                        if (expectedSignatureEncoded.equals(clientSignature, ignoreCase = true)) return true
                    }
                }
            }
            return false
        }

        // 2) 其他接口：保持原逻辑（必须令牌+签名）
        if (authHeader.isNullOrBlank() || token.isNullOrBlank()) return false
        if (authHeader != KanoUtils.sha256Hex(token)) return false
        if (timestampStr.isNullOrBlank() || clientSignature.isNullOrBlank()) return false
        val clientTimestamp = timestampStr.toLongOrNull() ?: return false
        val rawDecoded = "minikano$method$decodedPath$clientTimestamp"
        val expectedSignatureDecoded = KanoUtils.HmacSignature(REQUEST_SECRET_KEY, rawDecoded)
        if (expectedSignatureDecoded.equals(clientSignature, ignoreCase = true)) return true
        if (encodedPath != decodedPath) {
            val rawEncoded = "minikano$method$encodedPath$clientTimestamp"
            val expectedSignatureEncoded = KanoUtils.HmacSignature(REQUEST_SECRET_KEY, rawEncoded)
            if (expectedSignatureEncoded.equals(clientSignature, ignoreCase = true)) return true
        }
        return false
    }
}
