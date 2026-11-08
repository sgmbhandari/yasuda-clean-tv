package local.cleanhome.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import java.io.IOException

class WallpaperStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val cache = WallpaperCache(java.io.File(context.filesDir, "wallpaper-cache"))
    val bundled: List<Wallpaper> = context.assets.open("catalogue.json").bufferedReader().use { input ->
        val array = JSONArray(input.readText())
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Wallpaper(item.getString("id"), item.getString("group"), item.getString("title"), item.getString("source"),
                item.getString("artist"), item.getString("license"), item.getString("attributionUrl"))
        }
    }
    val catalogue: List<Wallpaper> get() = (bundled + cache.snapshot.images + cache.snapshot.retained).distinctBy { it.id }
    val rotationCatalogue: List<Wallpaper> get() = cache.snapshot.images.ifEmpty { bundled }
    val refreshedAt: Long get() = cache.snapshot.refreshedAt

    fun refresh(now: Long, keepGoing: () -> Boolean): Int {
        val feed = CommonsFeed()
        cache.prepare(state().currentId)
        val before = cache.snapshot
        val (candidates, resetHistory) = feed.candidates(before.seen, keepGoing)
        val selected = mutableListOf<Wallpaper>()
        val currentBatch = before.images.map { it.id }.toSet()
        val started = android.os.SystemClock.elapsedRealtime()
        fun active() = keepGoing() && android.os.SystemClock.elapsedRealtime() - started < 180_000
        try {
            for (ids in candidates.take(60).chunked(5)) {
                if (!active()) throw IOException("Refresh deferred")
                for (image in feed.images(ids, ::active)) {
                    if (image.id in currentBatch || selected.any { it.id == image.id }) continue
                    try {
                        val bytes = feed.download(image, ::active)
                        val decoded = decode(bytes)
                        val valid = decoded.width >= 1280 && decoded.height >= 720 && decoded.width.toFloat() / decoded.height in 1.4f..2.4f
                        decoded.recycle()
                        if (!valid) continue
                        if (!active()) throw IOException("Refresh deferred")
                        selected += cache.staged(image, bytes)
                        if (selected.size == RefreshPolicy.BATCH_SIZE) break
                    } catch (error: IOException) {
                        if (!active()) throw error
                        android.util.Log.w("CleanHome", "Commons image unavailable: ${error.javaClass.simpleName}")
                    }
                }
                if (selected.size == RefreshPolicy.BATCH_SIZE) break
            }
            if (selected.size != RefreshPolicy.BATCH_SIZE || !active()) throw IOException("No complete landscape batch available")
            val current = catalogue.find { it.id == state().currentId }
            val seen = (if (resetHistory) emptySet() else before.seen) + selected.map { it.id.split('_')[1] }
            cache.publish(selected, current, now, seen)
            return selected.size
        } finally { cache.cleanOrphans() }
    }

    fun state() = RotationState(prefs.getString("current_id", "").orEmpty(), prefs.getLong("selected_at", 0),
        list("landscapes"), prefs.getBoolean("paused", false))

    private fun list(key: String) = prefs.getString(key, "").orEmpty().split(',').filter { it.isNotEmpty() }

    fun save(state: RotationState) {
        prefs.edit().putString("current_id", state.currentId).putLong("selected_at", state.changedAt)
            .putString("landscapes", state.seen.joinToString(",")).remove("abstracts").remove("next_group")
            .putBoolean("paused", state.paused).apply()
    }

    fun local(image: Wallpaper): Boolean = image.source.startsWith("asset:wallpapers/") || image.source.startsWith("cache:")

    fun load(image: Wallpaper): Bitmap {
        if (image.source.startsWith("cache:")) return decode(cache.file(image).readBytes())
        if (!local(image) || ".." in image.source) throw IOException("Wallpaper is not available locally")
        return context.assets.open(image.source.removePrefix("asset:")).use { decode(it.readBytes()) }
    }

    fun decode(data: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (!ImagePolicy.valid(data.size.toLong(), bounds.outWidth, bounds.outHeight)) throw IOException("Invalid wallpaper image")
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
            while (bounds.outWidth / (inSampleSize * 2) >= 1920 && bounds.outHeight / (inSampleSize * 2) >= 1080) inSampleSize *= 2
        }
        val raw = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: throw IOException("Cannot decode wallpaper")
        if (raw.width <= 1920 && raw.height <= 1280) return raw
        val scale = minOf(1920f / raw.width, 1280f / raw.height)
        return Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true).also { if (it !== raw) raw.recycle() }
    }
}
