import SwiftUI
import Foundation

struct LyricLine: Identifiable {
    let id: Int
    let time: Double
    let text: String
}
struct SongLyrics {
    var lines: [LyricLine] = []
    var plain = ""
    var instrumental = false
}
actor LyricsProvider {
    static let shared = LyricsProvider()
    private var cache: [String: SongLyrics] = [:]
    private var order: [String] = []
    private var retryAfter = Date.distantPast
    private func clean(_ value: String) -> String {
        value.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .filter { $0.isLetter || $0.isNumber }
    }
    func fetch(_ track: Track, duration: Int) async throws -> SongLyrics {
        if track.local { return SongLyrics() }
        let key = "\(track.id):\(duration)"
        if let cached = cache[key] { return cached }
        guard Date() >= retryAfter else { throw URLError(.resourceUnavailable) }
        let artist = track.artist.replacingOccurrences(of: #"(?i)\s*-\s*Topic$"#, with: "", options: .regularExpression)
        let title = track.title.replacingOccurrences(of: #"(?i)\s*[\[(](?:official\s+(?:audio|video)|lyrics?|visuali[sz]er)[\])]"#, with: "", options: .regularExpression)
        var url = URLComponents(string: "https://lrclib.net/api/get")!
        url.queryItems = [URLQueryItem(name: "track_name", value: title), URLQueryItem(name: "artist_name", value: artist)]
        if duration > 0 { url.queryItems?.append(URLQueryItem(name: "duration", value: String(duration))) }
        var request = URLRequest(url: url.url!, timeoutInterval: 12)
        request.setValue("MusicTransi/0.12 (personal music player)", forHTTPHeaderField: "User-Agent")
        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        if http.statusCode == 404 { return SongLyrics() }
        if http.statusCode == 429 {
            let delay = Double(http.value(forHTTPHeaderField: "Retry-After") ?? "") ?? 60
            retryAfter = Date().addingTimeInterval(max(1, delay))
        }
        guard http.statusCode == 200 else { throw URLError(.badServerResponse) }
        var data = Data()
        for try await byte in bytes {
            try Task.checkCancellation()
            guard data.count < 524288 else { throw URLError(.dataLengthExceedsMaximum) }
            data.append(byte)
        }
        struct Response: Decodable {
            let trackName: String; let artistName: String
            let syncedLyrics: String?; let plainLyrics: String?; let instrumental: Bool?
        }
        let value = try JSONDecoder().decode(Response.self, from: data)
        guard clean(value.trackName) == clean(title), clean(value.artistName) == clean(artist) else { return SongLyrics() }
        let result = SongLyrics(lines: Self.parse(value.syncedLyrics ?? ""), plain: value.plainLyrics ?? "", instrumental: value.instrumental ?? false)
        cache[key] = result; order.removeAll { $0 == key }; order.append(key)
        if order.count > 16 { cache.removeValue(forKey: order.removeFirst()) }
        return result
    }
    static func parse(_ text: String) -> [LyricLine] {
        let regex = try! NSRegularExpression(pattern: #"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]"#)
        let offsetRegex = try! NSRegularExpression(pattern: #"(?i)\[offset:([+-]?\d+)\]"#)
        let ns = text as NSString
        let match = offsetRegex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length))
        let offset = match.flatMap { Double(ns.substring(with: $0.range(at: 1))) }.map { $0 / 1000 } ?? 0
        var result: [(Double, String)] = []
        for raw in text.components(separatedBy: .newlines).prefix(2000) {
            let line = raw as NSString
            let range = NSRange(location: 0, length: line.length)
            let words = regex.stringByReplacingMatches(in: raw, range: range, withTemplate: "").trimmingCharacters(in: .whitespaces)
            for m in regex.matches(in: raw, range: range) {
                let seconds = Double(line.substring(with: m.range(at: 2))) ?? 0
                guard seconds < 60 else { continue }
                let minutes = Double(line.substring(with: m.range(at: 1))) ?? 0
                let fraction = m.range(at: 3).location == NSNotFound ? 0 : Double("0." + line.substring(with: m.range(at: 3))) ?? 0
                result.append((max(0, minutes * 60 + seconds + fraction + offset), words))
            }
        }
        return result.sorted { $0.0 < $1.0 }.prefix(2000).enumerated().map { LyricLine(id: $0.offset, time: $0.element.0, text: $0.element.1) }
    }
}

struct LyricsView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var result: SongLyrics?
    @State private var error: String?
    @State private var follow = true
    @State private var offset = 0.0
    @State private var retry = 0
    @State private var selecting = false
    @State private var selected = Set<Int>()
    @State private var sharing = false
    private var shareLines: [String] {
        guard let result else { return [] }
        return result.lines.isEmpty ? result.plain.components(separatedBy: .newlines).filter { !$0.isEmpty } : result.lines.map(\.text)
    }
    private func choose(_ index: Int) {
        var next = selected
        if next.contains(index) { next.remove(index) } else { next.insert(index) }
        let words = next.sorted().compactMap { shareLines.indices.contains($0) ? shareLines[$0] : nil }
        if words.count <= 4 && words.joined(separator: "\n").count <= 280 { selected = next }
    }
    private var active: Int? { result?.lines.last(where: { $0.time <= store.position + offset })?.id }
    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                if !shareLines.isEmpty {
                    HStack {
                        Button(selecting ? "Cancel selection" : "Share lines") { selecting.toggle(); selected = []; if selecting { follow = false } }
                        Spacer()
                        if selecting { Button("Preview") { sharing = true }.disabled(selected.isEmpty) }
                    }.padding(.horizontal)
                    if selecting { Text("Choose up to 4 lines · 280 characters").font(.caption) }
                }
                if !(result?.lines.isEmpty ?? true) {
                    Toggle("Follow playback", isOn: $follow).padding(.horizontal)
                }
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 22) {
                            if let error { Text(error); Button("Retry") { retry += 1 } }
                            else if let result {
                                if result.instrumental { Text("This recording is marked instrumental.") }
                                else if !result.lines.isEmpty {
                                    ForEach(result.lines) { line in
                                        Button { if selecting { choose(line.id) } else { store.seek(max(0, line.time - offset)); follow = true } } label: {
                                            Text(line.text.isEmpty ? "♪" : line.text)
                                                .font(.title2.weight(.semibold))
                                                .foregroundStyle((selecting ? selected.contains(line.id) : active == line.id) ? Color.pink : Color.secondary)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                        }.buttonStyle(.plain).id(line.id)
                                    }
                                } else if result.plain.isEmpty { Text("No matching lyrics available.") }
                                else { ForEach(Array(shareLines.enumerated()), id: \.offset) { index, text in
                                    Button { if selecting { choose(index) } } label: {
                                        Text(text).foregroundStyle(selected.contains(index) ? Color.pink : Color.primary)
                                    }.buttonStyle(.plain)
                                } }
                            } else { ProgressView("Loading lyrics…") }
                        }.padding()
                    }.onChange(of: active) { _, value in
                        if follow && !selecting, let value { withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(value, anchor: .center) } }
                    }.onChange(of: follow) { _, enabled in
                        if enabled, let active { withAnimation { proxy.scrollTo(active, anchor: .top) } }
                    }
                }
                if !(result?.lines.isEmpty ?? true) {
                    Stepper("Lyrics offset: \(offset, specifier: "%.1f")s", value: $offset, in: -3...3, step: 0.1).font(.caption).padding(.horizontal)
                }
                Text("Lyrics from LRCLIB · availability and timing vary").font(.caption2).foregroundStyle(.secondary)
                Divider()
                PlayerTransport().padding(.horizontal).padding(.bottom, 8)
            }.navigationTitle(store.current?.title ?? "Lyrics").navigationBarTitleDisplayMode(.inline)
                .toolbar { Button("Done") { dismiss() } }
                .task(id: "\(store.current?.id ?? ""):\(Int(store.duration)):\(retry)") {
                    result = nil; error = nil
                    guard let track = store.current else { return }
                    do {
                        let fetched = try await LyricsProvider.shared.fetch(track, duration: Int(store.duration))
                        try Task.checkCancellation(); result = fetched
                    } catch is CancellationError { }
                    catch { if !Task.isCancelled { self.error = "Couldn't load lyrics. Check your connection and retry." } }
                }.onChange(of: store.current?.id) { _, _ in offset = 0; selected = []; selecting = false; sharing = false }
                .sheet(isPresented: $sharing) {
                    if let track = store.current { LyricCardPreview(track: track, lines: selected.sorted().compactMap { shareLines.indices.contains($0) ? shareLines[$0] : nil }) }
                }
        }
    }
}
