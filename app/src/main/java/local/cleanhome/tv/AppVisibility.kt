package local.cleanhome.tv

/** Launcher presentation only. Never disables or uninstalls a package. */
object AppVisibility {
    fun visible(installed: List<String>, hidden: Set<String>) = installed.filterNot { it in hidden }
}
