import Shared
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @Bindable private var app = AppModel.shared

    var body: some View {
        Form {
            Section(tr("settings.theme")) {
                Picker(tr("settings.theme"), selection: Binding(get: { app.theme }, set: { app.setTheme($0) })) {
                    Label(tr("theme.light"), systemImage: "sun.max.fill").tag("light")
                    Label(tr("theme.dark"), systemImage: "moon.fill").tag("dark")
                    Label(tr("theme.system"), systemImage: "circle.lefthalf.filled").tag("system")
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }

            Section {
                Picker(tr("settings.language"), selection: Binding(get: { app.localeCode }, set: { app.setLocale($0) })) {
                    Text("Romana").tag("ro")
                    Text("English").tag("en")
                }
            } footer: {
                Text(tr("settings.languageDescription"))
            }

            trackingSection

            Section {
                Toggle(tr("settings.noticeAlerts"), isOn: Binding(get: { app.noticeAlerts }, set: { app.setNoticeAlerts($0) }))
            } footer: {
                Text(tr("settings.noticeAlertsDescription"))
            }

            BackupSection()

            Section(tr("settings.about")) {
                HStack(spacing: 12) {
                    Image(systemName: "bus.doubledecker.fill")
                        .font(.title2)
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Color.accentColor.gradient, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    VStack(alignment: .leading) {
                        Text("InfoTB").font(.headline)
                        Text(tr("settings.aboutDescription")).font(.caption).foregroundStyle(.secondary)
                    }
                }
                LabeledContent(tr("settings.version"), value: Self.version)
                Text(tr("settings.aboutInfo"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle(tr("settings.title"))
    }

    private var trackingSection: some View {
        Section {
            if app.subscriptions.isEmpty {
                Label(tr("dynamicIsland.emptyDescription"), systemImage: "bell.badge")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            ForEach(app.subscriptions, id: \.id) { subscription in
                Toggle(isOn: Binding(get: { subscription.enabled }, set: { _ in app.toggleSubscription(subscription) })) {
                    HStack(spacing: 10) {
                        LineBadge(name: subscription.lineName, color: subscription.lineColor, size: .small)
                        VStack(alignment: .leading) {
                            Text(subscription.stopName).font(.subheadline.weight(.medium))
                            Text(subscription.direction == .tur ? tr("direction.tur") : tr("direction.retur"))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .swipeActions {
                    Button(role: .destructive) {
                        app.removeSubscription(subscription)
                    } label: {
                        Label(tr("widget.delete"), systemImage: "trash")
                    }
                }
            }
        } header: {
            Text(tr("dynamicIsland.title"))
        } footer: {
            if !app.subscriptions.isEmpty {
                Text(tr("dynamicIsland.enabled"))
            }
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }
}

/// Export to a JSON file and restore from one, replacing or merging.
private struct BackupSection: View {
    private let backupManager = IosServices.shared.backupManager

    @State private var isBusy = false
    @State private var status: String?
    @State private var statusIsError = false
    @State private var exportFile: JSONFile?
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var pending: (text: String, summary: BackupSummary)?

    var body: some View {
        Section {
            Button {
                export()
            } label: {
                Label(tr("backup.export"), systemImage: "square.and.arrow.up")
            }
            .disabled(isBusy)

            Button {
                showImporter = true
            } label: {
                Label(tr("backup.import"), systemImage: "square.and.arrow.down")
            }
            .disabled(isBusy)
        } header: {
            Text(tr("settings.backup"))
        } footer: {
            if let status {
                Text(status).foregroundStyle(statusIsError ? .red : .secondary)
            } else {
                Text(tr("settings.backupDescription"))
            }
        }
        .fileExporter(
            isPresented: $showExporter,
            document: exportFile,
            contentType: .json,
            defaultFilename: backupManager.suggestedBaseName()
        ) { result in
            isBusy = false
            switch result {
            case .success: show(tr("backup.exported"))
            case .failure: show(tr("backup.cancelled"))
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url): inspect(url)
            case .failure: show(tr("backup.cancelled"))
            }
        }
        .confirmationDialog(
            tr("backup.restoreTitle"),
            isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }),
            titleVisibility: .visible,
            presenting: pending
        ) { restore in
            Button(tr("backup.modeReplace"), role: .destructive) { apply(restore.text, mode: .replace) }
            Button(tr("backup.modeMerge")) { apply(restore.text, mode: .merge) }
            Button(tr("backup.cancel"), role: .cancel) {}
        } message: { restore in
            let s = restore.summary
            Text("""
            \(tr("backup.exportedOn")) \(BackupManagerKt.formatBackupDate(epochMs: s.exportedAt)) · \(s.platform)
            \(s.widgets) \(tr("backup.summaryWidgets")) · \(s.groups) \(tr("backup.summaryGroups")) · \(s.favorites) \(tr("backup.summaryFavorites")) · \(s.subscriptions) \(tr("backup.summarySubscriptions"))

            \(tr("backup.modeReplace")): \(tr("backup.modeReplaceDescription"))
            \(tr("backup.modeMerge")): \(tr("backup.modeMergeDescription"))
            """)
        }
    }

    private func show(_ message: String, isError: Bool = false) {
        status = message
        statusIsError = isError
    }

    private func describe(_ error: Error) -> String {
        let kotlin = (error as NSError).userInfo["KotlinException"]
        if kotlin is BackupError.UnsupportedVersion { return tr("backup.unsupportedVersion") }
        if kotlin is BackupError.Malformed { return tr("backup.invalid") }
        return "\(tr("backup.failed")): \(error.localizedDescription)"
    }

    private func export() {
        isBusy = true
        Task { @MainActor in
            do {
                exportFile = JSONFile(text: try await backupManager.export())
                showExporter = true
            } catch {
                isBusy = false
                show(describe(error), isError: true)
            }
        }
    }

    private func inspect(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let text = try? String(contentsOf: url, encoding: .utf8), !text.isEmpty else {
            show(tr("backup.invalid"), isError: true)
            return
        }
        do {
            pending = (text, try backupManager.inspect(text: text))
        } catch {
            show(describe(error), isError: true)
        }
    }

    private func apply(_ text: String, mode: RestoreMode) {
        isBusy = true
        Task { @MainActor in
            do {
                _ = try await backupManager.restore(text: text, mode: mode)
                show(tr("backup.restored"))
                AppModel.shared.refreshTracking()
            } catch {
                show(describe(error), isError: true)
            }
            isBusy = false
        }
    }
}

/// The exported backup handed to the system file exporter.
private struct JSONFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }

    let text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
