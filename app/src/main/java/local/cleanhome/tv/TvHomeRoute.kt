package local.cleanhome.tv

object TvHomeRoute {
    private val sourceMenus = setOf("tv.cloudwalker.apiservice", "com.cvte.tv.globalui")
    fun home(isTif: Boolean, powerFlag: Boolean, hasSource: Boolean, caller: String? = null) =
        isTif || (powerFlag && !hasSource && caller !in sourceMenus)
}
