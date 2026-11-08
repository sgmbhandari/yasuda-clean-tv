package local.cleanhome.tv

class FocusMemory(var appId: String? = null) {
    fun resolve(installedFavourites: List<String>): String? =
        appId?.takeIf { it in installedFavourites } ?: installedFavourites.firstOrNull()
}
