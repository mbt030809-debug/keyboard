package com.mbt.keyboard

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 업데이트: APK를 내려받아 바로 설치 화면을 연다 (브라우저에 파일만 받아 두지 않는다).
 * 내려받기는 시스템 다운로드 관리자가 하므로 앱에 INTERNET 권한은 필요 없다.
 */
object UpdateInstaller {
    const val APK_NAME = "mbt-keyboard-latest.apk"

    /** @return 내려받기를 시작했으면 true. "출처를 알 수 없는 앱 설치" 허용이 없으면 설정 화면을 열고 false */
    fun start(context: Context, url: String): Boolean {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(app, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { app.startActivity(i) } catch (_: Exception) { }
            return false
        }
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
        File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME).delete()     // 지난 파일 정리
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(url))
                .setTitle(app.getString(R.string.app_name))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, APK_NAME),
        )
        Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                runCatching { app.unregisterReceiver(this) }
                install(app, dm, id)
            }
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED,
        )
        return true
    }

    private fun install(app: Context, dm: DownloadManager, id: Long) {
        val ok = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        } ?: false
        val uri = if (ok) dm.getUriForDownloadedFile(id) else null
        if (uri == null) {
            Toast.makeText(app, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { app.startActivity(i) } catch (_: Exception) {
            Toast.makeText(app, R.string.update_failed, Toast.LENGTH_LONG).show()
        }
    }
}
