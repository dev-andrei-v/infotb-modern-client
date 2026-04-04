package ro.infotb.modern.util

/**
 * Decodes a Google Encoded Polyline string into a list of [lat, lng] pairs.
 * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lng = 0

    while (index < encoded.length) {
        var shift = 0
        var result = 0

        do {
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)

        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        shift = 0
        result = 0

        do {
            val b = encoded[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)

        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        points.add(Pair(lat / 1e5, lng / 1e5))
    }

    return points
}
