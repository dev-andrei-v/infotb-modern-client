package ro.infotb.modern.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LeafletMap(
    lat: Double,
    lng: Double,
    label: String,
    zoom: Int,
    modifier: Modifier
) {
    val escapedLabel = label.replace("'", "\\'").replace("\"", "&quot;")
    val html = remember(lat, lng, label, zoom) {
        """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
html,body,#map{margin:0;padding:0;width:100%;height:100%;}
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', {zoomControl: false}).setView([$lat, $lng], $zoom);
L.control.zoom({position: 'bottomright'}).addTo(map);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OSM',
    maxZoom: 19
}).addTo(map);
L.marker([$lat, $lng]).addTo(map).bindPopup('$escapedLabel').openPopup();
</script>
</body>
</html>
""".trimIndent()
    }

    UIKitView(
        modifier = modifier,
        factory = {
            val config = WKWebViewConfiguration()
            val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
            webView.scrollView.setBounces(false)
            val data = (html as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            if (data != null) {
                webView.loadData(data, MIMEType = "text/html", characterEncodingName = "UTF-8", baseURL = NSURL(string = ""))
            }
            webView
        }
    )
}
