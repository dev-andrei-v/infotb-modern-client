package ro.infotb.modern.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LeafletRouteMap(
    stops: List<Stop>,
    vehicles: List<VehiclePosition>,
    lineColor: String,
    segmentPath: String?,
    modifier: Modifier
) {
    val color = if (lineColor.startsWith("#")) lineColor else "#$lineColor"
    val html = remember(stops, lineColor, segmentPath) {
        buildRouteMapHtml(stops, color, segmentPath)
    }

    val webViewRef = remember { arrayOfNulls<WKWebView>(1) }
    val pageReady = remember { mutableStateOf(false) }

    LaunchedEffect(vehicles, pageReady.value) {
        if (!pageReady.value) return@LaunchedEffect
        val webView = webViewRef[0] ?: return@LaunchedEffect
        val vehiclesJs = vehicles.joinToString(",") { v ->
            "{lat:${v.lat},lng:${v.lng},id:${v.id}}"
        }
        webView.evaluateJavaScript("updateVehicles([$vehiclesJs])") { _, _ -> }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            val config = WKWebViewConfiguration()
            val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
            webView.scrollView.setBounces(false)
            webView.navigationDelegate = object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                    pageReady.value = true
                }
            }
            val data = (html as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            if (data != null) {
                webView.loadData(data, MIMEType = "text/html", characterEncodingName = "UTF-8", baseURL = NSURL(string = ""))
            }
            webViewRef[0] = webView
            webView
        },
        update = { webView ->
            pageReady.value = false
            val data = (html as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            if (data != null) {
                webView.loadData(data, MIMEType = "text/html", characterEncodingName = "UTF-8", baseURL = NSURL(string = ""))
            }
        }
    )
}

private fun buildRouteMapHtml(
    stops: List<Stop>,
    color: String,
    segmentPath: String?
): String {
    val stopsJs = stops.joinToString(",") { stop ->
        val name = stop.name
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&#39;")
            .replace("\"", "&quot;")
        "{lat:${stop.lat},lng:${stop.lng},name:'$name'}"
    }

    val encodedPath = segmentPath?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""

    return """
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
var map = L.map('map', {zoomControl: false});
L.control.zoom({position: 'bottomright'}).addTo(map);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OSM',
    maxZoom: 19
}).addTo(map);

function decodePoly(str) {
    var coords = [], index = 0, lat = 0, lng = 0;
    while (index < str.length) {
        var b, shift = 0, result = 0;
        do { b = str.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
        lat += (result & 1) ? ~(result >> 1) : (result >> 1);
        shift = 0; result = 0;
        do { b = str.charCodeAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
        lng += (result & 1) ? ~(result >> 1) : (result >> 1);
        coords.push([lat / 1e5, lng / 1e5]);
    }
    return coords;
}

function bearingBetween(a, b) {
    var toRad = function(d) { return d * Math.PI / 180; };
    var toDeg = function(r) { return r * 180 / Math.PI; };
    var dLng = toRad(b[1] - a[1]);
    var y = Math.sin(dLng) * Math.cos(toRad(b[0]));
    var x = Math.cos(toRad(a[0])) * Math.sin(toRad(b[0])) -
        Math.sin(toRad(a[0])) * Math.cos(toRad(b[0])) * Math.cos(dLng);
    return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

var stops = [$stopsJs];
var encodedPath = '$encodedPath';
var lineColor = '$color';

var routeCoords;
if (encodedPath.length > 0) {
    routeCoords = decodePoly(encodedPath);
} else {
    routeCoords = stops.map(function(s) { return [s.lat, s.lng]; });
}

if (routeCoords.length > 0) {
    L.polyline(routeCoords, {color: lineColor, weight: 4, opacity: 0.8}).addTo(map);
}

if (routeCoords.length > 1) {
    var step = Math.max(1, Math.floor(routeCoords.length / 15));
    for (var i = 0; i < routeCoords.length - 1; i += step) {
        var next = Math.min(i + 1, routeCoords.length - 1);
        var mid = [(routeCoords[i][0] + routeCoords[next][0]) / 2,
                   (routeCoords[i][1] + routeCoords[next][1]) / 2];
        var bearing = bearingBetween(routeCoords[i], routeCoords[next]);
        var arrowHtml = '<div style="width:24px;height:24px;transform:rotate(' + bearing + 'deg);pointer-events:none;filter:drop-shadow(0 1px 2px rgba(0,0,0,.4))">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">' +
            '<polygon points="12,2 20,16 12,11 4,16" fill="white" stroke="' + lineColor + '" stroke-width="2" stroke-linejoin="round"/>' +
            '</svg></div>';
        var arrowIcon = L.divIcon({className: '', html: arrowHtml, iconSize: [24, 24], iconAnchor: [12, 12]});
        L.marker(mid, {icon: arrowIcon, interactive: false}).addTo(map);
    }
}

var stopIcon = L.divIcon({
    className: '',
    html: '<div style="width:10px;height:10px;background:#fff;border:2px solid #1e293b;border-radius:50%;box-shadow:0 1px 3px rgba(0,0,0,.3)"></div>',
    iconSize: [10, 10], iconAnchor: [5, 5]
});
var terminalIcon = L.divIcon({
    className: '',
    html: '<div style="width:14px;height:14px;background:' + lineColor + ';border:2px solid #fff;border-radius:50%;box-shadow:0 1px 3px rgba(0,0,0,.3)"></div>',
    iconSize: [14, 14], iconAnchor: [7, 7]
});

stops.forEach(function(stop, i) {
    var isTerminal = (i === 0 || i === stops.length - 1);
    L.marker([stop.lat, stop.lng], {icon: isTerminal ? terminalIcon : stopIcon})
        .addTo(map)
        .bindPopup('<b>' + (i+1) + '.</b> ' + stop.name);
});

var vehicleMarkers = [];

function vehicleIconHtml(color) {
    return '<div style="width:36px;height:36px;position:relative">' +
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36" width="36" height="36">' +
        '<circle cx="18" cy="18" r="17" fill="' + color + '" stroke="white" stroke-width="2"/>' +
        '</svg>' +
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" style="position:absolute;top:9px;left:9px">' +
        '<path d="M8 6v6m8-6v6M2 12h20M2 8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>' +
        '<circle cx="7" cy="20" r="1" fill="white"/><circle cx="17" cy="20" r="1" fill="white"/>' +
        '</svg></div>';
}

function updateVehicles(newVehicles) {
    vehicleMarkers.forEach(function(m) { map.removeLayer(m); });
    vehicleMarkers = [];
    var vIcon = L.divIcon({className: '', html: vehicleIconHtml(lineColor), iconSize: [36, 36], iconAnchor: [18, 18]});
    newVehicles.forEach(function(v) {
        var m = L.marker([v.lat, v.lng], {icon: vIcon, zIndexOffset: 500})
            .addTo(map)
            .bindPopup('#' + v.id);
        vehicleMarkers.push(m);
    });
}

var allPoints = stops.map(function(s) { return [s.lat, s.lng]; });
if (routeCoords.length > 0) allPoints = allPoints.concat(routeCoords);
if (allPoints.length > 0) {
    map.fitBounds(L.latLngBounds(allPoints).pad(0.05));
}
</script>
</body>
</html>
""".trimIndent()
}
