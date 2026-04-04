package ro.infotb.modern.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = TransportTypeSerializer::class)
enum class TransportType {
    BUS, TRAM, CABLE_CAR, SUBWAY;

    companion object {
        fun fromString(value: String): TransportType = when (value.uppercase()) {
            "BUS" -> BUS
            "TRAM" -> TRAM
            "CABLE_CAR", "TROLLEYBUS" -> CABLE_CAR
            "SUBWAY", "METRO" -> SUBWAY
            else -> BUS
        }
    }
}

object TransportTypeSerializer : KSerializer<TransportType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransportType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TransportType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): TransportType {
        return TransportType.fromString(decoder.decodeString())
    }
}
