package com.minikano.f50_sms.modules.crontab

import android.content.Context
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.modules.auth.authenticatedRoute
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.ShellKano.Companion.executeShellFromAssetsSubfolderWithArgs
import com.minikano.f50_sms.utils.ShellKano.Companion.killProcessByName
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONObject
import java.io.File
import java.util.UUID

fun Route.crontabModule(context: Context) {
    val TAG = "[$BASE_TAG]_crontabModulee"

    staticFiles("/api/crontab_scripts", File(context.filesDir, "crontab_scripts"))

    authenticatedRoute(context) {
        //上传脚本
        post("/api/upload_crontab_script") {
            try {
                val multipart = call.receiveMultipart()
                var fileName: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val originalFileName = part.originalFileName as String
                            val ext = originalFileName.substringAfterLast('.', "unknow")  // 没有后缀默认
                            fileName = "${UUID.randomUUID()}.$ext"
                            val fileBytes = part.streamProvider().readBytes()
                            val uploadDir = File(context.filesDir, "uploads")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            File(uploadDir, fileName!!).writeBytes(fileBytes)
                        }

                        else -> {}
                    }
                    part.dispose()
                }

                if (fileName != null) {
                    call.response.headers.append("Access-Control-Allow-Origin", "*")
                    val fileUrl = "/uploads/$fileName"

                    call.respondText(
                        """{"url":"$fileUrl"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.OK
                    )
                } else {
                    throw Exception("上传失败")
                }

            } catch (e: Exception) {
                KanoLog.d(TAG, "上传出错： ${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"上传出错: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        //删除脚本
        post("/api/delete_crontab_script") {
            try {
                val body = call.receiveText()
                val json = JSONObject(body)

                val fileName = json.optString("file_name")
                val uploadDir = File(context.filesDir, "uploads/$fileName")

                if (uploadDir.exists()) {
                    uploadDir.delete()
                }

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"success"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )

            } catch (e: Exception) {
                KanoLog.d(TAG, "删除出错： ${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"删除出错: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        //保存crontab定时任务
        post("/api/set_crontab") {
            try {
                val body = call.receiveText()
                val json = JSONObject(body)
//                    backgroundEnabled = json.optString("backgroundEnabled", "false").trim(),

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"test"}""",
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

        //删除crontab定时任务
        post("/api/delete_crontab") {
            try {
                val body = call.receiveText()
                val json = JSONObject(body)
//                    backgroundEnabled = json.optString("backgroundEnabled", "false").trim(),

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"test"}""",
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

        //重启crontab服务
        post("/api/restart_crond"){
            try{
                KanoLog.d("kano_ZTE_LOG", "干掉crond...")
                killProcessByName("kano_crond")
                KanoLog.d("kano_ZTE_LOG", "crond启动中...")
                //启动crond
                var result =
                    executeShellFromAssetsSubfolderWithArgs(
                        context,
                        "shell/kano_crond",
                        "-L",
                        "${context.cacheDir.absolutePath}/kano_cron.log",
//                        "-l",
//                        "8"
                    )
                if (result != null) {
                    KanoLog.d("kano_ZTE_LOG", "crond已启动${result}")
                } else {
                    throw Exception("crond启动失败")
                }

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"success"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )

            } catch (e: Exception) {
                KanoLog.d(TAG, "重启crond服务失败： ${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"重启crond服务失败: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        //读取crontab定时任务
        get("/api/get_crontabs") {
            try {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    "测试中...",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.d(TAG, "读取定时脚本出错： ${e.message}")
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"读取定时脚本出错"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }
}