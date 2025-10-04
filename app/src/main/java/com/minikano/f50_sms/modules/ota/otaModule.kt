package com.minikano.f50_sms.modules.ota

import android.content.Context
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.utils.KanoLog
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

@Suppress("UNUSED_PARAMETER")
fun Route.otaModule(context: Context) {
    val TAG = "[$BASE_TAG]_OTAModule"
    val disabledResponse = """{"error":"ota_disabled"}"""

    get("/api/check_update") {
        KanoLog.d(TAG, "OTA endpoint disabled: /api/check_update")
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(disabledResponse, ContentType.Application.Json, HttpStatusCode.OK)
    }

    post("/api/download_apk") {
        KanoLog.d(TAG, "OTA endpoint disabled: /api/download_apk")
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(disabledResponse, ContentType.Application.Json, HttpStatusCode.OK)
    }

    get("/api/download_apk_status") {
        KanoLog.d(TAG, "OTA endpoint disabled: /api/download_apk_status")
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        val status = """{"status":"disabled","percent":0,"error":"ota_disabled"}"""
        call.respondText(status, ContentType.Application.Json, HttpStatusCode.OK)
    }

    post("/api/install_apk") {
        KanoLog.d(TAG, "OTA endpoint disabled: /api/install_apk")
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(disabledResponse, ContentType.Application.Json, HttpStatusCode.OK)
    }
}
