package ro.infotb.modern.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DirectionSerializer::class)
enum class Direction(val value: Int) {
    TUR(0),
    RETUR(1);

    companion object {
        fun fromInt(v: Int): Direction = if (v == 1) RETUR else TUR
    }
}

object DirectionSerializer : KSerializer<Direction> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Direction", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Direction) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): Direction {
        return Direction.fromInt(decoder.decodeInt())
    }
}
