package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class TvHomeRouteTest {
    @Test fun powerAndStartupReturnHomeButManualSourceDoesNot() {
        assertTrue(TvHomeRoute.home(isTif = true, powerFlag = false, hasSource = false))
        assertTrue(TvHomeRoute.home(isTif = false, powerFlag = true, hasSource = false))
        assertFalse(TvHomeRoute.home(isTif = false, powerFlag = false, hasSource = false))
        assertFalse(TvHomeRoute.home(isTif = false, powerFlag = true, hasSource = true))
        assertFalse(TvHomeRoute.home(isTif = false, powerFlag = false, hasSource = true))
        assertFalse(TvHomeRoute.home(false, true, false, "tv.cloudwalker.apiservice"))
        assertFalse(TvHomeRoute.home(false, true, false, "com.cvte.tv.globalui"))
        assertTrue(TvHomeRoute.home(false, true, false, "android"))
    }
}
