import SwiftUI
import UIKit

struct PlayerGlass: ViewModifier {
    let enabled: Bool
    @ViewBuilder func body(content: Content) -> some View {
        if !enabled { content }
        else {
            if #available(iOS 26.0, *) {
                content.glassEffect(.regular, in: RoundedRectangle(cornerRadius: 24))
            } else {
                content.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
                    .overlay { RoundedRectangle(cornerRadius: 24).stroke(.white.opacity(0.3), lineWidth: 0.75) }
            }
        }
    }
}

enum AlbumImages {
    static func load(_ url: String) async -> UIImage? {
        guard let url = URL(string: url), ["https", "http"].contains(url.scheme ?? "") else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(for: URLRequest(url: url, timeoutInterval: 6))
            guard let http = response as? HTTPURLResponse, http.statusCode == 200, data.count <= 4 * 1024 * 1024 else { return nil }
            return UIImage(data: data)
        } catch { return nil }
    }
}

struct DirectionalCover: View {
    let track: Track
    let index: Int
    var size: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Track?
    @State private var previousIndex = 0
    @State private var backwards = false
    var body: some View {
        ZStack {
            if let shown {
                Cover(url: shown.artwork, size: size).id(shown.id)
                    .transition(reduceMotion ? .opacity : .asymmetric(
                        insertion: .move(edge: backwards ? .leading : .trailing).combined(with: .opacity),
                        removal: .move(edge: backwards ? .trailing : .leading).combined(with: .opacity)))
            }
        }.frame(width: size, height: size).clipped()
            .onAppear { shown = track; previousIndex = index }
            .onChange(of: track.id) { _, _ in
                backwards = index < previousIndex; previousIndex = index
                withAnimation(.easeInOut(duration: reduceMotion ? 0.2 : 0.52)) { shown = track }
            }
    }
}

struct AlbumBackdrop: View {
    let track: Track?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var image: UIImage?
    @State private var identity = ""
    var body: some View {
        ZStack {
            Color(red: 0.12, green: 0.08, blue: 0.13)
            if let image {
                GeometryReader { geometry in
                    Image(uiImage: image).resizable().scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                        .blur(radius: 36).opacity(0.48)
                }.id(identity).transition(.opacity)
            }
            LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
        }.ignoresSafeArea().allowsHitTesting(false)
            .task(id: track?.id) {
                guard let track else { return }
                let loaded = await AlbumImages.load(track.artwork)
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: reduceMotion ? 0.2 : 1.4)) { image = loaded; identity = track.id }
            }
    }
}

@MainActor struct LyricCardPreview: View {
    let track: Track
    let lines: [String]
    @Environment(\.dismiss) private var dismiss
    @State private var rendered: UIImage?
    var body: some View {
        NavigationStack {
            VStack {
                if let rendered {
                    Image(uiImage: rendered).resizable().scaledToFit().padding()
                    ShareLink(item: Image(uiImage: rendered), preview: SharePreview(track.title, image: Image(uiImage: rendered))) {
                        Label("Share lyric card", systemImage: "square.and.arrow.up")
                    }.buttonStyle(.borderedProminent)
                } else { ProgressView("Preparing card…") }
            }.navigationTitle("Share lyrics").toolbar { Button("Done") { dismiss() } }
                .task {
                    let art = await AlbumImages.load(track.artwork)
                    guard !Task.isCancelled else { return }
                    let renderer = ImageRenderer(content: card(art))
                    renderer.scale = 3
                    rendered = renderer.uiImage
                }
        }
    }
    private func card(_ art: UIImage?) -> some View {
        VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 12) {
                Group {
                    if let art { Image(uiImage: art).resizable().scaledToFill() }
                    else { ZStack { Color.purple; Image(systemName: "music.note").font(.largeTitle) } }
                }.frame(width: 74, height: 74).clipShape(RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 6) {
                    Text(track.title).font(.headline).lineLimit(2)
                    Text(track.artist).font(.subheadline).foregroundStyle(.white.opacity(0.7)).lineLimit(2)
                }
            }
            Divider().overlay(.white.opacity(0.3))
            Text(lines.joined(separator: "\n")).font(.system(size: 20, weight: .semibold)).minimumScaleFactor(0.65)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            Text("MUSICTRANSI").font(.caption.bold()).foregroundStyle(.white.opacity(0.6))
        }.padding(28).frame(width: 360, height: 450).foregroundStyle(.white)
            .background(LinearGradient(colors: [Color(red: 0.32, green: 0.17, blue: 0.3), Color(red: 0.08, green: 0.08, blue: 0.11)], startPoint: .topLeading, endPoint: .bottomTrailing))
    }
}
