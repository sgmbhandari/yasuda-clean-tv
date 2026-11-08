package local.cleanhome.tv

import java.util.Random

data class Wallpaper(val id: String, val group: String, val title: String, val source: String,
                     val artist: String, val license: String, val attributionUrl: String)

data class RotationState(
    val currentId: String = "",
    val changedAt: Long = 0,
    val seen: List<String> = emptyList(),
    val paused: Boolean = false
)

class Rotation(private val catalogue: List<Wallpaper>, private val random: Random = Random()) {
    fun choose(state: RotationState, excluded: Set<String> = emptySet()): Wallpaper {
        val all = catalogue.filter { it.id !in excluded }
        check(all.isNotEmpty()) { "No readable wallpapers remain" }
        val fresh = all.filter { it.id !in state.seen }
        val candidates = fresh.ifEmpty { all }.let { values -> values.filter { it.id != state.currentId }.ifEmpty { values } }
        return candidates[random.nextInt(candidates.size)]
    }

    fun commit(state: RotationState, image: Wallpaper, now: Long): RotationState {
        val history = if (image.id in state.seen) listOf(image.id) else state.seen + image.id
        return state.copy(currentId = image.id, changedAt = now, seen = history)
    }

    companion object {
        const val INTERVAL_MS = 30 * 60 * 1000L
        fun isDue(state: RotationState, now: Long): Boolean = state.currentId.isEmpty() ||
            (!state.paused && (now < state.changedAt || now - state.changedAt >= INTERVAL_MS))
    }
}

object Favourites {
    fun visible(saved: List<String>, installed: Set<String>) = saved.distinct().filter { it in installed }
    fun move(saved: List<String>, id: String, direction: Int, installed: Set<String> = saved.toSet()): List<String> {
        val items = saved.toMutableList()
        val from = items.indexOf(id)
        if (from < 0) return saved
        val visible = visible(saved, installed)
        val position = visible.indexOf(id)
        if (position < 0) return saved
        val neighbour = visible[(position + direction).coerceIn(0, visible.lastIndex)]
        val to = items.indexOf(neighbour)
        items.removeAt(from)
        items.add(to, id)
        return items
    }
}

object ImagePolicy {
    const val MAX_DOWNLOAD = 8 * 1024 * 1024L
    fun valid(bytes: Long, width: Int, height: Int) = bytes in 1..MAX_DOWNLOAD &&
        width >= 640 && height >= 360 && width.toLong() * height <= 100_000_000
}
