package com.offpay.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper for sharing OffPay's own APK via the system share sheet. The
 * approach mirrors what apps like ShareX/Bridgefy do — copy the installed
 * APK from the package's source path into the app's cache, expose it
 * through our FileProvider, and fire ACTION_SEND with `application/vnd.android.package-archive`.
 *
 * Falls back to a text-only "install link" share if the APK can't be read
 * (e.g. split APKs from Play Store, where there's no single .apk file we
 * can attach). The fallback always works.
 */
object ApkShareUtil {

    private const val INSTALL_LINK = "https://offpay.vercel.app/"
    private const val SHARE_BODY = "Try OffPay — UPI payments without internet. " +
        "I'm sending you the APK; web fallback: $INSTALL_LINK"
    private const val FALLBACK_BODY = "Try OffPay — UPI payments without internet. " +
        "Install via the web/PWA at $INSTALL_LINK"

    /**
     * Shares the installed APK file. If the source APK can't be located
     * or copied, falls back to [shareLink] so the user always gets a
     * share sheet to send something useful.
     */
    fun shareInstalledApk(context: Context) {
        val sourceApk = locateSourceApk(context)
        if (sourceApk == null || !sourceApk.exists() || sourceApk.length() == 0L) {
            shareLink(context)
            return
        }

        val cacheCopy = File(context.cacheDir, "OffPay.apk")
        runCatching {
            sourceApk.copyTo(cacheCopy, overwrite = true)
        }.onFailure {
            shareLink(context)
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, authority, cacheCopy)
        }.getOrElse {
            shareLink(context)
            return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, SHARE_BODY)
            putExtra(Intent.EXTRA_SUBJECT, "OffPay")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(Intent.createChooser(send, "Share OffPay"))
        }.onFailure {
            shareLink(context)
        }
    }

    /**
     * Plain-text share fallback. Used when the APK can't be located or
     * the chooser fails — at least the install URL gets sent.
     */
    fun shareLink(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, FALLBACK_BODY)
            putExtra(Intent.EXTRA_SUBJECT, "OffPay")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Share OffPay"))
        }
    }

    /**
     * Tries to find the installed APK file on disk. Returns null when the
     * package was installed as a split-APK bundle (Play Store) — those
     * can't be re-shared as a single .apk.
     */
    private fun locateSourceApk(context: Context): File? {
        val info = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, 0)
        }.getOrNull() ?: return null

        // Standalone APK install — sourceDir points to a single .apk file.
        val source = info.sourceDir ?: return null
        val file = File(source)
        if (file.isFile && file.canRead()) return file

        // Split-APK installs have splitSourceDirs[*]. We can't legally
        // recombine them into one shareable APK without extra signing
        // work, so we bail out and let the caller fall back to a link.
        return null
    }
}
