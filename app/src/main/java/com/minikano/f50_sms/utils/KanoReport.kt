package com.minikano.f50_sms.utils

class KanoReport {
    companion object {
        suspend fun reportToServer() {
            KanoLog.d("kano_ZTE_LOG_report_service", "远程上报功能已禁用")
        }

        data class Report(
            val id: Long?,
            val uuid: String,
            val deviceName: String?,
            val appVer: String?,
            val firmwareVer: String?,
            val requestTime: String?,
            val isRoot: Boolean,
            val isWhiteList: Boolean
        )

        suspend fun getRemoteDeviceRegisterItem(uuid: String): Report? {
            KanoLog.d("kano_ZTE_LOG_devcheck", "远程白名单校验已禁用，uuid=$uuid")
            return null
        }
    }
}
