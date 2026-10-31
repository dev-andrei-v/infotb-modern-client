import MapKit
import Shared
import SwiftUI

/// A small map pinned on one stop.
struct StopMap: View {
    let latitude: Double
    let longitude: Double
    let label: String

    var body: some View {
        let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        Map(initialPosition: .region(MKCoordinateRegion(center: coordinate, latitudinalMeters: 500, longitudinalMeters: 500))) {
            Marker(label, systemImage: "bus.fill", coordinate: coordinate)
                .tint(.red)
        }
        .mapControls { }
    }
}

/// A line's route with its stops and live vehicle positions.
struct RouteMap: View {
    let stops: [Stop]
    let vehicles: [VehiclePosition]
    let lineColor: String
    let lineType: TransportType
    let segmentPath: String?
    /// The stop the user tapped; its dot grows and shows its name.
    var selectedStopId: Binding<Int64?> = .constant(nil)

    @State private var position: MapCameraPosition = .automatic

    private var color: Color { Color(hex: lineColor) ?? .lineFallback }

    private var route: [CLLocationCoordinate2D] {
        if let segmentPath, !segmentPath.isEmpty {
            return PolylineKt.decodePolyline(encoded: segmentPath).compactMap { pair in
                guard let lat = pair.first?.doubleValue, let lng = pair.second?.doubleValue else { return nil }
                return CLLocationCoordinate2D(latitude: lat, longitude: lng)
            }
        }
        return stops.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
    }

    var body: some View {
        Map(position: $position, interactionModes: [.pan, .zoom]) {
            if route.count >= 2 {
                MapPolyline(coordinates: route)
                    .stroke(color, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            }
            // Arrows along the route show the direction of travel, as the Leaflet map did
            ForEach(arrows) { arrow in
                Annotation("", coordinate: arrow.coordinate, anchor: .center) {
                    DirectionArrow(color: color)
                        .rotationEffect(.degrees(arrow.bearing))
                        .allowsHitTesting(false)
                }
            }
            ForEach(Array(stops.enumerated()), id: \.element.id) { index, stop in
                let terminal = index == 0 || index == stops.count - 1
                let selected = selectedStopId.wrappedValue == stop.id
                let size: CGFloat = selected ? 20 : terminal ? 14 : 10
                Annotation(terminal || selected ? stop.name : "", coordinate: CLLocationCoordinate2D(latitude: stop.lat, longitude: stop.lng)) {
                    Circle()
                        .fill(color)
                        .frame(width: size, height: size)
                        .overlay(Circle().stroke(.white, lineWidth: selected ? 3 : 2))
                        .shadow(color: .black.opacity(selected ? 0.3 : 0), radius: 4)
                        // A larger invisible target so small dots are easy to hit
                        .frame(width: 36, height: 36)
                        .contentShape(Circle())
                        .onTapGesture {
                            withAnimation(.snappy) {
                                selectedStopId.wrappedValue = selected ? nil : stop.id
                            }
                        }
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel(stop.name)
                }
            }
            ForEach(vehicles, id: \.id) { vehicle in
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: vehicle.lat, longitude: vehicle.lng)) {
                    VehicleGlyph(type: (vehicle.transportType ?? lineType).name, size: 14)
                        .foregroundStyle(.white)
                        .frame(width: 28, height: 28)
                        .background(Color.realtime, in: Circle())
                        .overlay(Circle().stroke(.white, lineWidth: 2))
                        .shadow(radius: 2)
                }
            }
        }
        // No rotation or tilt, so the direction arrows always point the right way on screen
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .mapControls {
            MapScaleView()
        }
    }

    private struct Arrow: Identifiable {
        let id: Int
        let coordinate: CLLocationCoordinate2D
        /// Degrees clockwise from north.
        let bearing: Double
    }

    /// About fifteen arrows spread along the route, each midway along a segment.
    private var arrows: [Arrow] {
        let points = route
        guard points.count > 1 else { return [] }
        let step = max(1, points.count / 15)
        return stride(from: 0, to: points.count - 1, by: step).map { i in
            let a = points[i], b = points[i + 1]
            return Arrow(
                id: i,
                coordinate: CLLocationCoordinate2D(latitude: (a.latitude + b.latitude) / 2, longitude: (a.longitude + b.longitude) / 2),
                bearing: Self.bearing(from: a, to: b)
            )
        }
    }

    private static func bearing(from a: CLLocationCoordinate2D, to b: CLLocationCoordinate2D) -> Double {
        let lat1 = a.latitude * .pi / 180, lat2 = b.latitude * .pi / 180
        let dLng = (b.longitude - a.longitude) * .pi / 180
        let y = sin(dLng) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }
}

/// White chevron outlined in the line colour, pointing up (north) before rotation.
private struct DirectionArrow: View {
    let color: Color

    var body: some View {
        ArrowShape()
            .fill(.white)
            .overlay(ArrowShape().stroke(color, style: StrokeStyle(lineWidth: 2, lineJoin: .round)))
            .frame(width: 20, height: 20)
            .shadow(color: .black.opacity(0.35), radius: 1.5, y: 1)
    }

    private struct ArrowShape: Shape {
        func path(in rect: CGRect) -> Path {
            // Same outline as the Leaflet arrow: 12,2 20,16 12,11 4,16 on a 24 grid
            let s = rect.width / 24
            var p = Path()
            p.move(to: CGPoint(x: 12 * s, y: 2 * s))
            p.addLine(to: CGPoint(x: 20 * s, y: 16 * s))
            p.addLine(to: CGPoint(x: 12 * s, y: 11 * s))
            p.addLine(to: CGPoint(x: 4 * s, y: 16 * s))
            p.closeSubpath()
            return p
        }
    }
}
