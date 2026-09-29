package com.mono.launcher

/** One launchable activity, as reported by PackageManager. */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val id: String get() = "$packageName/$activityName"
}
