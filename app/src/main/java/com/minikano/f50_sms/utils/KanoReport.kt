package com.minikano.f50_sms.utils

class KanoReport {
    companion object {
        suspend fun reportToServer() {
            KanoLog.d("kano_ZTE_LOG_report_service", "已禁用远程上报，跳过")
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
            KanoLog.d("kano_ZTE_LOG_devcheck", "已禁用远程白名单校验，uuid=$uuid")
            return null
        }
    }
}
