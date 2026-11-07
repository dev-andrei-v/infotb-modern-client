import Shared
import SwiftUI

@Observable
final class LinesModel {
    let viewModel = IosServices.shared.linesViewModel()
    private let scope = KotlinScope()

    private(set) var lines: [Line] = []
    private(set) var isLoading = false
    private(set) var error: String?
    private(set) var selectedTypes: Set<String> = []
    private(set) var favoriteIds: Set<Int64> = []
    private(set) var lineDisplayStyle = "normal"

    var searchQuery = "" {
        didSet {
            if oldValue.isEmpty && !searchQuery.isEmpty {
                collapsedTypesBeforeSearch = collapsedTypes
                collapsedTypes.subtract(TransportType.ordered.map(\.name))
            } else if !oldValue.isEmpty && searchQuery.isEmpty {
                if let collapsedTypesBeforeSearch {
                    self.collapsedTypes = collapsedTypesBeforeSearch
                }
                collapsedTypesBeforeSearch = nil
            }
            viewModel.setSearchQuery(query: searchQuery)
        }
    }
    var nightOnly = false
    var collapsedTypes: Set<String> = []
    private var collapsedTypesBeforeSearch: Set<String>?

    init() {
        scope.own(viewModel)
        scope.watch(viewModel.filteredLines) { [weak self] in self?.lines = Unbox.list($0) }
        scope.watch(viewModel.isLoading) { [weak self] in self?.isLoading = Unbox.bool($0) }
        scope.watch(viewModel.error) { [weak self] in self?.error = $0 as? String }
        scope.watch(viewModel.selectedTypes) { [weak self] value in
            self?.selectedTypes = Set(((value as? NSSet)?.allObjects as? [TransportType] ?? []).map(\.name))
        }
        scope.watch(viewModel.favoriteIds) { [weak self] value in
            self?.favoriteIds = Set((value as? [NSNumber] ?? []).map(\.int64Value))
        }
        scope.watch(viewModel.lineDisplayStyle) { [weak self] value in
            self?.lineDisplayStyle = value as? String ?? "normal"
        }
    }

    func toggleType(_ type: TransportType) {
        nightOnly = false
        viewModel.toggleTypeFilter(type: type)
    }

    func toggleFavorite(_ line: Line) { viewModel.toggleFavorite(lineId: line.id) }
    func reload() { viewModel.loadLines() }
    func setLineDisplayStyle(_ style: String) { viewModel.setLineDisplayStyle(style: style) }

    var favorites: [Line] { lines.filter { favoriteIds.contains($0.id) } }

    var expandableTypes: [TransportType] {
        TransportType.ordered.filter { !dayLines(of: $0).isEmpty }
    }

    var allSectionsExpanded: Bool {
        !expandableTypes.isEmpty && expandableTypes.allSatisfy { !collapsedTypes.contains($0.name) }
    }

    func toggleAllSections() {
        let types = expandableTypes.map(\.name)
        guard !types.isEmpty else { return }
        if allSectionsExpanded { collapsedTypes.formUnion(types) }
        else { types.forEach { collapsedTypes.remove($0) } }
    }

    var nightLines: [Line] { Self.sorted(lines.filter(Self.isNight)) }

    func dayLines(of type: TransportType) -> [Line] {
        Self.sorted(lines.filter { !Self.isNight($0) && $0.type == type })
    }

    static func isNight(_ line: Line) -> Bool { line.name.uppercased().hasPrefix("N") }

    /// Numeric order, so 2 comes before 10.
    static func sorted(_ lines: [Line]) -> [Line] {
        lines.sorted {
            let a = Int64($0.name.filter(\.isNumber)) ?? .max
            let b = Int64($1.name.filter(\.isNumber)) ?? .max
            return a != b ? a < b : $0.name < $1.name
        }
    }
}

struct LinesView: View {
    @Bindable var model: LinesModel
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        content
            .navigationTitle(tr("lines.title"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            model.setLineDisplayStyle("normal")
                        } label: {
                            if model.lineDisplayStyle == "normal" { Label(tr("lines.styleNormal"), systemImage: "checkmark") }
                            else { Text(tr("lines.styleNormal")) }
                        }
                        Button {
                            model.setLineDisplayStyle("bus_display")
                        } label: {
                            if model.lineDisplayStyle == "bus_display" { Label(tr("lines.styleBusDisplay"), systemImage: "checkmark") }
                            else { Text(tr("lines.styleBusDisplay")) }
                        }
                    } label: {
                        Image(systemName: "textformat")
                    }
                    .accessibilityLabel(tr("lines.displayStyle"))
                }
                if !model.nightOnly && !model.expandableTypes.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(action: model.toggleAllSections) {
                            Image(systemName: model.allSectionsExpanded ? "rectangle.compress.vertical" : "rectangle.expand.vertical")
                        }
                        .accessibilityLabel(tr(model.allSectionsExpanded ? "lines.collapseAll" : "lines.expandAll"))
                    }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { bottomBar }
            .scrollDismissesKeyboard(.interactively)
            .refreshable { model.reload() }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading && model.lines.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.error != nil && model.lines.isEmpty {
            ContentUnavailableView {
                Label(tr("lines.errorTitle"), systemImage: "wifi.exclamationmark")
            } description: {
                Text(tr("lines.errorDescription"))
            } actions: {
                Button(tr("common.retry")) { model.reload() }.buttonStyle(.borderedProminent)
            }
        } else if model.lines.isEmpty {
            ContentUnavailableView.search(text: model.searchQuery)
        } else {
            list
        }
    }

    private var list: some View {
        List {
            if !model.favorites.isEmpty {
                Section {
                    ForEach(model.favorites, id: \.id) { row($0, showsType: true) }
                } header: {
                    Label(tr("lines.favorites"), systemImage: "star.fill")
                        .foregroundStyle(Color.favorite)
                }
            }

            if model.nightOnly {
                Section {
                    ForEach(model.nightLines, id: \.id) { row($0) }
                } header: {
                    sectionHeader(tr("lines.night"), count: model.nightLines.count, color: .night, glyph: nil)
                }
            } else {
                ForEach(TransportType.ordered, id: \.name) { type in
                    let lines = model.dayLines(of: type)
                    if !lines.isEmpty {
                        Section(isExpanded: expansion(for: type)) {
                            ForEach(lines, id: \.id) { row($0) }
                        } header: {
                            sectionHeader(type.label, count: lines.count, color: type.color, glyph: type)
                        }
                    }
                }
            }
        }
        .listStyle(.sidebar)
    }

    private func expansion(for type: TransportType) -> Binding<Bool> {
        Binding(
            get: { !model.collapsedTypes.contains(type.name) },
            set: { expanded in
                if expanded { model.collapsedTypes.remove(type.name) } else { model.collapsedTypes.insert(type.name) }
            }
        )
    }

    private func sectionHeader(_ title: String, count: Int, color: Color, glyph: TransportType?) -> some View {
        HStack(spacing: 6) {
            if let glyph {
                VehicleGlyph(type: glyph.name, size: 14)
            }
            Text(title)
            Text("\(count)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .foregroundStyle(color)
    }

    private func row(_ line: Line, showsType: Bool = false) -> some View {
        let isFavorite = model.favoriteIds.contains(line.id)
        let displayLineName = InfoTBDisplayName(line.name, type: line.type)
        return NavigationLink(value: Route.line(line.id)) {
            VStack(alignment: .leading, spacing: 4) {
                if model.lineDisplayStyle == "bus_display" {
                    HStack(spacing: 14) {
                        Text(displayLineName)
                            .font(.system(size: 28, weight: .black, design: .monospaced))
                            .tracking(0.5)
                            .lineLimit(1)
                            .frame(minWidth: 58, alignment: .leading)
                        Text([line.startStopName, line.endStopName].compactMap { $0?.uppercased() }.joined(separator: "\n").isEmpty
                             ? "—"
                             : [line.startStopName, line.endStopName].compactMap { $0?.uppercased() }.joined(separator: "\n"))
                            .font(.system(size: 12, weight: .heavy, design: .monospaced))
                            .tracking(0.35)
                            .foregroundStyle(colorScheme == .dark ? Color(red: 1, green: 0.78, blue: 0.34) : Color(red: 0.60, green: 0.36, blue: 0.02))
                            .lineLimit(2)
                            .lineSpacing(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .foregroundStyle(colorScheme == .dark ? Color(red: 1, green: 0.78, blue: 0.34) : Color(red: 0.60, green: 0.36, blue: 0.02))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, minHeight: 68, alignment: .leading)
                    .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.08)))
                } else {
                    HStack(spacing: 12) {
                        LineBadge(name: displayLineName, color: line.color)
                        if showsType {
                            Text(line.type.label)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if isFavorite {
                            Image(systemName: "star.fill").foregroundStyle(Color.favorite)
                        }
                    }
                }
                if model.lineDisplayStyle == "normal", let start = line.startStopName, let end = line.endStopName {
                    Text("\(start.uppercased())\n\(end.uppercased())")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .tracking(0.6)
                        .lineSpacing(-1)
                        .foregroundStyle(Color(red: 1, green: 0.78, blue: 0.34))
                        .lineLimit(2)
                        .truncationMode(.tail)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color(red: 0.09, green: 0.12, blue: 0.16), in: RoundedRectangle(cornerRadius: 6))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .task(id: line.id) { model.viewModel.loadLineSummary(lineId: line.id) }
        .swipeActions(edge: .trailing) {
            Button {
                model.toggleFavorite(line)
            } label: {
                Label(isFavorite ? tr("lines.unfavorite") : tr("lines.favorite"), systemImage: isFavorite ? "star.slash" : "star")
            }
            .tint(.favorite)
        }
        .contextMenu {
            Button {
                model.toggleFavorite(line)
            } label: {
                Label(isFavorite ? tr("lines.unfavorite") : tr("lines.favorite"), systemImage: isFavorite ? "star.slash" : "star")
            }
        }
    }

    /// Filters and search sit at the bottom, within thumb reach, above the tab bar.
    private var bottomBar: some View {
        VStack(spacing: 8) {
            filterChips
            SearchField(text: $model.searchQuery, prompt: tr("lines.searchPlaceholder"))
                .padding(.horizontal)
        }
        .padding(.bottom, 8)
    }

    /// Transport type and night filters.
    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(TransportType.ordered, id: \.name) { type in
                    FilterChip(
                        title: type.label,
                        color: type.color,
                        isOn: model.selectedTypes.contains(type.name),
                        icon: { VehicleGlyph(type: type.name, size: 13) }
                    ) {
                        model.toggleType(type)
                    }
                }
                FilterChip(
                    title: tr("lines.night"),
                    color: .night,
                    isOn: model.nightOnly,
                    icon: { Image(systemName: "moon.stars.fill").font(.system(size: 12, weight: .semibold)) }
                ) {
                    model.nightOnly.toggle()
                }
            }
            .padding(.horizontal)
        }
        .scrollClipDisabled()
    }
}

/// A capsule toggle: plain glass when off, tinted with its colour when on.
private struct FilterChip<Icon: View>: View {
    let title: String
    let color: Color
    let isOn: Bool
    @ViewBuilder let icon: () -> Icon
    let action: () -> Void

    var body: some View {
        Button {
            withAnimation(.snappy) { action() }
        } label: {
            Label { Text(title) } icon: { icon() }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isOn ? color : .primary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .chipBackground(isOn ? color : nil)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

private extension View {
    @ViewBuilder
    func chipBackground(_ tint: Color?) -> some View {
        if let tint {
            tintedGlass(tint, in: Capsule())
        } else {
            glassBackground(in: Capsule(), interactive: true)
        }
    }

}

/// A glass search capsule; the system one can't sit at the bottom inside a tab view.
private struct SearchField: View {
    @Binding var text: String
    let prompt: String
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField(prompt, text: $text)
                .focused($focused)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .submitLabel(.search)
            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tr("dashboard.cancel"))
            }
        }
        .font(.body)
        .padding(.horizontal, 16)
        .frame(height: 48)
        .glassBackground(in: Capsule(), interactive: true)
        .contentShape(Capsule())
        .onTapGesture { focused = true }
    }
}

