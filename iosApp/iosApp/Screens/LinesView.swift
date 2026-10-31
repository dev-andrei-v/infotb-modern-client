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

    var searchQuery = "" {
        didSet { viewModel.setSearchQuery(query: searchQuery) }
    }
    var nightOnly = false
    var collapsedTypes: Set<String> = []

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
    }

    func toggleType(_ type: TransportType) {
        nightOnly = false
        viewModel.toggleTypeFilter(type: type)
    }

    func toggleFavorite(_ line: Line) { viewModel.toggleFavorite(lineId: line.id) }
    func reload() { viewModel.loadLines() }

    var favorites: [Line] { lines.filter { favoriteIds.contains($0.id) } }

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

    var body: some View {
        content
            .navigationTitle(tr("lines.title"))
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
        return NavigationLink(value: Route.line(line.id)) {
            HStack(spacing: 12) {
                LineBadge(name: line.name, color: line.color)
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

