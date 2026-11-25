import SwiftUI

private struct Tool: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let icon: String
    let route: Route
}

private let tools: [Tool] = [
    Tool(title: "Чат с ИИ", subtitle: "Любые вопросы", icon: "bubble.left.and.bubble.right", route: .chat(id: nil, mode: ChatMode.chat, draft: "")),
    Tool(title: "Изображения", subtitle: "Генерация по описанию", icon: "photo", route: .images),
    Tool(title: "Написать код", subtitle: "Функции, скрипты, приложения", icon: "chevron.left.forwardslash.chevron.right", route: .chat(id: nil, mode: ChatMode.code, draft: "")),
    Tool(title: "Объяснить код", subtitle: "Разбор по шагам", icon: "magnifyingglass", route: .chat(id: nil, mode: ChatMode.code, draft: "Объясни, что делает этот код:\n\n")),
    Tool(title: "Найти ошибку", subtitle: "Отладка и исправления", icon: "ladybug", route: .chat(id: nil, mode: ChatMode.code, draft: "Найди и исправь ошибку в коде:\n\n")),
    Tool(title: "Идеи", subtitle: "Мозговой штурм", icon: "lightbulb", route: .chat(id: nil, mode: ChatMode.ideas, draft: "")),
    Tool(title: "Анализ файла", subtitle: "PDF, текст, фото", icon: "doc.text.magnifyingglass", route: .chat(id: nil, mode: ChatMode.chat, draft: "attach")),
    Tool(title: "Перевод", subtitle: "На любой язык", icon: "character.bubble", route: .chat(id: nil, mode: ChatMode.chat, draft: "Переведи на английский:\n\n")),
    Tool(title: "Пересказ", subtitle: "Кратко о главном", icon: "text.alignleft", route: .chat(id: nil, mode: ChatMode.chat, draft: "Сделай краткий пересказ текста:\n\n")),
    Tool(title: "Письмо", subtitle: "Деловое или личное", icon: "envelope", route: .chat(id: nil, mode: ChatMode.chat, draft: "Помоги написать письмо: ")),
    Tool(title: "Улучшить текст", subtitle: "Стиль и грамотность", icon: "wand.and.stars", route: .chat(id: nil, mode: ChatMode.chat, draft: "Исправь ошибки и улучши стиль текста:\n\n")),
]

struct ToolsView: View {
    @EnvironmentObject private var router: Router

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text("Инструменты").font(AuraFont.headline).foregroundStyle(Aura.textPrimary)
                Text("Выберите, с чем помочь").font(AuraFont.bodySmall).foregroundStyle(Aura.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 12)

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(tools) { tool in
                    Button { router.open(tool.route) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            IconTile(icon: tool.icon, size: 44)
                            Spacer()
                            Text(tool.title).font(AuraFont.titleMedium).foregroundStyle(Aura.textPrimary).lineLimit(1)
                            Text(tool.subtitle).font(AuraFont.caption).foregroundStyle(Aura.textSecondary).lineLimit(1)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, minHeight: 132, maxHeight: 132, alignment: .leading)
                        .glass()
                    }
                    .buttonStyle(PressableStyle())
                }
            }
            .padding(.top, 12)
            Spacer().frame(height: tabBarSpace + 24)
        }
        .padding(.horizontal, 20)
    }
}
