package com.minikano.f50_sms.utils

import android.content.Context
import android.os.Build
import android.util.Log

object DeviceModelChecker {
    private var isUnSupportDevice = false
    private val devicesBlackList = listOf(
        "MU5352"
    )
    private val PREFS_NAME = "kano_ZTE_store"
    private val frimwareWhiteList = listOf(
        "MU5352_DSV1.0.0B07",
        "MU5352_DSV1.0.0B05",
        "MU5352_DSV1.0.0B03",
        "MU300",
        "F50",
        "U30Air",
    )

    suspend fun checkBlackList(context:Context): Boolean {
        Log.d("kano_ZTE_LOG_devcheck", "正在遍历黑名单设备...")
        val model = Build.MODEL.trim()
        val firmwareVersion = Build.DISPLAY

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDeviceWhiteList = prefs.getString("is_device_white_list", null)
        if (!isDeviceWhiteList.isNullOrEmpty()) {
            if(isDeviceWhiteList == "kano") {
                Log.d("kano_ZTE_LOG_devcheck", "线上白名单(已持久化)，永久放行")
                return false
            } else {
                Log.d("kano_ZTE_LOG_devcheck", "错误的白名单字符串，跳过")
                prefs.edit().remove("is_device_white_list").apply()
            }
        }

        val uuid = UniqueDeviceIDManager.getUUID()
        Log.d("kano_ZTE_LOG_devcheck", "当前设备UUID:$uuid，已跳过远程白名单校验")
        isUnSupportDevice = false
        Log.d("kano_ZTE_LOG_devcheck", "已禁用本地黑名单限制，允许继续运行")
        return false
    }

    fun checkIsNotUFI(context: Context):Boolean{
        val isUFI_0 = KanoUtils.isAppInstalled(context,"com.zte.web")
        val isUFI = ShellKano.runShellCommand("pm list package")
        Log.d("kano_ZTE_LOG_devcheck", "isUFI_0：${isUFI_0},has com.zte.web? :${isUFI?.contains("com.zte.web")} ，已跳过设备类型限制")
        return false
    }
}
