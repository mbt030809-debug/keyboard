package com.mbt.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {
    private const val BRANCH = "ccr-131ce975-kvfk2l"
    private const val VERSION_URL = "https://raw.githubusercontent.com/mbt030809-debug/keyboard/$BRANCH/app/version.properties"
    private var checkedThisSession = false
    private var pendingApk: File? = null

    fun checkAndUpdate(activity: Activity) {
        if (checkedThisSession) return
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return
        checkedThisSession = true
        val currentBuild = try {
            val pi = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
        } catch (_: Throwable) {
            return
        }
        Thread {
            try {
                val props = fetchText(VERSION_URL) ?: return@Thread
                val remoteBuild = Regex("""build\s*=\s*(\d+)""").find(props)?.groupValues?.get(1)?.toIntOrNull() ?: return@Thread
                if (remoteBuild <= currentBuild) return@Thread
                val apkUrl = "https://github.com/mbt030809-debug/keyboard/raw/refs/heads/$BRANCH/dist/mbt-keyboard-b$remoteBuild-20261124.apk"
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(dir, "mbt-keyboard-b$remoteBuild.apk")
                if (!outFile.exists() || outFile.length() < 100_000L) {
                    if (!urlExists(apkUrl)) return@Thread
                    activity.runOnUiThread {
                        if (!activity.isFinishing) {
                            Toast.makeText(activity, "새 버전(b$remoteBuild) 다운로드 중...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (!download(apkUrl, outFile)) return@Thread
                }
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        launchInstall(activity, outFile)
                    }
                }
            } catch (_: Throwable) {
            }
        }.start()
    }

    fun resumePendingInstall(activity: Activity) {
        val apk = pendingApk ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            pendingApk = null
            launchInstall(activity, apk)
        }
    }

    private fun launchInstall(activity: Activity, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            pendingApk = apkFile
            MaterialAlertDialogBuilder(activity)
                .setTitle("앱 자동 업데이트")
                .setMessage("새 버전을 바로 설치하려면 '출처를 알 수 없는 앱 설치' 권한을 한 번만 허용해 주세요.")
                .setPositiveButton("허용하러 가기") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    activity.startActivity(intent)
                }
                .setNegativeButton("나중에") { _, _ -> pendingApk = null }
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun fetchText(urlStr: String): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
        }
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun urlExists(urlStr: String): Boolean {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            connectTimeout = 4000
            readTimeout = 4000
        }
        return try {
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    private fun download(urlStr: String, dest: File): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8000
            readTimeout = 30000
        }
        return try {
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() < 100_000L) { tmp.delete(); return false }
            tmp.renameTo(dest)
        } finally {
            conn.disconnect()
        }
    }
}
