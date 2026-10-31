import Shared
import SwiftUI

/// Data older than this is flagged on the card.
private let staleAfterMs: Int64 = 60_000

/// One monitored stop: its lines with live countdowns, notices, and quick actions.
struct DashboardCard: View {
    let model: DashboardModel
    let widget: WidgetConfig
    let now: Date
    let showGroup: Bool
    let onOptions: () -> Void

    private let app = AppModel.shared

    var body: some View {
        let state = model.cardStates[widget.stopId]
        let allLines = state?.info?.lines ?? []
        let lines = model.lines(for: widget)
        let detailed = widget.size == .large
        let maxRows = detailed ? Int.max : 4
        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        // Seconds since this card's data arrived; drives the local countdown.
        let elapsed = (state?.lastUpdatedMs ?? 0) > 0 ? max(0, (nowMs - state!.lastUpdatedMs) / 1000) : 0
        let isStale = state?.hasData == true && (state?.error != nil || nowMs - (state?.lastUpdatedMs ?? 0) > staleAfterMs)

        HStack(spacing: 0) {
            cardAccent(for: allLines, sorted: lines)
                .frame(width: 5)

            VStack(alignment: .leading, spacing: 8) {
                header(state: state, isStale: isStale)

                let news = model.news(for: lines)
                if !news.isEmpty {
                    NewsBanner(news: news) { app.selectedTab = .news }
                }

                if state?.info != nil, !lines.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(lines.prefix(maxRows).enumerated()), id: \.offset) { index, line in
                            if index > 0 { Divider() }
                            row(line, elapsed: elapsed, detailed: detailed)
                                .padding(.vertical, 6)
                        }
                    }
                    if lines.count > maxRows {
                        Button {
                            app.push(.stop(widget.stopId))
                        } label: {
                            Text("+\(lines.count - maxRows) \(tr("widget.moreLines"))")
                                .font(.footnote.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                        }
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.capsule)
                    }
                } else if state?.info == nil && widget.hidden {
                    Text(tr("widget.hidden")).font(.footnote).foregroundStyle(.secondary)
                } else if state?.info == nil && state?.error == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 48)
                } else if state?.info == nil {
                    Label(tr("widget.loadFailed"), systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .symbolRenderingMode(.multicolor)
                } else {
                    Text(tr("widget.noData")).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .padding(.leading, 12)
            .padding(.trailing, 10)
            .padding(.vertical, 12)
        }
        .card()
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .contextMenu { menuItems }
    }

    private func header(state: StopCardState?, isStale: Bool) -> some View {
        HStack(alignment: .top) {
            Button {
                app.push(.stop(widget.stopId))
            } label: {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 4) {
                        Text(widget.stopName)
                            .font(.headline)
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.tertiary)
                    }
                    HStack(spacing: 6) {
                        if model.nearestMode, let meters = model.distances[widget.stopId] {
                            Label(DashboardFormat.distance(meters), systemImage: "location.fill")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color.accentColor)
                        }
                        if showGroup && !widget.group.isEmpty {
                            Text(widget.group).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        if isStale {
                            Label(tr("dashboard.stale"), systemImage: "exclamationmark.triangle.fill")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.orange)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .tintedGlass(.orange, in: Capsule())
                        } else if state?.isLoading == true && state?.hasData == true {
                            ProgressView().controlSize(.mini)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Menu {
                menuItems
            } label: {
                Image(systemName: "ellipsis")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .frame(width: 32, height: 32)
                    .glassBackground(in: Circle(), interactive: true)
            }
            .accessibilityLabel(tr("widget.options"))
        }
    }

    @ViewBuilder
    private var menuItems: some View {
        Button(action: onOptions) {
            Label(tr("widget.options"), systemImage: "slider.horizontal.3")
        }
        Button {
            model.resize(widget, to: widget.size == .large ? .medium : .large)
        } label: {
            Label(
                widget.size == .large ? tr("widget.sizeMedium") : tr("widget.sizeLarge"),
                systemImage: widget.size == .large ? "rectangle.compress.vertical" : "rectangle.expand.vertical"
            )
        }
        Button {
            model.toggleHidden(widget)
        } label: {
            Label(tr("widget.hide"), systemImage: "eye.slash")
        }
        Divider()
        Button(role: .destructive) {
            model.remove(widget)
        } label: {
            Label(tr("widget.delete"), systemImage: "trash")
        }
    }

    private func row(_ line: LineArrivalDetail, elapsed: Int64, detailed: Bool) -> some View {
        let key = DashboardModel.timetableKey(stopId: widget.stopId, line: line)
        let subscription = app.trackingSubscription(stopId: widget.stopId, line: line)
        return ArrivalRow(
            line: line,
            elapsed: elapsed,
            followUpCount: detailed ? 3 : 2,
            directionLines: detailed ? 2 : 1,
            isTracking: subscription != nil,
            timetableExpanded: model.expandedTimetables.contains(key),
            timetableLoading: model.timetableLoading.contains(key),
            onLineTap: { app.push(.line(line.id)) },
            onTimetable: { model.toggleTimetable(stopId: widget.stopId, line: line) },
            onBell: {
                if let subscription {
                    app.stopTracking(subscription)
                } else {
                    app.track(
                        stopId: widget.stopId,
                        stopName: widget.stopName,
                        line: line,
                        minutes: Int((line.arrivingTime - elapsed) / 60)
                    )
                }
            }
        ) {
            if let timetable = model.timetables[key], !timetable.isEmpty {
                UpcomingTimetable(timetable: timetable).padding(.leading, 48)
            } else if model.timetableLoading.contains(key) {
                ProgressView().frame(maxWidth: .infinity)
            } else {
                Text(tr("widget.noScheduled")).font(.caption).foregroundStyle(.secondary).padding(.leading, 48)
            }
        }
    }
}

/// One-line strip shown when a notice mentions one of the card's lines.
private struct NewsBanner: View {
    let news: [Announcement]
    let onTap: () -> Void

    var body: some View {
        let first = news[0]
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                VStack(alignment: .leading, spacing: 1) {
                    Text(first.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    if news.count > 1 {
                        Text(tr("widget.moreNews").replacingOccurrences(of: "{n}", with: "\(news.count - 1)"))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 4)
                HStack(spacing: 3) {
                    ForEach(Array(first.lines.prefix(3).enumerated()), id: \.offset) { _, line in
                        LineBadge(name: line.name, color: line.color, size: .small)
                    }
                }
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .tintedGlass(.orange, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
