package local.cleanhome.tv

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class WallpaperCache(private val folder: File) {
    data class Snapshot(val images: List<Wallpaper> = emptyList(), val retained: List<Wallpaper> = emptyList(),
                        val refreshedAt: Long = 0, val seen: Set<String> = emptySet())
    private val manifest = AtomicFile(File(folder, "catalogue.json"))
    @Volatile var snapshot = Snapshot()
        private set

    init {
        folder.mkdirs()
        snapshot = try {
            val json = JSONObject(String(manifest.readFully(), Charsets.UTF_8))
            val array = json.getJSONArray("images")
            val images = (0 until array.length()).map { parse(array.getJSONObject(it)) }.filter { file(it).isFile }
            val older = json.optJSONArray("retained") ?: JSONArray()
            val retained = (0 until older.length()).map { parse(older.getJSONObject(it)) }.filter { file(it).isFile }
            val seen = json.optJSONArray("seen") ?: JSONArray()
            Snapshot(images, retained, if (images.size == RefreshPolicy.BATCH_SIZE) json.getLong("refreshedAt") else 0,
                (0 until seen.length()).map { seen.getString(it) }.toSet())
        } catch (_: Exception) { Snapshot() }
        cleanOrphans()
    }

    fun file(image: Wallpaper): File {
        val name = image.source.removePrefix("cache:")
        if (!image.source.startsWith("cache:") || !name.matches(Regex("commons_[0-9]+_[a-z0-9]+\\.jpg"))) throw IOException("Invalid cache path")
        return File(folder, name)
    }

    fun prepare(currentId: String) {
        val before = snapshot
        val retained = before.retained.filter { it.id == currentId }
        val json = JSONObject().put("refreshedAt", before.refreshedAt).put("images", JSONArray(before.images.map { encode(it) }))
            .put("retained", JSONArray(retained.map { encode(it) })).put("seen", JSONArray(before.seen.toList()))
        val output = manifest.startWrite()
        try { output.write(json.toString().toByteArray(Charsets.UTF_8)); manifest.finishWrite(output) }
        catch (error: Exception) { manifest.failWrite(output); throw error }
        snapshot = before.copy(retained = retained)
        cleanOrphans()
    }

    fun staged(image: Wallpaper, bytes: ByteArray): Wallpaper {
        val local = image.copy(source = "cache:${image.id}.jpg")
        if (bytes.size > RefreshPolicy.IMAGE_LIMIT || bytes.isEmpty()) throw IOException("Image exceeds cache budget")
        val destination = file(local)
        if (!destination.exists()) {
            val temp = File(folder, destination.name + ".part")
            try {
                if (size() + bytes.size > RefreshPolicy.CACHE_LIMIT) throw IOException("Wallpaper cache full")
                temp.outputStream().use { it.write(bytes); it.fd.sync() }
                if (!temp.renameTo(destination)) throw IOException("Cannot save wallpaper")
            } finally { temp.delete() }
        }
        return local
    }

    @Synchronized fun publish(images: List<Wallpaper>, current: Wallpaper?, now: Long, seen: Set<String>) {
        check(images.size == RefreshPolicy.BATCH_SIZE && images.all { file(it).isFile })
        val retained = (snapshot.images + listOfNotNull(current?.takeIf { it.source.startsWith("cache:") })).distinctBy { it.id }
            .filter { it.id !in images.map { entry -> entry.id } && file(it).isFile }
        val next = Snapshot(images, retained, now, seen)
        val json = JSONObject().put("refreshedAt", now).put("images", JSONArray(images.map { encode(it) }))
            .put("seen", JSONArray(seen.toList()))
        json.put("retained", JSONArray(retained.map { encode(it) }))
        val output = manifest.startWrite()
        try { output.write(json.toString().toByteArray(Charsets.UTF_8)); manifest.finishWrite(output) }
        catch (error: Exception) { manifest.failWrite(output); throw error }
        snapshot = next
        cleanOrphans()
    }

    fun cleanOrphans() {
        val keep = (snapshot.images + snapshot.retained).map { file(it).name }.toSet()
        folder.listFiles().orEmpty().filter { it.name.endsWith(".part") || (it.name.endsWith(".jpg") && it.name !in keep) }.forEach { it.delete() }
    }

    fun size() = folder.listFiles().orEmpty().filter { it.name.endsWith(".jpg") || it.name.endsWith(".part") }.sumOf { it.length() }
    private fun encode(image: Wallpaper) = JSONObject().put("id", image.id).put("title", image.title).put("source", image.source)
        .put("artist", image.artist).put("license", image.license).put("attributionUrl", image.attributionUrl)
    private fun parse(json: JSONObject) = Wallpaper(json.getString("id"), "landscape", json.getString("title"), json.getString("source"),
        json.getString("artist"), json.getString("license"), json.getString("attributionUrl"))
}
