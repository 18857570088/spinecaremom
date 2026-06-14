package com.zclei.spinecaremom

object ApiConfig {
    const val projectCode: String = BuildConfig.PROJECT_CODE
    const val baseUrl: String = BuildConfig.API_BASE_URL

    fun endpoint(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return "$cleanBase/$cleanPath"
    }
}
