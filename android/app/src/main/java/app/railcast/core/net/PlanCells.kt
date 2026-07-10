package app.railcast.core.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Per-row seats/fare hydrate separately, so on the wire each cell is either the
// literal string "pending" OR the ready object (docs/api-contracts.md §4). These
// custom serializers mirror that union field-for-field.

@Serializable
data class RowAvailability(
    val status: String, // available | rac | waitlist | not_available
    val text: String,
    val predictionPct: Int? = null,
    val canBook: Boolean,
)

@Serializable
data class RowFareBreakdown(
    val base: Double,
    val reservation: Double,
    val superfast: Double,
    val tatkal: Double,
    val gst: Double,
    val dynamic: Double,
    val other: Double,
)

@Serializable
data class RowFare(val total: Double, val breakdown: RowFareBreakdown)

/** availability cell: still hydrating, or the ready value. */
@Serializable(with = AvailabilityCellSerializer::class)
sealed interface AvailabilityCell {
    data object Pending : AvailabilityCell
    data class Ready(val value: RowAvailability) : AvailabilityCell
}

/** fare cell: still hydrating, or the ready value. */
@Serializable(with = FareCellSerializer::class)
sealed interface FareCell {
    data object Pending : FareCell
    data class Ready(val value: RowFare) : FareCell
}

object AvailabilityCellSerializer : KSerializer<AvailabilityCell> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AvailabilityCell")

    override fun deserialize(decoder: Decoder): AvailabilityCell {
        val input = decoder as? JsonDecoder ?: error("AvailabilityCell requires JSON")
        val el = input.decodeJsonElement()
        return if (el is JsonObject) {
            AvailabilityCell.Ready(input.json.decodeFromJsonElement(RowAvailability.serializer(), el))
        } else {
            AvailabilityCell.Pending // "pending"
        }
    }

    override fun serialize(encoder: Encoder, value: AvailabilityCell) {
        val out = encoder as? JsonEncoder ?: error("AvailabilityCell requires JSON")
        when (value) {
            AvailabilityCell.Pending -> out.encodeJsonElement(JsonPrimitive("pending"))
            is AvailabilityCell.Ready ->
                out.encodeJsonElement(out.json.encodeToJsonElement(RowAvailability.serializer(), value.value))
        }
    }
}

object FareCellSerializer : KSerializer<FareCell> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FareCell")

    override fun deserialize(decoder: Decoder): FareCell {
        val input = decoder as? JsonDecoder ?: error("FareCell requires JSON")
        val el = input.decodeJsonElement()
        return if (el is JsonObject) {
            FareCell.Ready(input.json.decodeFromJsonElement(RowFare.serializer(), el))
        } else {
            FareCell.Pending
        }
    }

    override fun serialize(encoder: Encoder, value: FareCell) {
        val out = encoder as? JsonEncoder ?: error("FareCell requires JSON")
        when (value) {
            FareCell.Pending -> out.encodeJsonElement(JsonPrimitive("pending"))
            is FareCell.Ready -> out.encodeJsonElement(out.json.encodeToJsonElement(RowFare.serializer(), value.value))
        }
    }
}
