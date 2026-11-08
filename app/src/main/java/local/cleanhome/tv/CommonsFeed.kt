package local.cleanhome.tv

import android.text.Html
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class CommonsFeed(private val request: (String, Int, () -> Boolean) -> ByteArray = ::fetch) {
    fun candidates(seen: Set<String>, keepGoing: () -> Boolean): Pair<List<Long>, Boolean> {
        val data = query(mapOf("list" to "categorymembers", "cmtitle" to "Category:Featured pictures of landscapes",
            "cmtype" to "file", "cmnamespace" to "6", "cmlimit" to "500", "cmsort" to "timestamp", "cmdir" to "desc"), keepGoing)
        val members = data.getJSONObject("query").getJSONArray("categorymembers")
        val ids = (0 until members.length()).map { members.getJSONObject(it).getLong("pageid") }.distinct()
        val fresh = ids.filter { it.toString() !in seen }
        return (if (fresh.size >= RefreshPolicy.BATCH_SIZE) fresh else ids).shuffled() to (fresh.size < RefreshPolicy.BATCH_SIZE)
    }

    fun images(ids: List<Long>, keepGoing: () -> Boolean): List<Wallpaper> {
        val data = query(mapOf("pageids" to ids.joinToString("|"), "prop" to "imageinfo|categories", "cllimit" to "max",
            "iiprop" to "url|size|mime|extmetadata|sha1", "iiurlwidth" to "1920",
            "iiextmetadatafilter" to "Artist|LicenseShortName|LicenseUrl|ImageDescription|ObjectName"), keepGoing)
        val pages = data.getJSONObject("query").getJSONObject("pages")
        return pages.keys().asSequence().mapNotNull { key -> parse(pages.getJSONObject(key)) }.toList()
    }

    fun download(image: Wallpaper, keepGoing: () -> Boolean): ByteArray = request(image.source, RefreshPolicy.IMAGE_LIMIT, keepGoing)

    private fun query(params: Map<String, String>, keepGoing: () -> Boolean): JSONObject {
        val all = mapOf("action" to "query", "format" to "json", "maxlag" to "5") + params
        val url = "https://commons.wikimedia.org/w/api.php?" + all.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val json = JSONObject(String(request(url, 512 * 1024, keepGoing), Charsets.UTF_8))
        if (json.has("error")) throw IOException("Commons API unavailable")
        return json
    }

    private fun parse(page: JSONObject): Wallpaper? {
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val metadata = info.optJSONObject("extmetadata") ?: return null
        fun field(name: String) = Html.fromHtml(metadata.optJSONObject(name)?.optString("value").orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
        val title = page.optString("title").removePrefix("File:")
        val categories = page.optJSONArray("categories")?.toString().orEmpty()
        if (Regex("AI.generated|artificial intelligence|paintings|drawings|illustrations|photomontages", RegexOption.IGNORE_CASE)
                .containsMatchIn(title + categories + field("ImageDescription"))) return null
        val license = field("LicenseShortName")
        if (!(license.startsWith("CC BY") || license.startsWith("CC0") || license == "Public domain")) return null
        val artist = field("Artist").take(500)
        val url = info.optString("thumburl").substringBefore('?')
        val width = info.optInt("thumbwidth")
        val height = info.optInt("thumbheight")
        val ratio = width.toDouble() / height.coerceAtLeast(1)
        val sha = info.optString("sha1")
        if (artist.isBlank() || sha.isBlank() || info.optString("mime") != "image/jpeg" ||
            width < 1280 || height < 720 || ratio !in 1.4..2.4 || !RefreshPolicy.allowedUrl(url)) return null
        val attribution = info.optString("descriptionurl")
        if (!attribution.startsWith("https://commons.wikimedia.org/wiki/File:")) return null
        return Wallpaper("commons_${page.getLong("pageid")}_${sha.take(12)}", "landscape",
            title.substringBeforeLast('.').replace('_', ' ').take(160), url, artist, license, attribution)
    }

    companion object {
        private fun fetch(url: String, limit: Int, keepGoing: () -> Boolean): ByteArray {
            var next = url
            repeat(4) {
                if (!keepGoing() || Thread.currentThread().isInterrupted) throw IOException("Refresh deferred")
                if (!RefreshPolicy.allowedUrl(next)) throw IOException("Untrusted wallpaper URL")
                val connection = URL(next).openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "CleanHomeTV/1.3 (personal landscape viewer; https://www.mediawiki.org/wiki/API:Etiquette)")
                    val code = connection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        next = URL(URL(next), connection.getHeaderField("Location") ?: throw IOException("Redirect missing destination")).toString()
                    } else {
                        if (code != 200 || connection.contentLengthLong > limit) throw IOException("Wallpaper response rejected: $code")
                        val started = android.os.SystemClock.elapsedRealtime()
                        return connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                if (!keepGoing() || Thread.currentThread().isInterrupted || android.os.SystemClock.elapsedRealtime() - started > 30_000)
                                    throw IOException("Refresh deferred")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > limit) throw IOException("Wallpaper response too large")
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    }
                } finally { connection.disconnect() }
            }
            throw IOException("Too many wallpaper redirects")
        }
    }
}
