package com.minikano.f50_sms.modules.theme

import android.content.Context
import androidx.core.content.edit
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.modules.auth.authenticatedRoute
import com.minikano.f50_sms.utils.KanoLog
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class ThemeConfig(
    val backgroundEnabled: String = "false",
    val backgroundUrl: String = "",
    val textColor: String = "rgba(255, 255, 255, 1)",
    val textColorPer: String = "100",
    val themeColor: String = "201",
    val colorPer: String = "67",
    val saturationPer: String = "100",
    val brightPer: String = "21",
    val opacityPer: String = "21",
    val blurSwitch: String = "true",
    val overlaySwitch: String = "true"
)

val jsonFull = Json {
    encodeDefaults = true
    prettyPrint = false
    ignoreUnknownKeys = true
}

fun Route.themeModule(context: Context) {
    val tag = "[$BASE_TAG]_themeModule"
    val uploadRoot = File(context.filesDir, "uploads")

    get("/api/uploads/{...}") {
        val relativePath = (call.parameters["..."]
            ?: call.request.path().removePrefix("/api/uploads/"))
            .trim('/')

        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains('\u0000')) {
            call.respondText("403 Forbidden", status = HttpStatusCode.Forbidden)
            return@get
        }

        val targetFile = File(uploadRoot, relativePath)

        val baseCanonical = uploadRoot.canonicalFile
        val targetCanonical = targetFile.canonicalFile
        val inRoot = targetCanonical.path == baseCanonical.path ||
            targetCanonical.path.startsWith(baseCanonical.path + File.separator)

        if (!inRoot) {
            call.respondText("403 Forbidden", status = HttpStatusCode.Forbidden)
            return@get
        }

        try {
            if (!targetFile.exists() || !targetFile.isFile) {
                call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respondFile(targetFile)
        } catch (e: SecurityException) {
            KanoLog.e(tag, "read uploads forbidden: $relativePath", e)
            call.respondText("403 Forbidden", status = HttpStatusCode.Forbidden)
        } catch (e: FileNotFoundException) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            val isAccessDenied = rootCause.message?.contains("EACCES", ignoreCase = true) == true
            call.respondText(
                if (isAccessDenied) "403 Forbidden" else "404 Not Found",
                status = if (isAccessDenied) HttpStatusCode.Forbidden else HttpStatusCode.NotFound
            )
        } catch (e: Exception) {
            KanoLog.e(tag, "read uploads failed: $relativePath", e)
            call.respondText("500 Internal Server Error", status = HttpStatusCode.InternalServerError)
        }
    }

    authenticatedRoute(context) {
        post("/api/upload_img") {
            try {
                val multipart = call.receiveMultipart()
                var fileName: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val originalFileName = part.originalFileName as String
                            val ext = originalFileName.substringAfterLast('.', "jpg")
                            fileName = "${UUID.randomUUID()}.$ext"

                            if (!uploadRoot.exists()) uploadRoot.mkdirs()
                            val outFile = File(uploadRoot, fileName!!)

                            part.streamProvider().use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }

                        else -> {}
                    }
                    part.dispose()
                }

                if (fileName.isNullOrBlank()) throw Exception("upload failed")

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val fileUrl = "/uploads/$fileName"
                call.respondText(
                    """{"url":"$fileUrl"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.e(tag, "upload_img failed: ${e.message}", e)
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"upload failed: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        post("/api/delete_img") {
            try {
                val body = call.receiveText()
                val json = JSONObject(body)

                val fileName = json.optString("file_name").trim()
                if (fileName.isBlank() || fileName.contains("..") || fileName.startsWith("/")) {
                    call.respondText(
                        """{"error":"非法文件名"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden
                    )
                    return@post
                }

                val baseDir = uploadRoot
                val target = File(baseDir, fileName)

                val baseCanonical = baseDir.canonicalPath.trimEnd(File.separatorChar)
                val targetCanonical = target.canonicalPath
                val inCanonicalRoot = targetCanonical == baseCanonical ||
                    targetCanonical.startsWith("$baseCanonical${File.separator}")

                val baseAbsolute = baseDir.absolutePath.trimEnd(File.separatorChar)
                val targetAbsolute = target.absolutePath
                val inAbsoluteRoot = targetAbsolute == baseAbsolute ||
                    targetAbsolute.startsWith("$baseAbsolute${File.separator}")

                if (!inCanonicalRoot && !inAbsoluteRoot) {
                    call.respondText(
                        """{"error":"非法路径"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden
                    )
                    return@post
                }

                if (target.exists() && target.isFile) target.delete()

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"success"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.e(tag, "delete_img failed: ${e.message}", e)
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"delete failed: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        @Serializable
        data class DeleteAllUploadsResp(
            val result: String,
            val deleted_list: Map<String, Boolean>
        )

        post("/api/delete_all_uploads_data") {
            try {
                val result = mutableMapOf<String, Boolean>()
                val files = uploadRoot.listFiles()
                if (files != null && files.isNotEmpty()) {
                    files.forEach { file ->
                        if (file.isFile) {
                            result[file.name] = try {
                                file.delete()
                            } catch (e: Exception) {
                                KanoLog.e(tag, "delete file failed: ${file.name}", e)
                                false
                            }
                        }
                    }
                }

                val payload = DeleteAllUploadsResp(
                    result = "success",
                    deleted_list = result
                )

                call.respondText(
                    Json.encodeToString(payload),
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.e(tag, "delete_all_uploads_data failed: ${e.message}", e)
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"delete failed: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }

        post("/api/set_theme") {
            try {
                val body = call.receiveText()
                val json = JSONObject(body)

                val config = ThemeConfig(
                    backgroundEnabled = json.optString("backgroundEnabled", "false").trim(),
                    backgroundUrl = json.optString("backgroundUrl", "").trim(),
                    textColor = json.optString("textColor", "rgba(255, 255, 255, 1)").trim(),
                    textColorPer = json.optString("textColorPer", "100").trim(),
                    themeColor = json.optString("themeColor", "201").trim(),
                    colorPer = json.optString("colorPer", "67").trim(),
                    saturationPer = json.optString("saturationPer", "100").trim(),
                    brightPer = json.optString("brightPer", "21").trim(),
                    opacityPer = json.optString("opacityPer", "21").trim(),
                    blurSwitch = json.optString("blurSwitch", "true").trim(),
                    overlaySwitch = json.optString("overlaySwitch", "true").trim()
                )

                val jsonStore = jsonFull.encodeToString(config)

                val sharedPref =
                    context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
                sharedPref.edit(commit = true) {
                    putString("kano_theme", jsonStore)
                }

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"result":"success"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            } catch (e: Exception) {
                KanoLog.e(tag, "set_theme failed: ${e.message}", e)
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.respondText(
                    """{"error":"set_theme failed: ${e.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }

    get("/api/get_theme") {
        try {
            val sharedPref = context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            val kanoTheme = sharedPref.getString("kano_theme", null)
            val json = try {
                kanoTheme?.let { JSONObject(it) }
            } catch (_: Exception) {
                null
            }

            val config = if (json != null && json.length() > 0) {
                ThemeConfig(
                    backgroundEnabled = json.optString("backgroundEnabled", "false").trim(),
                    backgroundUrl = json.optString("backgroundUrl", "").trim(),
                    textColor = json.optString("textColor", "rgba(255, 255, 255, 1)").trim(),
                    textColorPer = json.optString("textColorPer", "100").trim(),
                    themeColor = json.optString("themeColor", "201").trim(),
                    colorPer = json.optString("colorPer", "67").trim(),
                    saturationPer = json.optString("saturationPer", "100").trim(),
                    brightPer = json.optString("brightPer", "21").trim(),
                    opacityPer = json.optString("opacityPer", "21").trim(),
                    blurSwitch = json.optString("blurSwitch", "true").trim(),
                    overlaySwitch = json.optString("overlaySwitch", "true").trim()
                )
            } else {
                ThemeConfig()
            }

            val text = jsonFull.encodeToString(config)
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                text,
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.e(tag, "get_theme failed: ${e.message}", e)
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"get_theme failed"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }
}
