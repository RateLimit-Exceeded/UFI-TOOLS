package com.minikano.f50_sms.modules.ota

import android.content.Context
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.utils.KanoLog
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

object ApkState {
    var downloadResultPath: String? = null
    var downloadInProgress = false
    var download_percent = 0
    var downloadError: String? = null
    var currentDownloadingUrl: String = ""
}

fun Route.otaModule(context: Context) {
    val TAG = "[$BASE_TAG]_OTAModule"

    //检查更新
    get("/api/check_update") {
        KanoLog.d(TAG, "OTA 检查已禁用")
        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(
            """{"error":"ota_disabled"}""",
            ContentType.Application.Json,
            HttpStatusCode.OK
        )
    }

    //从URL下载APK
    post("/api/download_apk") {
        KanoLog.d(TAG, "OTA 下载已禁用")
        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(
            """{"error":"ota_disabled"}""",
            ContentType.Application.Json,
            HttpStatusCode.OK
        )
    }

    //下载进度
    get("/api/download_apk_status") {
        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(
            """{"status":"disabled","percent":0,"error":"ota_disabled"}""",
            ContentType.Application.Json
        )
    }

    //安装APK
    post("/api/install_apk") {
        KanoLog.d(TAG, "OTA 安装已禁用")
        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(
            """{"error":"ota_disabled"}""",
            ContentType.Application.Json,
            HttpStatusCode.OK
        )
    }

}
