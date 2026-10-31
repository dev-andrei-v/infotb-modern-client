import Shared
import SwiftUI

/// Hour and minute of the wall clock, for highlighting the next departure.
private struct ClockNow {
    let hour: Int
    let minute: Int

    static var current: ClockNow {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return ClockNow(hour: parts.hour ?? 0, minute: parts.minute ?? 0)
    }

    /// The first minute in [hour] at or after now, if this is the current hour.
    func nextMinute(in hour: TimetableHour) -> Int? {
        guard Int(hour.hour) == self.hour else { return nil }
        return hour.minutes.compactMap { Int($0) }.filter { $0 >= minute }.min()
    }
}

private struct MinuteChip: View {
    let text: String
    let isNext: Bool
    let isPast: Bool

    var body: some View {
        Text(text.count < 2 ? "0" + text : text)
            .font(.caption.monospacedDigit().weight(isNext ? .bold : .regular))
            .foregroundStyle(isNext ? Color.accentColor : isPast ? Color.secondary.opacity(0.4) : Color.secondary)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(
                isNext ? Color.accentColor.opacity(0.15) : isPast ? Color.clear : Color(.tertiarySystemFill),
                in: RoundedRectangle(cornerRadius: 5, style: .continuous)
            )
            .overlay {
                if isNext {
                    RoundedRectangle(cornerRadius: 5, style: .continuous).strokeBorder(Color.accentColor, lineWidth: 1.2)
                }
            }
    }
}

/// The whole day's timetable, scrolled to the current hour.
struct TimetableGrid: View {
    let timetable: [TimetableHour]

    var body: some View {
        let now = ClockNow.current
        VStack(spacing: 0) {
            HStack {
                Text(tr("timetable.hour")).frame(width: 48, alignment: .leading)
                Text(tr("timetable.minutes"))
                Spacer()
            }
            .font(.caption.weight(.bold))
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(.tertiarySystemFill))

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(timetable.enumerated()), id: \.offset) { index, hour in
                            if index > 0 { Divider() }
                            row(hour, now: now).id(index)
                        }
                    }
                }
                .frame(height: 280)
                .onAppear {
                    let target = timetable.firstIndex { (Int($0.hour) ?? -1) >= now.hour }
                    if let target { proxy.scrollTo(target, anchor: .top) }
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Color(.separator), lineWidth: 0.5))
    }

    private func row(_ hour: TimetableHour, now: ClockNow) -> some View {
        let h = Int(hour.hour) ?? -1
        let isCurrent = h == now.hour
        let isPastHour = h < now.hour
        let next = now.nextMinute(in: hour)
        return HStack(alignment: .firstTextBaseline) {
            Text(hour.hour)
                .font(.subheadline.monospacedDigit().weight(isCurrent ? .heavy : .bold))
                .foregroundStyle(isPastHour ? Color.secondary.opacity(0.5) : isCurrent ? Color.accentColor : Color.primary)
                .frame(width: 48, alignment: .leading)
            FlowLayout(spacing: 4, lineSpacing: 3) {
                ForEach(Array(hour.minutes.enumerated()), id: \.offset) { _, minute in
                    let value = Int(minute) ?? 0
                    MinuteChip(
                        text: minute,
                        isNext: value == next,
                        isPast: isPastHour || (isCurrent && value < now.minute)
                    )
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(isCurrent ? Color.accentColor.opacity(0.08) : .clear)
    }
}

/// Departures over the next three hours, compact enough for a dashboard card.
struct UpcomingTimetable: View {
    let timetable: [TimetableHour]

    var body: some View {
        let now = ClockNow.current
        let entries: [(hour: String, minutes: [String])] = timetable.compactMap { entry in
            guard let h = Int(entry.hour), (now.hour...now.hour + 2).contains(h) else { return nil }
            let minutes = h == now.hour ? entry.minutes.filter { (Int($0) ?? 0) >= now.minute } : entry.minutes
            return minutes.isEmpty ? nil : (entry.hour, minutes)
        }

        if entries.isEmpty {
            Text(tr("widget.noScheduled"))
                .font(.caption)
                .foregroundStyle(.secondary)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(entries, id: \.hour) { entry in
                    let isCurrent = Int(entry.hour) == now.hour
                    let next = isCurrent ? entry.minutes.compactMap { Int($0) }.min() : nil
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(entry.hour.count < 2 ? "0" + entry.hour : entry.hour)
                            .font(.caption.monospacedDigit().weight(.bold))
                            .frame(width: 22, alignment: .leading)
                        FlowLayout(spacing: 4, lineSpacing: 3) {
                            ForEach(entry.minutes, id: \.self) { minute in
                                MinuteChip(text: minute, isNext: Int(minute) == next, isPast: false)
                            }
                        }
                    }
                }
            }
            .padding(8)
            .background(Color(.tertiarySystemFill).opacity(0.5), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
    }
}
