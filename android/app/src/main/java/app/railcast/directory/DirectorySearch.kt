package app.railcast.directory

/**
 * Fuzzy, typo-tolerant, offline ranking over the bundled directory. Users type
 * a name OR a number/code; results resolve to the code/number the API layer
 * needs before any call. Pure and deterministic so ranking is unit-tested
 * without Android. [FR-1.1]
 */
object DirectorySearch {

    /** Ranked hits for [rawQuery], best first, capped at [limit]. */
    fun search(index: DirectoryIndex, rawQuery: String, limit: Int = 20): List<SearchResult> {
        val q = normalize(rawQuery)
        if (q.isEmpty()) return emptyList()
        val numeric = q.all { it.isDigit() }

        val hits = ArrayList<SearchResult>()
        // A digit query is a train number; skip the (letter-coded) stations.
        if (!numeric) {
            for (s in index.stations) scoreStation(s, q)?.let { hits += SearchResult(s, it) }
        }
        for (t in index.trains) scoreTrain(t, q, numeric)?.let { hits += SearchResult(t, it) }

        hits.sortWith(
            compareByDescending<SearchResult> { it.score }
                .thenBy { it.entry.label.length }
                .thenBy { it.entry.label },
        )
        return if (hits.size > limit) hits.subList(0, limit) else hits
    }

    private fun scoreStation(s: Station, q: String): Int? {
        val best = maxOf(
            field(s.code.lowercase(), q, weight = 120),
            field(s.name.lowercase(), q, weight = 100),
            field(s.city.lowercase(), q, weight = 60),
        )
        return best.takeIf { it > 0 }
    }

    private fun scoreTrain(t: Train, q: String, numeric: Boolean): Int? {
        val byNumber = field(t.number, q, weight = 120)
        // Don't fuzzy-match names against a pure-digit query.
        val byName = if (numeric) 0 else field(t.name.lowercase(), q, weight = 100)
        val best = maxOf(byNumber, byName)
        return best.takeIf { it > 0 }
    }

    /** Best match tier of [q] within one [field], scaled by [weight]/100. */
    private fun field(field: String, q: String, weight: Int): Int {
        if (field.isEmpty()) return 0
        val tier = when {
            field == q -> 100
            field.startsWith(q) -> 82
            wordStartsWith(field, q) -> 66
            field.contains(q) -> 48
            q.length >= 3 && fuzzy(field, q) -> 26
            else -> 0
        }
        return tier * weight / 100
    }

    private fun wordStartsWith(field: String, q: String): Boolean =
        field.split(' ', '-', '/').any { it.startsWith(q) }

    /** Typo tolerance: any word within edit distance 1 (len ≤ 5) or 2 (longer) of [q]. */
    private fun fuzzy(field: String, q: String): Boolean {
        val budget = if (q.length <= 5) 1 else 2
        return field.split(' ', '-', '/').any { word ->
            kotlin.math.abs(word.length - q.length) <= budget && levenshtein(word, q, budget) <= budget
        }
    }

    /** Bounded Levenshtein: returns [max]+1 as soon as the row's best exceeds [max]. */
    private fun levenshtein(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowBest = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowBest) rowBest = cur[j]
            }
            if (rowBest > max) return max + 1
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    private fun normalize(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")
}
