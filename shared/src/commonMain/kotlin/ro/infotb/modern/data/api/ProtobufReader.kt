package ro.infotb.modern.data.api

class ProtobufReader(private val data: ByteArray) {
    var pos = 0
    val isAtEnd: Boolean get() = pos >= data.size

    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= data.size) break
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result
    }

    fun readFixed64(): Double {
        var bits = 0L
        for (i in 0 until 8) {
            bits = bits or ((data[pos++].toLong() and 0xFF) shl (i * 8))
        }
        return Double.fromBits(bits)
    }

    fun readFixed32(): Float {
        var bits = 0
        for (i in 0 until 4) {
            bits = bits or ((data[pos++].toInt() and 0xFF) shl (i * 8))
        }
        return Float.fromBits(bits)
    }

    fun readString(): String {
        val length = readVarint().toInt()
        val str = data.decodeToString(pos, pos + length)
        pos += length
        return str
    }

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        val bytes = data.copyOfRange(pos, pos + length)
        pos += length
        return bytes
    }

    fun readBool(): Boolean = readVarint() != 0L

    fun readInt(): Int = readVarint().toInt()

    fun readLong(): Long = readVarint()

    fun readDouble(): Double = readFixed64()

    fun readMessage(): ProtobufReader {
        val length = readVarint().toInt()
        val subData = data.copyOfRange(pos, pos + length)
        pos += length
        return ProtobufReader(subData)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> pos += 8
            2 -> {
                val length = readVarint().toInt()
                pos += length
            }
            5 -> pos += 4
            else -> throw IllegalArgumentException("Unknown wire type: $wireType")
        }
    }
}
