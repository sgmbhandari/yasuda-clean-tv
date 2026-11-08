package local.cleanhome.tv

import android.test.InstrumentationTestCase
import java.io.IOException

class DeviceTests : InstrumentationTestCase() {
    fun testFirmwareStartupRoutesHomeAndPowerStillSleeps() {
        val context = instrumentation.targetContext
        val manager = context.packageManager
        for (category in listOf("android.intent.category.TIF_TV_HOME", "android.intent.category.TV_HOME")) {
            val startup = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(category)
            val resolved = manager.resolveActivity(startup, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(category, "local.cleanhome.tv", resolved?.activityInfo?.packageName)
            assertEquals(if (category.endsWith("TIF_TV_HOME")) "local.cleanhome.tv.BootHome" else "local.cleanhome.tv.StartupHome", resolved?.activityInfo?.name)
        }
        for ((name, expected) in listOf("config_shortPressOnPowerBehavior" to 3, "config_shortPressOnSleepBehavior" to 1)) {
            val id = context.resources.getIdentifier(name, "integer", "android")
            assertTrue("Firmware power resource missing: $name", id != 0)
            assertEquals(expected, context.resources.getInteger(id))
        }
        val realInputs = manager.getApplicationInfo("com.cvte.tv.api.impl", 0)
        assertTrue("Real TV/input service must remain enabled", realInputs.enabled)
    }

    fun testCatalogueAndBundledImages() {
        val store = WallpaperStore(instrumentation.targetContext)
        assertEquals(24, store.bundled.size)
        assertEquals(24, store.bundled.map { it.id }.toSet().size)
        assertEquals(24, store.bundled.count { it.group == "landscape" })
        assertEquals(0, store.bundled.count { it.group == "abstract" })
        assertTrue(store.bundled.all { store.local(it) })
        store.bundled.filter { store.local(it) }.forEach { image ->
            val bitmap = store.load(image)
            assertTrue(bitmap.width >= 640)
            assertTrue(bitmap.width <= 1920)
            bitmap.recycle()
        }
    }

    fun testMalformedImageRetainsPersistedSelection() {
        val store = WallpaperStore(instrumentation.targetContext)
        val before = store.state()
        try {
            store.decode("<html>not an image</html>".toByteArray())
            fail("Invalid image accepted")
        } catch (_: IOException) { }
        assertEquals(before, store.state())
    }

    fun testBundledFallbacksLocalWithOnlyRequiredNetworkAndOverlayPermissions() {
        val context = instrumentation.targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains("android.permission.INTERNET"))
        assertTrue(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        val store = WallpaperStore(context)
        assertTrue(store.bundled.all { it.source.startsWith("asset:wallpapers/") })
        store.bundled.forEach { store.load(it).recycle() }
    }

    fun testOfflineFallbackAndPersistedPause() {
        val store = WallpaperStore(instrumentation.targetContext)
        val before = store.state()
        try {
            store.save(before.copy(paused = true))
            assertTrue(WallpaperStore(instrumentation.targetContext).state().paused)
            store.bundled.filter { store.local(it) }.forEach { store.load(it).recycle() }
            val remote = store.catalogue.first().copy(source = "https://example.invalid/image.jpg")
            try { store.load(remote); fail("Offline download was attempted") } catch (_: IOException) { }
            assertEquals(before.currentId, store.state().currentId)
        } finally { store.save(before) }
    }
}
