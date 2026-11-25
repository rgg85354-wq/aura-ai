import SwiftUI
import UIKit

private enum MDBlock {
    case heading(Int, String)
    case paragraph(String)
    case bullet(String, String, Int)
    case quote(String)
    case code(String, String)
    case rule
}

private func parseMarkdown(_ text: String) -> [MDBlock] {
    var blocks: [MDBlock] = []
    var paragraph: [String] = []
    func flush() {
        let joined = paragraph.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        if !joined.isEmpty { blocks.append(.paragraph(joined)) }
        paragraph.removeAll()
    }
    let lines = text.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")
    var i = 0
    while i < lines.count {
        let line = lines[i]
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("```") {
            flush()
            let lang = String(trimmed.dropFirst(3)).trimmingCharacters(in: .whitespaces)
            var code: [String] = []
            i += 1
            while i < lines.count && !lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("```") {
                code.append(lines[i]); i += 1
            }
            blocks.append(.code(lang, code.joined(separator: "\n")))
        } else if trimmed.isEmpty {
            flush()
        } else if let level = headingLevel(trimmed) {
            flush()
            blocks.append(.heading(level, String(trimmed.drop(while: { $0 == "#" })).trimmingCharacters(in: .whitespaces)))
        } else if trimmed == "---" || trimmed == "***" {
            flush(); blocks.append(.rule)
        } else if trimmed.hasPrefix(">") {
            flush(); blocks.append(.quote(String(trimmed.dropFirst()).trimmingCharacters(in: .whitespaces)))
        } else if let item = listItem(trimmed) {
            flush()
            let indent = (line.count - line.drop(while: { $0 == " " }).count) / 2
            blocks.append(.bullet(item.0, item.1, indent))
        } else {
            paragraph.append(line)
        }
        i += 1
    }
    flush()
    return blocks
}

private func headingLevel(_ s: String) -> Int? {
    let hashes = s.prefix(while: { $0 == "#" }).count
    guard hashes > 0, hashes <= 6, s.dropFirst(hashes).first == " " else { return nil }
    return hashes
}

private func listItem(_ s: String) -> (String, String)? {
    if let first = s.first, "-*•+".contains(first), s.dropFirst().first == " " {
        return ("•", String(s.dropFirst(2)))
    }
    let digits = s.prefix(while: { $0.isNumber })
    if !digits.isEmpty {
        let rest = s.dropFirst(digits.count)
        if let sep = rest.first, sep == "." || sep == ")", rest.dropFirst().first == " " {
            return ("\(digits).", String(rest.dropFirst(2)))
        }
    }
    return nil
}

private func inline(_ text: String) -> AttributedString {
    let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
    return (try? AttributedString(markdown: text, options: options)) ?? AttributedString(text)
}

struct MarkdownView: View {
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(parseMarkdown(text).enumerated()), id: \.offset) { _, block in
                switch block {
                case .heading(let level, let t):
                    Text(inline(t))
                        .font(.system(size: level == 1 ? 22 : level == 2 ? 19 : 17, weight: .bold))
                        .foregroundStyle(Aura.textPrimary)
                        .padding(.top, 4)
                case .paragraph(let t):
                    Text(inline(t)).font(AuraFont.body).foregroundStyle(Aura.textPrimary)
                case .bullet(let marker, let t, let indent):
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(marker).font(AuraFont.body.weight(.semibold)).foregroundStyle(Aura.lilac).frame(minWidth: 18, alignment: .leading)
                        Text(inline(t)).font(AuraFont.body).foregroundStyle(Aura.textPrimary)
                    }
                    .padding(.leading, CGFloat(indent) * 14)
                case .quote(let t):
                    HStack(spacing: 10) {
                        RoundedRectangle(cornerRadius: 2).fill(Aura.violet).frame(width: 3)
                        Text(inline(t)).font(AuraFont.body.italic()).foregroundStyle(Aura.textSecondary)
                    }
                case .code(let lang, let code):
                    CodeBlockView(language: lang, code: code)
                case .rule:
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1).padding(.vertical, 4)
                }
            }
        }
        .textSelection(.enabled)
        .tint(Aura.lilac)
    }
}

struct CodeBlockView: View {
    let language: String
    let code: String
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(language.isEmpty ? "code" : language).font(AuraFont.caption).foregroundStyle(Aura.textSecondary)
                Spacer()
                Button {
                    UIPasteboard.general.string = code
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copied = false }
                } label: {
                    Label(copied ? "Скопировано" : "Копировать", systemImage: copied ? "checkmark" : "doc.on.doc")
                        .font(AuraFont.caption)
                        .foregroundStyle(Aura.textSecondary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.white.opacity(0.05))

            ScrollView(.horizontal, showsIndicators: false) {
                Text(code)
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Color(hex: 0xE4E1F5))
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(12)
            }
        }
        .background(Color(hex: 0x0D0C12))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color.white.opacity(0.1)))
    }
}
