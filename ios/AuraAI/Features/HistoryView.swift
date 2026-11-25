import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var store: AuraStore
    @EnvironmentObject private var router: Router
    @State private var query = ""
    @State private var results: [Chat]?
    @State private var error: String?
    @State private var renaming: Chat?
    @State private var newTitle = ""
    @State private var deleting: Chat?

    private var shown: [Chat] { results ?? store.chats }

    private var groups: [(String, [Chat])] {
        var order: [String] = []
        var map: [String: [Chat]] = [:]
        for chat in shown {
            let key = Format.dayGroup(chat.updatedAt)
            if map[key] == nil { order.append(key) }
            map[key, default: []].append(chat)
        }
        return order.map { ($0, map[$0] ?? []) }
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("История").font(AuraFont.headline).foregroundStyle(Aura.textPrimary)
                Spacer()
                GlassIconButton(icon: "plus", filled: true) { router.newChat() }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)

            AuraTextField(placeholder: "Поиск по чатам", text: $query, icon: "magnifyingglass")
                .padding(.horizontal, 20)

            if let error {
                ErrorBanner(message: error, action: "OK") { self.error = nil }.padding(.horizontal, 20)
            }

            if shown.isEmpty {
                Spacer()
                EmptyStateView(
                    icon: "clock",
                    title: query.isEmpty ? "Пока нет чатов" : "Ничего не найдено",
                    subtitle: query.isEmpty ? "Начните разговор, и он появится здесь на всех ваших устройствах" : "Попробуйте другой запрос"
                )
                Spacer()
            } else {
                List {
                    ForEach(groups, id: \.0) { group, chats in
                        Section {
                            ForEach(chats) { chat in
                                ChatRow(chat: chat, showPreview: true) {
                                    router.open(.chat(id: chat.id, mode: chat.mode, draft: ""))
                                }
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .listRowInsets(EdgeInsets(top: 5, leading: 20, bottom: 5, trailing: 20))
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) { deleting = chat } label: { Label("Удалить", systemImage: "trash") }
                                    Button { newTitle = chat.title; renaming = chat } label: { Label("Имя", systemImage: "pencil") }
                                        .tint(Aura.violet)
                                }
                                .contextMenu {
                                    Button { newTitle = chat.title; renaming = chat } label: { Label("Переименовать", systemImage: "pencil") }
                                    Button(role: .destructive) { deleting = chat } label: { Label("Удалить", systemImage: "trash") }
                                }
                            }
                        } header: {
                            Text(group).font(AuraFont.caption).foregroundStyle(Aura.textSecondary).textCase(nil)
                        }
                    }
                    Color.clear.frame(height: tabBarSpace).listRowBackground(Color.clear).listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable {
                    do { try await store.sync() } catch { self.error = error.localizedDescription }
                }
            }
        }
        .task(id: query) {
            // Search on the server (titles and message text) with a short debounce.
            let q = query.trimmingCharacters(in: .whitespaces)
            guard !q.isEmpty else { results = nil; return }
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            do {
                results = try await store.searchChats(q)
            } catch {
                results = store.chats.filter { $0.title.localizedCaseInsensitiveContains(q) }
            }
        }
        .alert("Название чата", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Название", text: $newTitle)
            Button("Сохранить") {
                guard let chat = renaming, !newTitle.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                Task {
                    do { try await store.renameChat(id: chat.id, title: newTitle) } catch { self.error = error.localizedDescription }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog(
            "Удалить «\(deleting?.title ?? "")»?",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible
        ) {
            Button("Удалить", role: .destructive) {
                guard let chat = deleting else { return }
                Task {
                    do {
                        try await store.deleteChat(id: chat.id)
                        results = results?.filter { $0.id != chat.id }
                    } catch {
                        self.error = error.localizedDescription
                    }
                }
            }
        } message: {
            Text("Чат удалится на всех ваших устройствах.")
        }
    }
}
