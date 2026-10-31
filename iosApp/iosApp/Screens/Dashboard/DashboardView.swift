import Shared
import SwiftUI

struct DashboardView: View {
    @Bindable var model: DashboardModel
    private let app = AppModel.shared

    @Environment(\.scenePhase) private var scenePhase
    @State private var optionsWidgetId: String?
    @State private var showGroups = false
    @State private var confirmReset = false

    var body: some View {
        Group {
            if model.widgets.isEmpty {
                emptyState
            } else if model.isEditing {
                editList
            } else {
                cards
            }
        }
        .navigationTitle(tr("dashboard.title"))
        .toolbar { toolbar }
        .onAppear { model.setActive(true) }
        .onDisappear { model.setActive(false) }
        .onChange(of: scenePhase) { _, phase in model.setActive(phase == .active) }
        .sheet(item: Binding(get: { optionsWidgetId.map(WidgetRef.init) }, set: { optionsWidgetId = $0?.id })) { ref in
            WidgetOptionsSheet(model: model, widgetId: ref.id)
        }
        .sheet(isPresented: $showGroups) {
            GroupsSheet(model: model)
        }
        .confirmationDialog(tr("dashboard.resetConfirmTitle"), isPresented: $confirmReset, titleVisibility: .visible) {
            Button(tr("dashboard.resetConfirm"), role: .destructive) { model.clearAll() }
        } message: {
            Text(tr("dashboard.resetConfirmDescription"))
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if model.isEditing {
            ToolbarItem(placement: .confirmationAction) {
                Button(tr("dashboard.done")) { model.toggleEditing() }
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !model.widgets.isEmpty {
                    Button(tr("dashboard.edit")) { model.toggleEditing() }
                }
                Button {
                    app.selectedTab = .lines
                } label: {
                    Label(tr("widget.addStation"), systemImage: "plus")
                }
            }
        }
    }

    // MARK: Empty

    private var emptyState: some View {
        ContentUnavailableView {
            Label(tr("dashboard.emptyTitle"), systemImage: "square.grid.2x2")
        } description: {
            Text(tr("dashboard.emptyDescription"))
        } actions: {
            Button {
                app.selectedTab = .lines
            } label: {
                Label(tr("widget.addStation"), systemImage: "plus")
            }
            .buttonStyle(.borderedProminent)
        }
    }

    // MARK: Cards

    private var cards: some View {
        TimelineView(.periodic(from: .now, by: 5)) { context in
            let now = context.date
            let sections = model.sections(at: now)
            let showHeaders = model.showsHeaders(sections)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    statusHeader(now: now)

                    if model.visibleCount == 0 {
                        Text(tr("dashboard.allHidden"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 60)
                    }

                    ForEach(sections) { section in
                        if !section.isHidden && section.widgets.contains(where: { !$0.hidden }) {
                            if showHeaders { sectionHeader(section) }
                            if !model.isCollapsed(section) {
                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 320), spacing: 12)], spacing: 12) {
                                    ForEach(section.widgets.filter { !$0.hidden }, id: \.id) { widget in
                                        DashboardCard(
                                            model: model,
                                            widget: widget,
                                            now: now,
                                            showGroup: model.nearestMode,
                                            onOptions: { optionsWidgetId = widget.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
                .animation(.snappy, value: model.widgets.map(\.id))
            }
            .background(Color(.systemGroupedBackground))
            .refreshable { await model.refresh() }
        }
    }

    @ViewBuilder
    private func statusHeader(now: Date) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Group {
                if model.isRefreshing {
                    Text(tr("dashboard.updating"))
                } else if model.lastRefreshMs > 0 {
                    Text("\(tr("dashboard.updated")) \(DashboardFormat.ago(Int64(now.timeIntervalSince1970 * 1000) - model.lastRefreshMs))")
                }
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
            .contentTransition(.numericText())

            if model.widgets.count > 1 && model.locationSupported {
                Picker(tr("dashboard.sortGroups"), selection: Binding(get: { model.sortMode }, set: { model.setSort($0) })) {
                    Text(tr("dashboard.sortGroups")).tag(DashboardSort.groups)
                    Label(tr("dashboard.sortNearest"), systemImage: "location.fill").tag(DashboardSort.nearest)
                }
                .pickerStyle(.segmented)
            }

            if model.nearestMode && !model.hasUserLocation {
                if model.locationLoading {
                    Label(tr("dashboard.locationLoading"), systemImage: "location")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if model.locationFailed {
                    HStack {
                        Label(tr("dashboard.locationUnavailable"), systemImage: "location.slash")
                            .font(.footnote)
                            .foregroundStyle(.red)
                        Spacer()
                        Button(tr("dashboard.locationRetry")) { model.refreshLocation() }
                            .font(.footnote.weight(.semibold))
                    }
                }
            }
        }
    }

    private func sectionHeader(_ section: DashboardSection) -> some View {
        let collapsed = model.isCollapsed(section)
        return Button {
            withAnimation(.snappy) { model.toggleCollapsed(section) }
        } label: {
            HStack(spacing: 6) {
                Text(section.name.isEmpty ? tr("widget.noGroup") : section.name)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.primary)
                if let schedule = section.schedule {
                    Label(DashboardFormat.schedule(schedule), systemImage: "clock")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.secondary)
                        .labelStyle(.titleAndIcon)
                }
                Spacer()
                if section.isInactive {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .rotationEffect(.degrees(collapsed ? 0 : 90))
                }
            }
            .opacity(collapsed ? 0.55 : 1)
            .padding(.top, 8)
        }
        .buttonStyle(.plain)
        .disabled(!section.isInactive)
    }

    // MARK: Edit mode

    /// Reorder by dragging, delete by swiping, hide with the eye; groups get their own sheet.
    private var editList: some View {
        let sections = model.sections(at: .now)
        let showHeaders = model.showsHeaders(sections)
        return List {
            ForEach(sections) { section in
                Section {
                    ForEach(section.widgets, id: \.id) { widget in
                        editRow(widget, groupHidden: section.isHidden)
                    }
                    .onMove { model.move(in: section, from: $0, to: $1) }
                    .onDelete { offsets in offsets.forEach { model.remove(section.widgets[$0]) } }
                } header: {
                    if showHeaders {
                        HStack {
                            Text(section.name.isEmpty ? tr("widget.noGroup") : section.name)
                            if let schedule = section.schedule {
                                Text(DashboardFormat.schedule(schedule))
                            }
                            Spacer()
                            if !section.name.isEmpty {
                                Button {
                                    model.toggleGroupHidden(section.name)
                                } label: {
                                    Image(systemName: section.isHidden ? "eye.slash" : "eye")
                                }
                                .accessibilityLabel(section.isHidden ? tr("widget.show") : tr("widget.hide"))
                            }
                        }
                    }
                }
            }

            Section {
                Button {
                    showGroups = true
                } label: {
                    Label(tr("dashboard.manageGroupsTitle"), systemImage: "folder")
                }
                Button(role: .destructive) {
                    confirmReset = true
                } label: {
                    Label(tr("dashboard.reset"), systemImage: "trash")
                }
            }
        }
        .listStyle(.insetGrouped)
        .environment(\.editMode, .constant(.active))
    }

    private func editRow(_ widget: WidgetConfig, groupHidden: Bool) -> some View {
        HStack {
            Button {
                optionsWidgetId = widget.id
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(widget.stopName).foregroundStyle(.primary)
                    if widget.size == .large {
                        Text(tr("widget.sizeLarge")).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button {
                model.toggleHidden(widget)
            } label: {
                Image(systemName: widget.hidden ? "eye.slash" : "eye")
                    .foregroundStyle(widget.hidden ? Color.secondary : Color.accentColor)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(widget.hidden ? tr("widget.show") : tr("widget.hide"))
        }
        .opacity(widget.hidden || groupHidden ? 0.5 : 1)
    }
}

/// Identifiable wrapper so a widget id can drive `.sheet(item:)`.
private struct WidgetRef: Identifiable {
    let id: String
}
