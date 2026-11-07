import Shared
import SwiftUI

/// One line at a stop: badge, destination, the next few arrivals, timetable and
/// bell buttons, and the big countdown on the right.
struct ArrivalRow<Expanded: View>: View {
    let line: LineArrivalDetail
    /// Seconds since the data was fetched, so countdowns keep moving between refreshes.
    var elapsed: Int64 = 0
    var followUpCount = 3
    var directionLines = 2
    var destinationOnly = false
    var detailed = false
    var isTracking = false
    var timetableExpanded = false
    var timetableLoading = false
    var onLineTap: () -> Void = {}
    var onTimetable: () -> Void = {}
    var onBell: () -> Void = {}
    @ViewBuilder var expanded: () -> Expanded

    private var inService: Bool { line.arrivingTime >= 0 }
    private var liveSeconds: Int64 { max(0, line.arrivingTime - elapsed) }
    private var destinationText: String {
        LineColorsKt.formatDestination(directionName: line.directionName, direction: line.direction)
    }
    private var isAirportDest: Bool {
        LineColorsKt.isAirportStopName(name: destinationText) ||
        LineColorsKt.isAirportStopName(name: line.directionName) ||
        LineColorsKt.isAirportLineName(name: line.name)
    }

    var body: some View {
        let followUps = inService ? Array(line.arrivingTimes.dropFirst().prefix(followUpCount)) : []
        let showFollowUps = !followUps.isEmpty

        VStack(alignment: .leading, spacing: 6) {
            Grid(alignment: .leading, horizontalSpacing: 10, verticalSpacing: 4) {
                GridRow(alignment: .center) {
                    Button(action: onLineTap) {
                        LineBadge(name: line.name, color: line.color, isAirport: LineColorsKt.isAirportLineName(name: line.name))
                    }
                    .buttonStyle(.plain)
                    .frame(width: 48)

                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        HStack(alignment: .firstTextBaseline, spacing: 5) {
                            if isAirportDest {
                                Image(systemName: "airplane")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(.secondary)
                            }
                            Text(destinationText)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.primary)
                                .lineLimit(2)
                                .minimumScaleFactor(0.80)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if inService {
                            ArrivalBadge(seconds: liveSeconds, isTimetable: line.isTimetable,
                                         size: detailed ? .large : .medium, imminent: liveSeconds < 120)
                                .fixedSize()
                        } else {
                            OutOfServicePill().fixedSize()
                        }
                    }
                }
                if showFollowUps || detailed || destinationOnly {
                    GridRow(alignment: .center) {
                        Text(showFollowUps ? tr("dashboard.nextArrivals") : "")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(.secondary)
                            .frame(width: 48)
                        HStack(spacing: 6) {
                            if showFollowUps {
                                ViewThatFits(in: .horizontal) {
                                    followUpRow(followUps)
                                    followUpRow(Array(followUps.prefix(2)))
                                    followUpRow(Array(followUps.prefix(1)))
                                }
                            }
                            Spacer(minLength: 4)
                            timetableButton
                            if inService { bellButton }
                        }
                    }
                }
            }

            if timetableExpanded {
                expanded()
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.snappy, value: timetableExpanded)
    }

    private var timetableButton: some View {
        Button(action: onTimetable) {
            Group {
                if timetableLoading {
                    ProgressView().controlSize(.mini)
                } else {
                    Image(systemName: timetableExpanded ? "calendar.circle.fill" : "calendar")
                        .foregroundStyle(timetableExpanded ? InfoTBDesign.accent : .secondary)
                }
            }
            .frame(width: 30, height: 30)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(tr("stopDetail.timetable"))
    }

    private var bellButton: some View {
        Button(action: onBell) {
            Image(systemName: isTracking ? "bell.slash.fill" : "bell")
                .symbolEffect(.bounce, value: isTracking)
                .foregroundStyle(isTracking ? Color.red : .secondary)
                .frame(width: 30, height: 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isTracking ? tr("stopDetail.stopTracking") : tr("stopDetail.notification"))
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
