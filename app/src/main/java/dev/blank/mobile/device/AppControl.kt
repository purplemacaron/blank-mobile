package dev.blank.mobile.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.delay

class AppControl(private val context: Context) {
    fun list(): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                ri.loadLabel(pm).toString() to pkg
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        if (apps.isEmpty()) return "No launchable apps found"
        val shown = apps.take(60)
        val lines = shown.joinToString("\n") { "- ${it.first} (${it.second})" }
        val more = if (apps.size > shown.size) "\n… and ${apps.size - shown.size} more" else ""
        return "Installed apps (${apps.size}):\n$lines$more"
    }

    fun launch(pkg: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "No launchable app for package $pkg"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Launched $pkg"
    }

    fun install(path: String): String {
        val file = File(path)
        apkReadError(file)?.let { return it }
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            return "Cannot share $path — only files in shared storage or app directories can be installed"
        }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return "Install confirmation opened for ${file.name}. The user must approve it on screen."
    }

    fun uninstall(pkg: String): String {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Uninstall confirmation opened for $pkg. The user must approve it on screen."
    }

    suspend fun forceStop(pkg: String): String {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        delay(1200)
        if (!DeviceBridge.tapByLabels(listOf("강제 종료", "Force stop"))) {
            return "Opened app info for $pkg but could not find the force-stop button. Tap it manually."
        }
        delay(800)
        val confirmed = DeviceBridge.tapByLabels(listOf("확인", "OK"))
        return if (confirmed) "Force-stopped $pkg"
        else "Tapped force-stop for $pkg; the confirmation dialog may still be open."
    }

    companion object {
        // Scoped storage lets us stat files we cannot open; probe before handing
        // the URI to the installer so failures are honest instead of "parse error".
        fun apkReadError(file: File): String? {
            if (!file.exists()) return "APK not found: ${file.absolutePath}"
            return try {
                val first = file.inputStream().use { it.read() }
                if (first == -1) "APK is empty: ${file.name}" else null
            } catch (_: Exception) {
                "Cannot read ${file.name} — call install without a path so the user can pick the file"
            }
        }
    }
}