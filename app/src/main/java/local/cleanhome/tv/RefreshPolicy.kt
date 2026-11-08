package local.cleanhome.tv

import java.net.URI

object RefreshPolicy {
    const val INTERVAL_MS = 48 * 60 * 60 * 1000L
    const val RETRY_MS = 30 * 60 * 1000L
    const val IMAGE_LIMIT = 2 * 1024 * 1024
    const val CACHE_LIMIT = 50 * 1024 * 1024L
    const val BATCH_SIZE = 12

    fun isDue(refreshedAt: Long, now: Long, visible: Boolean, elapsed: Long, retryAfter: Long) =
        visible && elapsed >= retryAfter && (refreshedAt == 0L || now < refreshedAt || now - refreshedAt >= INTERVAL_MS)

    fun allowedUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port in listOf(-1, 443) &&
            ((uri.host == "commons.wikimedia.org" && uri.path == "/w/api.php") ||
                (uri.host in listOf("upload.wikimedia.org", "thumb.wikimedia.org") && uri.path.startsWith("/wikipedia/commons/")))
    } catch (_: Exception) { false }
}
