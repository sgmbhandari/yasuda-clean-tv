package local.cleanhome.tv

import android.test.InstrumentationTestCase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class RefreshTests : InstrumentationTestCase() {
    fun testCachePublicationSurvivesRecreationAndRetainsCurrentImage() {
        val folder = File(instrumentation.targetContext.cacheDir, "refresh-fixture")
        folder.deleteRecursively()
        try {
            val cache = WallpaperCache(folder)
            fun batch(start: Int) = (start until start + 12).map { id ->
                cache.staged(Wallpaper("commons_${id}_abc", "landscape", "Photo $id", "https://upload.wikimedia.org/wikipedia/commons/photo.jpg", "Artist", "CC BY 4.0", "https://commons.wikimedia.org/wiki/File:Photo.jpg"), byteArrayOf(1,2,3))
            }
            val first = batch(1); cache.publish(first, null, 123L, setOf("1"))
            val restored = WallpaperCache(folder)
            assertEquals(123L, restored.snapshot.refreshedAt)
            assertEquals(first, restored.snapshot.images)
            cache.prepare(first.first().id)
            val second = batch(20); cache.publish(second, first.first(), 456L, setOf("20"))
            assertTrue(cache.file(first.first()).exists())
            assertEquals(first, cache.snapshot.retained)
            cache.prepare(first.first().id)
            assertEquals(listOf(first.first()), cache.snapshot.retained)
            assertFalse(cache.file(first.last()).exists())
            assertTrue(cache.size() <= RefreshPolicy.CACHE_LIMIT)
            val partial = batch(40).take(5)
            try { cache.publish(partial, null, 999L, emptySet()); fail("Partial feed replaced good images") } catch (_: IllegalStateException) { }
            cache.cleanOrphans()
            assertEquals(456L, WallpaperCache(folder).snapshot.refreshedAt)
        } finally { folder.deleteRecursively() }
    }

    fun testCorruptCacheAndInvalidPathsCannotReplaceBundledFallbacks() {
        val folder = File(instrumentation.targetContext.cacheDir, "corrupt-refresh-fixture")
        folder.mkdirs(); File(folder, "catalogue.json").writeText("<html>bad response</html>")
        try {
            val cache = WallpaperCache(folder)
            assertTrue(cache.snapshot.images.isEmpty()); assertEquals(0L, cache.snapshot.refreshedAt)
            val image = Wallpaper("bad", "landscape", "Bad", "cache:../../outside.jpg", "", "", "")
            try { cache.file(image); fail("Unsafe cache path") } catch (_: IOException) { }
            assertEquals(24, WallpaperStore(instrumentation.targetContext).bundled.size)
        } finally { folder.deleteRecursively() }
    }

    fun testCommonsMetadataRejectsArtAndPreservesCreditsAndHttps() {
        val info = JSONObject().put("thumburl", "https://upload.wikimedia.org/wikipedia/commons/a/ab/Photo.jpg")
            .put("thumbwidth",1920).put("thumbheight",1080).put("mime","image/jpeg").put("sha1","abc123")
            .put("descriptionurl","https://commons.wikimedia.org/wiki/File:Photo.jpg")
            .put("extmetadata",JSONObject().put("Artist",JSONObject().put("value","<b>Photographer</b>"))
                .put("LicenseShortName",JSONObject().put("value","CC BY-SA 4.0")))
        val page = JSONObject().put("pageid",1).put("title","File:Quiet mountain.jpg").put("imageinfo",JSONArray().put(info))
        val response = JSONObject().put("query",JSONObject().put("pages",JSONObject().put("1",page)))
        val feed = CommonsFeed { _, _, _ -> response.toString().toByteArray() }
        val image = feed.images(listOf(1), { true }).single()
        assertEquals("Photographer", image.artist); assertEquals("CC BY-SA 4.0",image.license)
        page.put("categories",JSONArray().put(JSONObject().put("title","Category:AI-generated landscapes")))
        assertTrue(feed.images(listOf(1), { true }).isEmpty())
        page.remove("categories");info.put("thumburl","https://example.invalid/tracker.jpg")
        assertTrue(feed.images(listOf(1), { true }).isEmpty())
    }
}
