import Shared
import SwiftUI

/// Per-card settings: which lines show, size, group, hide and delete.
struct WidgetOptionsSheet: View {
    let model: DashboardModel
    let widgetId: String

    @Environment(\.dismiss) private var dismiss
    @State private var newGroup = ""

    var body: some View {
        NavigationStack {
            if let widget = model.widgets.first(where: { $0.id == widgetId }) {
                form(widget)
                    .navigationTitle(widget.stopName)
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func form(_ widget: WidgetConfig) -> some View {
        let allLines = model.cardStates[widget.stopId]?.info?.lines ?? []
        return Form {
            if !allLines.isEmpty {
                Section {
                    ForEach(allLines, id: \.lineKey) { line in
                        let key = line.lineKey
                        let visible = widget.visibleLines.isEmpty || widget.visibleLines.contains(key)
                        Button {
                            model.toggleLine(widget, key: key)
                        } label: {
                            HStack(spacing: 10) {
                                LineBadge(name: line.name, color: line.color, size: .small)
                                Text(line.directionName).foregroundStyle(.primary).lineLimit(1)
                                Spacer()
                                Image(systemName: visible ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(visible ? Color.accentColor : .secondary)
                                    .font(.title3)
                            }
                        }
                    }
                } header: {
                    Text(tr("widget.lines"))
                } footer: {
                    Text(tr("widget.linesHint"))
                }
            }

            Section {
                Picker(tr("widget.size"), selection: Binding(get: { widget.size }, set: { model.resize(widget, to: $0) })) {
                    Text(tr("widget.sizeMedium")).tag(WidgetSize.medium)
                    Text(tr("widget.sizeLarge")).tag(WidgetSize.large)
                }
                .pickerStyle(.segmented)
            } header: {
                Text(tr("widget.size"))
            } footer: {
                Text(tr("widget.sizeHint"))
            }

            Section(tr("widget.group")) {
                Picker(tr("widget.group"), selection: Binding(get: { widget.group }, set: { model.setGroup(widget, to: $0) })) {
                    Text(tr("widget.noGroup")).tag("")
                    ForEach(model.groups, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()

                HStack {
                    TextField(tr("widget.newGroupName"), text: $newGroup)
                        .submitLabel(.done)
                        .onSubmit { addGroup(to: widget) }
                    Button {
                        addGroup(to: widget)
                    } label: {
                        Image(systemName: "plus.circle.fill").font(.title3)
                    }
                    .buttonStyle(.borderless)
                    .disabled(newGroup.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }

            Section {
                Button {
                    model.toggleHidden(widget)
                    dismiss()
                } label: {
                    Label(widget.hidden ? tr("widget.show") : tr("widget.hide"), systemImage: widget.hidden ? "eye" : "eye.slash")
                }
                Button(role: .destructive) {
                    dismiss()
                    model.remove(widget)
                } label: {
                    Label(tr("widget.delete"), systemImage: "trash")
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(tr("dashboard.done")) { dismiss() }
            }
        }
    }

    private func addGroup(to widget: WidgetConfig) {
        let name = newGroup.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        model.addGroup(name)
        model.setGroup(widget, to: name)
        newGroup = ""
    }
}

/// Rename, reorder, hide, schedule and delete groups.
struct GroupsSheet: View {
    let model: DashboardModel

    @Environment(\.dismiss) private var dismiss
    @State private var newName = ""
    @State private var renaming: String?
    @State private var renameText = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if model.groups.isEmpty {
                        Text(tr("dashboard.noGroups")).foregroundStyle(.secondary)
                    }
                    ForEach(model.groups, id: \.self) { group in
                        row(group)
                    }
                    .onMove { model.moveGroups(from: $0, to: $1) }
                    .onDelete { offsets in offsets.forEach { model.removeGroup(model.groups[$0]) } }
                } footer: {
                    Text(tr("dashboard.manageGroupsDescription"))
                }

                Section {
                    HStack {
                        TextField(tr("widget.newGroupName"), text: $newName)
                            .submitLabel(.done)
                            .onSubmit(add)
                        Button(action: add) {
                            Image(systemName: "plus.circle.fill").font(.title3)
                        }
                        .buttonStyle(.borderless)
                        .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .navigationTitle(tr("dashboard.manageGroupsTitle"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("dashboard.done")) { dismiss() }
                }
            }
            .navigationDestination(for: String.self) { group in
                GroupHoursView(model: model, group: group)
            }
            .alert(tr("dashboard.groupRename"), isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField(tr("widget.newGroupName"), text: $renameText)
                Button(tr("dashboard.cancel"), role: .cancel) {}
                Button(tr("dashboard.groupSave")) {
                    if let old = renaming, !renameText.trimmingCharacters(in: .whitespaces).isEmpty {
                        model.renameGroup(old, to: renameText.trimmingCharacters(in: .whitespaces))
                    }
                }
            }
        }
    }

    private func row(_ group: String) -> some View {
        let hidden = model.hiddenGroups.contains(group)
        let schedule = model.schedules[group]
        return HStack {
            NavigationLink(value: group) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(group).font(.body.weight(.semibold))
                    Label(schedule.map(DashboardFormat.schedule) ?? tr("dashboard.groupHoursSet"), systemImage: "clock")
                        .font(.caption)
                        .foregroundStyle(schedule != nil ? Color.accentColor : .secondary)
                }
            }
            Button {
                model.toggleGroupHidden(group)
            } label: {
                Image(systemName: hidden ? "eye.slash" : "eye")
                    .foregroundStyle(hidden ? Color.secondary : Color.accentColor)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(hidden ? tr("widget.show") : tr("widget.hide"))
        }
        .opacity(hidden ? 0.55 : 1)
        .swipeActions(edge: .leading) {
            Button {
                renameText = group
                renaming = group
            } label: {
                Label(tr("dashboard.groupRename"), systemImage: "pencil")
            }
            .tint(.accentColor)
        }
        .contextMenu {
            Button {
                renameText = group
                renaming = group
            } label: {
                Label(tr("dashboard.groupRename"), systemImage: "pencil")
            }
            Button(role: .destructive) {
                model.removeGroup(group)
            } label: {
                Label(tr("dashboard.groupDelete"), systemImage: "trash")
            }
        }
    }

    private func add() {
        let name = newName.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        model.addGroup(name)
        newName = ""
    }
}

/// The daily window during which a group is expanded on the dashboard.
private struct GroupHoursView: View {
    let model: DashboardModel
    let group: String

    @Environment(\.dismiss) private var dismiss
    @State private var from = Date()
    @State private var to = Date()

    var body: some View {
        Form {
            Section {
                DatePicker(tr("dashboard.groupHoursFrom"), selection: $from, displayedComponents: .hourAndMinute)
                DatePicker(tr("dashboard.groupHoursTo"), selection: $to, displayedComponents: .hourAndMinute)
            } footer: {
                Text(tr("dashboard.groupHoursDescription"))
            }

            if model.schedules[group] != nil {
                Section {
                    Button(tr("dashboard.groupHoursAlways"), role: .destructive) {
                        model.setSchedule(group, nil)
                        dismiss()
                    }
                }
            }
        }
        .navigationTitle("\(group) · \(tr("dashboard.groupHours"))")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(tr("dashboard.groupSave")) {
                    model.setSchedule(group, GroupSchedule(fromMinutes: Self.minutes(from), toMinutes: Self.minutes(to)))
                    dismiss()
                }
            }
        }
        .onAppear {
            let current = model.schedules[group]
            from = Self.date(current?.fromMinutes ?? 7 * 60)
            to = Self.date(current?.toMinutes ?? 9 * 60 + 30)
        }
    }

    private static func date(_ minutes: Int32) -> Date {
        Calendar.current.date(bySettingHour: Int(minutes / 60), minute: Int(minutes % 60), second: 0, of: .now) ?? .now
    }

    private static func minutes(_ date: Date) -> Int32 {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return Int32((parts.hour ?? 0) * 60 + (parts.minute ?? 0))
    }
}
