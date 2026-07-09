package app.railcast.directory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes the bundled positional-array index (packages/directory FORMAT.md).
 * Columns are bound by their header name — never by hard-coded offset — so a
 * future column addition stays backwards-compatible. The app treats the file
 * as opaque; this is the only place that knows its shape. [FR-1.1, FR-1.2]
 */
class DirectoryIndex(
    val version: Int,
    val stations: List<Station>,
    val trains: List<Train>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): DirectoryIndex {
            val root = json.parseToJsonElement(text).jsonObject
            val version = root["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1

            val stationCols = columnIndex(root, "stationColumns")
            val trainCols = columnIndex(root, "trainColumns")

            val sCode = stationCols.getValue("code")
            val sName = stationCols.getValue("name")
            val sCity = stationCols.getValue("city")
            val sState = stationCols.getValue("state")
            val sLat = stationCols.getValue("lat")
            val sLng = stationCols.getValue("lng")
            val stations = root.rows("stations").map { r ->
                Station(
                    code = r.str(sCode),
                    name = r.str(sName),
                    city = r.str(sCity),
                    state = r.str(sState),
                    lat = r.getOrNull(sLat)?.jsonPrimitive?.doubleOrNull,
                    lng = r.getOrNull(sLng)?.jsonPrimitive?.doubleOrNull,
                )
            }

            val tNumber = trainCols.getValue("number")
            val tName = trainCols.getValue("name")
            val tFrom = trainCols.getValue("fromCode")
            val tTo = trainCols.getValue("toCode")
            val trains = root.rows("trains").map { r ->
                Train(
                    number = r.str(tNumber),
                    name = r.str(tName),
                    fromCode = r.str(tFrom),
                    toCode = r.str(tTo),
                )
            }

            return DirectoryIndex(version, stations, trains)
        }

        private fun columnIndex(root: JsonObject, key: String): Map<String, Int> =
            root[key]?.jsonArray
                ?.mapIndexed { i, e -> e.jsonPrimitive.content to i }
                ?.toMap()
                ?: error("directory index missing header '$key'")

        private fun JsonObject.rows(key: String): List<JsonArray> =
            this[key]?.jsonArray?.map { it.jsonArray } ?: emptyList()

        private fun JsonArray.str(i: Int): String = getOrNull(i)?.jsonPrimitive?.contentOrNull ?: ""
    }
}
