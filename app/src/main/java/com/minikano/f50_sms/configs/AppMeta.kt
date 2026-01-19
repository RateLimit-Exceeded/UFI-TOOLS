package com.minikano.f50_sms.configs

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.KanoUtils
import com.minikano.f50_sms.utils.getBooleanCompat
import java.io.File

object AppMeta {
    var versionName: String = "unknown"
        private set
    var versionCode: Int = 0
        private set
    var model: String = Build.MODEL
        private set
    var isDeviceRooted: Boolean = false
        private set
    var isReadUseTerms: Boolean = false

    var isEnableLog: Boolean = false
        private set

    var GLOBAL_SERVER_URL = "https://pan.kanokano.cn"
        private set

    var isDefaultOrWeakToken = false
        private set

    private const val PREFS_NAME = "kano_ZTE_store"
    private const val GLOBAL_SERVER_URL_KEY = "GLOBAL_SERVER_URL"
    private const val PREF_ISDEBUG = "kano_is_debug"
    private const val PREF_IS_WEAK_TOKEN = "is_weak_token"
    private const val PREF_LOGIN_TOKEN = "login_token"
    private const val PREF_TOKEN_ENABLED = "login_token_enabled"

    fun updateIsDefaultOrWeakToken(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        //持久化
        prefs.edit(commit = true) {
            putBoolean(PREF_IS_WEAK_TOKEN, value)
        }
        isDefaultOrWeakToken = value
    }

    fun setGlobalServerUrl(context: Context, url: String) {
        if (url.isEmpty() || url.isBlank()) throw Exception("url is empty")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) { putString(GLOBAL_SERVER_URL_KEY, url) }
        GLOBAL_SERVER_URL = url
    }

    fun setIsEnableLog(context: Context, flag: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) { putBoolean(PREF_ISDEBUG, flag) }
        isEnableLog = flag
    }

    fun setIsEnableLog(prefs: SharedPreferences, flag: Boolean) {
        prefs.edit(commit = true) { putBoolean(PREF_ISDEBUG, flag) }
        isEnableLog = flag
    }

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            //预处理口令：如果历史存储为明文则转 sha256
            KanoUtils.transformLoginToken(context, prefs)

            // 清除数据后，服务可能会在 MainActivity 初始化口令前自启（BootReceiver/WebService）。
            // 为避免 token 为空导致无法鉴权/TTYD 密码错误，这里保证默认口令存在（默认：admin）。
            val token = prefs.getString(PREF_LOGIN_TOKEN, null)?.trim()
            if (token.isNullOrBlank()) {
                prefs.edit(commit = true) {
                    putString(PREF_LOGIN_TOKEN, KanoUtils.sha256Hex("admin"))
                    putString(PREF_TOKEN_ENABLED, true.toString())
                    putBoolean(PREF_IS_WEAK_TOKEN, true)
                }
            } else {
                // 如果 token 仍是默认 admin（sha256 形式），同步弱口令标记
                val adminHash = KanoUtils.sha256Hex("admin")
                if (token.equals(adminHash, ignoreCase = true) && !prefs.getBoolean(PREF_IS_WEAK_TOKEN, false)) {
                    prefs.edit(commit = true) { putBoolean(PREF_IS_WEAK_TOKEN, true) }
                }
            }

            isDefaultOrWeakToken = prefs.getBoolean(PREF_IS_WEAK_TOKEN, false)

            val globalServerAddress = prefs.getString(GLOBAL_SERVER_URL_KEY, null)
            if (globalServerAddress != null) {
                GLOBAL_SERVER_URL = globalServerAddress
            }

            val pkgInfo = context.applicationContext.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pkgInfo.versionName.toString()

            @Suppress("DEPRECATION")
            versionCode = pkgInfo.versionCode
            model = if (Build.MODEL.contains("MU5352")) "U30 Lite" else Build.MODEL

            val socketPath = File(context.filesDir, "kano_root_shell.sock")
            isDeviceRooted = socketPath.exists()

            isReadUseTerms = prefs.getString("isReadUseTerms", "false").toBoolean()

            isEnableLog = prefs.getBooleanCompat(PREF_ISDEBUG, false)
        } catch (e: Exception) {
            KanoLog.e("UFI_TOOLS_LOG", "AppMeta init failed！！", e)
        }
    }
}
