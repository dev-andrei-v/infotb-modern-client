import Shared
import SwiftUI

@Observable
final class AnnouncementsModel {
    let viewModel = IosServices.shared.announcementsViewModel()
    private let scope = KotlinScope()

    private(set) var announcements: [Announcement] = []
    private(set) var isLoading = false
    private(set) var error: String?

    init() {
        scope.own(viewModel)
        scope.watch(viewModel.announcements) { [weak self] in self?.announcements = Unbox.list($0) }
        scope.watch(viewModel.isLoading) { [weak self] in self?.isLoading = Unbox.bool($0) }
        scope.watch(viewModel.error) { [weak self] in self?.error = $0 as? String }
    }

    func reload() { viewModel.loadAnnouncements() }
}

struct AnnouncementsView: View {
    let model: AnnouncementsModel

    var body: some View {
        content
            .navigationTitle(tr("announcements.title"))
            .refreshable {
                model.reload()
                try? await Task.sleep(for: .milliseconds(600))
            }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading && model.announcements.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.error != nil && model.announcements.isEmpty {
            ContentUnavailableView {
                Label(tr("announcements.errorTitle"), systemImage: "wifi.exclamationmark")
            } description: {
                VStack(spacing: 6) {
                    Text(tr("announcements.errorDescription"))
                    if let error = model.error, !error.isEmpty {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .textSelection(.enabled)
                    }
                }
            } actions: {
                Button(tr("common.retry")) { model.reload() }.buttonStyle(.borderedProminent)
            }
        } else if model.announcements.isEmpty {
            ContentUnavailableView(tr("announcements.emptyTitle"), systemImage: "checkmark.seal")
        } else {
            List {
                Section {
                    ForEach(model.announcements, id: \.id) { AnnouncementRow(announcement: $0) }
                } footer: {
                    Text(tr("announcements.description"))
                }
            }
            .listStyle(.insetGrouped)
        }
    }
}

struct AnnouncementRow: View {
    let announcement: Announcement
    @State private var expanded = false

    private var title: String { HTMLText.plain(announcement.title) }
    private var message: String { HTMLText.plain(announcement.message) }
    private var titleIsRepeatedInMessage: Bool {
        !title.isEmpty && message.range(of: title, options: [.caseInsensitive, .diacriticInsensitive]) != nil
    }
    private var messageWithoutRepeatedTitle: String {
        guard titleIsRepeatedInMessage,
              let range = message.range(of: title, options: [.caseInsensitive, .diacriticInsensitive]) else { return message }
        return String(message[..<range.lowerBound] + message[range.upperBound...])
            .trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))
    }
    private var isLong: Bool {
        messageWithoutRepeatedTitle.count > 200
    }
    private var titlePreview: String {
        guard title.count > 72 else { return title }
        let prefix = String(title.prefix(69))
        if let boundary = prefix.lastIndex(of: " "), boundary > prefix.index(prefix.startIndex, offsetBy: 35) {
            return String(prefix[..<boundary]) + "…"
        }
        return String(title.prefix(69)) + "…"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                    .font(.title3)
                VStack(alignment: .leading, spacing: 2) {
                    Text(titlePreview)
                        .font(.headline)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Text(relativeDate)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if !announcement.lines.isEmpty {
                FlowLayout(spacing: 4, lineSpacing: 4) {
                    ForEach(Array(announcement.lines.enumerated()), id: \.offset) { _, line in
                        LineBadge(name: line.name, color: line.color, size: .small)
                    }
                }
            }

            if !messageWithoutRepeatedTitle.isEmpty {
                Text(messageWithoutRepeatedTitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(expanded || !isLong ? nil : 4)
            }

            if isLong {
                Button(expanded ? tr("announcements.showLess") : tr("announcements.showMore")) {
                    withAnimation(.snappy) { expanded.toggle() }
                }
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 6)
    }

    private var relativeDate: String {
        // STB sends milliseconds; tolerate seconds too.
        let raw = TimeInterval(announcement.created_at)
        let date = Date(timeIntervalSince1970: raw > 100_000_000_000 ? raw / 1000 : raw)
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Foundation.Locale(identifier: AppModel.shared.localeCode)
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: .now)
    }
}

/// STB notices arrive as HTML fragments; this turns them into readable plain text.
enum HTMLText {
    static func plain(_ html: String) -> String {
        var text = html
        let replacements: [(String, String)] = [
            ("<br\\s*/?>", "\n"), ("</p>", "\n"), ("<p[^>]*>", ""), ("<[^>]+>", ""),
        ]
        for (pattern, replacement) in replacements {
            text = text.replacingOccurrences(of: pattern, with: replacement, options: [.regularExpression, .caseInsensitive])
        }
        let entities = ["&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&nbsp;": " ", "&#39;": "'"]
        for (entity, value) in entities {
            text = text.replacingOccurrences(of: entity, with: value)
        }
        text = decodeNumericEntities(text)
        text = text.replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression)
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func decodeNumericEntities(_ text: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: "&#(x?)([0-9a-fA-F]+);") else { return text }
        let result = NSMutableString(string: text)
        // Back to front, so earlier ranges stay valid as later ones change length.
        for match in regex.matches(in: text, range: NSRange(location: 0, length: result.length)).reversed() {
            let isHex = match.range(at: 1).length > 0
            let digits = result.substring(with: match.range(at: 2))
            guard let code = UInt32(digits, radix: isHex ? 16 : 10), let scalar = Unicode.Scalar(code) else { continue }
            result.replaceCharacters(in: match.range, with: String(Character(scalar)))
        }
        return result as String
    }
}
