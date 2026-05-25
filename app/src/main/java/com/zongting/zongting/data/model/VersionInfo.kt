package com.zongting.zongting.data.model

import com.google.gson.annotations.SerializedName

data class VersionInfo(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("downloadUrl") val apkUrl: String,
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
    @SerializedName("channel") val channel: String = ""
)
