import Foundation

struct NearbyStopGroup: Identifiable {
    let id: String
    let stops: [CachedStop]

    var nearest: CachedStop { stops[0] }
    var name: String { nearest.name }
    var lines: [CachedStopLine] {
        var seen: Set<Int64> = []
        return stops.flatMap(\.lines).filter { seen.insert($0.id).inserted }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// Input is already ordered by distance. Keep that order for stations and
    /// their platforms, while joining direction-specific stops by station name.
    static func groups(_ stops: [CachedStop]) -> [NearbyStopGroup] {
        var order: [String] = []
        var members: [String: [CachedStop]] = [:]
        for stop in stops {
            let key = stop.name.trimmingCharacters(in: .whitespacesAndNewlines)
                .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Foundation.Locale(identifier: "ro_RO"))
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            if members[key] == nil { order.append(key) }
            members[key, default: []].append(stop)
        }
        return order.map { NearbyStopGroup(id: $0, stops: members[$0]!) }
    }
}
