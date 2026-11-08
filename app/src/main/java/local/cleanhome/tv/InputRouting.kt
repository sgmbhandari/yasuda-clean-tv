package local.cleanhome.tv

object InputRouting {
    fun sourceId(inputId: String): Int? {
        val parts = inputId.split('/')
        if (parts.size != 3 || !parts.last().startsWith("HW")) return null
        return parts.last().removePrefix("HW").toIntOrNull()?.takeIf { it >= 0 }
    }
}
