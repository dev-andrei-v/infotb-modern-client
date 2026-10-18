package ro.infotb.modern.location

actual class PlatformLocation actual constructor() {
    actual fun isSupported(): Boolean = false
    actual suspend fun current(): GeoPoint? = null
}
