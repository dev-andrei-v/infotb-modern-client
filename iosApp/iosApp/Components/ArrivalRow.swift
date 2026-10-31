import Shared
import SwiftUI

/// One line at a stop: badge, direction, the next few arrivals, timetable and
/// bell buttons, and the big countdown on the right.
struct ArrivalRow<Expanded: View>: View {
    let line: LineArrivalDetail
    /// Seconds since the data was fetched, so countdowns keep moving between refreshes.
    var elapsed: Int64 = 0
    var followUpCount = 3
    var directionLines = 2
    var isTracking = false
    var timetableExpanded = false
    var timetableLoading = false
    var onLineTap: () -> Void = {}
    var onTimetable: () -> Void = {}
    var onBell: () -> Void = {}
    @ViewBuilder var expanded: () -> Expanded

    private var inService: Bool { line.arrivingTime >= 0 }
    private var liveSeconds: Int64 { max(0, line.arrivingTime - elapsed) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button(action: onLineTap) {
                    LineBadge(name: line.name, color: line.color)
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 3) {
                    Text(line.directionName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(directionLines)
                    if inService {
                        let followUps = Array(line.arrivingTimes.dropFirst().prefix(followUpCount))
                        if !followUps.isEmpty {
                            // Show as many whole badges as fit rather than truncating them
                            ViewThatFits(in: .horizontal) {
                                followUpRow(followUps)
                                followUpRow(Array(followUps.prefix(2)))
                                followUpRow(Array(followUps.prefix(1)))
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button(action: onTimetable) {
                    Group {
                        if timetableLoading {
                            ProgressView().controlSize(.mini)
                        } else {
                            Image(systemName: timetableExpanded ? "calendar.circle.fill" : "calendar")
                                .foregroundStyle(timetableExpanded ? Color.accentColor : .secondary)
                        }
                    }
                    .frame(width: 30, height: 30)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tr("stopDetail.timetable"))

                if inService {
                    Button(action: onBell) {
                        Image(systemName: isTracking ? "bell.slash.fill" : "bell")
                            .symbolEffect(.bounce, value: isTracking)
                            .foregroundStyle(isTracking ? Color.red : .secondary)
                            .frame(width: 30, height: 30)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(isTracking ? tr("stopDetail.stopTracking") : tr("stopDetail.notification"))

                    ArrivalBadge(
                        seconds: liveSeconds,
                        isTimetable: line.isTimetable,
                        size: .large,
                        imminent: liveSeconds < 120
                    )
                    .fixedSize()
                } else {
                    OutOfServicePill().fixedSize()
                }
            }

            if timetableExpanded {
                expanded()
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.snappy, value: timetableExpanded)
    }

    private func followUpRow(_ arrivals: [ArrivalTime]) -> some View {
        HStack(spacing: 4) {
            ForEach(Array(arrivals.enumerated()), id: \.offset) { _, arrival in
                ArrivalBadge(
                    seconds: max(0, arrival.arrivingTime - elapsed),
                    isTimetable: arrival.isTimetable,
                    size: .small
                )
                .fixedSize()
            }
        }
    }
}
