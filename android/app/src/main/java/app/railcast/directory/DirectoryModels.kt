package app.railcast.directory

/**
 * In-memory directory records + search results. The bundled index (built by
 * packages/directory, format in FORMAT.md) is decoded into these. [FR-1.1]
 */
sealed interface DirectoryEntry {
    val label: String // primary display text
    val subtitle: String // secondary line (city / route)
    val query: String // resolved value handed to the API layer (code/number)
}

data class Station(
    val code: String,
    val name: String,
    val city: String,
    val state: String,
    val lat: Double?,
    val lng: Double?,
) : DirectoryEntry {
    override val label get() = name
    override val subtitle get() = listOf(city, state).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { code }
    override val query get() = code
}

data class Train(
    val number: String,
    val name: String,
    val fromCode: String,
    val toCode: String,
) : DirectoryEntry {
    override val label get() = name
    override val subtitle get() = "$number · $fromCode → $toCode"
    override val query get() = number
}

/** One ranked hit. Higher [score] = better match; ties break on shorter label. */
data class SearchResult(val entry: DirectoryEntry, val score: Int)
