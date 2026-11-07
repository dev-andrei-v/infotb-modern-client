import CoreLocation
import Foundation

struct RouteLanePath {
    let id: String
    let lineId: Int64
    let coordinates: [CLLocationCoordinate2D]
}

/// Separates routes only where they share a corridor and heading. Working in
/// local metres keeps the geometry independent of map zoom; lane widths are
/// applied in screen points when the camera changes. Stop markers stay put.
struct RouteLaneLayout {
    private struct Point {
        let x: Double
        let y: Double
    }
    private struct Sample {
        let point: Point
        let dx: Double
        let dy: Double
        let lineId: Int64
        let pathId: String
    }
    private struct Cell: Hashable {
        let x: Int
        let y: Int
        init(_ point: Point) { x = Int(floor(point.x / 40)); y = Int(floor(point.y / 40)) }
        init(x: Int, y: Int) { self.x = x; self.y = y }
    }
    private struct LanePoint {
        let point: Point
        let offset: Point
    }

    private let origin: CLLocationCoordinate2D
    private let longitudeScale: Double
    private let paths: [String: [LanePoint]]

    init(paths input: [RouteLanePath]) {
        let origin = input.first?.coordinates.first ?? .init(latitude: 44.435, longitude: 26.1)
        self.origin = origin
        let longitudeScale = 111_000 * max(0.1, cos(origin.latitude * .pi / 180))
        self.longitudeScale = longitudeScale
        var samples: [String: [Sample]] = [:]
        var grid: [Cell: [Sample]] = [:]

        for path in input {
            let points = path.coordinates.filter { CLLocationCoordinate2DIsValid($0) }.map {
                Point(x: ($0.longitude - origin.longitude) * longitudeScale, y: ($0.latitude - origin.latitude) * 111_000)
            }
            var values: [Sample] = []
            for (a, b) in zip(points, points.dropFirst()) {
                let dx = b.x - a.x, dy = b.y - a.y
                let length = hypot(dx, dy)
                guard length > 0 else { continue }
                // Bound interpolation even if a malformed path crosses continents.
                let steps = max(1, min(2_000, Int(ceil(length / 25))))
                for index in 0..<steps {
                    let t = Double(index) / Double(steps)
                    values.append(Sample(point: Point(x: a.x + dx * t, y: a.y + dy * t),
                                         dx: dx / length, dy: dy / length, lineId: path.lineId, pathId: path.id))
                }
            }
            if let last = points.last, let previous = values.last {
                values.append(Sample(point: last, dx: previous.dx, dy: previous.dy, lineId: path.lineId, pathId: path.id))
            }
            samples[path.id] = values
            for value in values { grid[Cell(value.point), default: []].append(value) }
        }

        var result: [String: [LanePoint]] = [:]
        for (id, values) in samples {
            result[id] = values.map { value in
                let cell = Cell(value.point)
                var neighbors: [Sample] = []
                for x in (cell.x - 1)...(cell.x + 1) {
                    for y in (cell.y - 1)...(cell.y + 1) {
                        neighbors += (grid[Cell(x: x, y: y)] ?? []).filter {
                            hypot($0.point.x - value.point.x, $0.point.y - value.point.y) < 30 &&
                                abs($0.dx * value.dx + $0.dy * value.dy) > 0.94
                        }
                    }
                }
                let lines = Set(neighbors.map(\.lineId)).sorted()
                guard lines.count > 1, let rank = lines.firstIndex(of: value.lineId),
                      let reference = neighbors.min(by: {
                          if $0.lineId != $1.lineId { return $0.lineId < $1.lineId }
                          return $0.pathId < $1.pathId
                      }) else { return LanePoint(point: value.point, offset: Point(x: 0, y: 0)) }
                let lane = Double(rank) - Double(lines.count - 1) / 2
                return LanePoint(point: value.point, offset: Point(x: -reference.dy * lane, y: reference.dx * lane))
            }
        }
        paths = result
    }

    func coordinates(metersPerPoint: Double) -> [String: [CLLocationCoordinate2D]] {
        let spacing = 6 * max(0.1, metersPerPoint)
        return paths.mapValues { values in
            values.indices.map { index in
                // Blend lane transitions where lines join or leave a corridor.
                let range = max(0, index - 2)...min(values.count - 1, index + 2)
                let dx = range.reduce(0.0) { $0 + values[$1].offset.x } / Double(range.count)
                let dy = range.reduce(0.0) { $0 + values[$1].offset.y } / Double(range.count)
                return CLLocationCoordinate2D(
                    latitude: origin.latitude + (values[index].point.y + dy * spacing) / 111_000,
                    longitude: origin.longitude + (values[index].point.x + dx * spacing) / longitudeScale
                )
            }
        }
    }
}
