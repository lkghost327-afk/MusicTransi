import SwiftUI
import UniformTypeIdentifiers

@main @MainActor struct MusicTransiApp: App {
    @StateObject private var store = Store()
    @AppStorage("appearance") private var appearance = 0
    var body: some Scene { WindowGroup { RootView().environmentObject(store).tint(.pink)
        .preferredColorScheme(appearance == 0 ? nil : appearance == 1 ? .light : .dark) } }
}
struct Cover: View {
    var url: String
    var size: CGFloat = 50
    var body: some View {
        AsyncImage(url: URL(string: url), transaction: Transaction(animation: .easeInOut(duration: 0.4))) { phase in
            if let image = phase.image { image.resizable().scaledToFill().transition(.opacity) }
            else { ZStack { Color.pink.opacity(0.12); Image(systemName: "music.note").foregroundStyle(.pink) } }
        }
            .frame(width: size, height: size).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
struct RootView: View {
    @EnvironmentObject var store: Store
    @State private var player = false
    @State private var settings = false
    @State private var importing = false
    var body: some View {
        TabView {
            NavigationStack { SearchView() }.tabItem { Label("Search", systemImage: "magnifyingglass") }
            NavigationStack { QueueView() }.tabItem { Label("Queue", systemImage: "list.bullet") }
            NavigationStack { LibraryView() }.tabItem { Label("Library", systemImage: "square.stack") }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 0) {
                if let track = store.current {
                    HStack {
                        Button { player = true } label: {
                            HStack { Cover(url: track.artwork, size: 42); VStack(alignment: .leading) {
                                Text(track.title).font(.subheadline.bold()).lineLimit(1)
                                Text(store.loading ? "Loading…" : track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }.frame(maxWidth: .infinity, alignment: .leading) }.foregroundStyle(.primary)
                        }
                        Button { store.playing ? store.pause() : store.resume() } label: { Image(systemName: store.playing ? "pause.fill" : "play.fill") }.accessibilityLabel(store.playing ? "Pause" : "Play")
                        Button { store.next() } label: { Image(systemName: "forward.end.fill") }.accessibilityLabel("Next")
                    }.padding(12).simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { value in
                        if value.translation.height < -60 && abs(value.translation.height) > abs(value.translation.width) { player = true }
                    })
                }
                HStack {
                    Button("Import audio", systemImage: "plus") { importing = true }
                    Spacer(); Button("Settings", systemImage: "gearshape") { settings = true }
                }.font(.caption).padding(.horizontal).padding(.vertical, 8)
            }.background(.regularMaterial)
        }
        .sheet(isPresented: $player) { PlayerView() }
        .sheet(isPresented: $settings) { SettingsView() }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.audio], allowsMultipleSelection: true) { result in
            switch result { case .success(let urls): store.importFiles(urls); case .failure(let error): store.message = error.localizedDescription }
        }
        .alert("MusicTransi", isPresented: Binding(get: { store.message != nil }, set: { if !$0 { store.message = nil } })) {
            Button("OK") { store.message = nil }
        } message: { Text(store.message ?? "") }
    }
}
struct SongRow: View {
    @EnvironmentObject var store: Store
    var track: Track
    var play: (() -> Void)? = nil
    @State private var picker = false
    var body: some View {
        HStack {
            Button { if let play { play() } else { store.playCollection([track]) } } label: {
                HStack { Cover(url: track.artwork); VStack(alignment: .leading) {
                    Text(track.title).lineLimit(1); Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }.frame(maxWidth: .infinity, alignment: .leading) }.foregroundStyle(.primary)
            }.buttonStyle(.plain)
            Menu {
                Button("Play next") { store.enqueue(track, next: true) }
                Button("Play later") { store.enqueue(track) }
                Button(store.library.likes.contains(where: { $0.id == track.id }) ? "Unlike" : "Like") { store.like(track) }
                Button("Add to playlist") { picker = true }
            } label: { Image(systemName: "ellipsis").frame(width: 36, height: 44) }.accessibilityLabel("Options for \(track.title)")
        }.sheet(isPresented: $picker) { PlaylistPicker(tracks: [track]) }
    }
}
struct SearchView: View {
    @State private var query = ""
    @State private var albumsMode = false
    @State private var songs: [Track] = []
    @State private var albums: [Album] = []
    @State private var busy = false
    @State private var error: String?
    @State private var request: Task<Void, Never>?
    @State private var generation = 0
    var body: some View {
        List {
            Picker("Search type", selection: $albumsMode) { Text("Songs").tag(false); Text("Albums").tag(true) }.pickerStyle(.segmented)
            if busy { ProgressView("Searching…") }
            if let error { Text(error).foregroundStyle(.secondary) }
            if query.isEmpty { Text("Find a song or album. Online search requires your companion service in Settings.").foregroundStyle(.secondary) }
            if albumsMode {
                ForEach(albums) { album in NavigationLink { AlbumView(album: album) } label: {
                    HStack { Cover(url: album.artwork); VStack(alignment: .leading) { Text(album.title); Text(album.artist).font(.caption).foregroundStyle(.secondary) } }
                } }
            } else { ForEach(songs) { SongRow(track: $0) } }
        }.navigationTitle("Search").searchable(text: $query, prompt: "Song, album or artist")
            .onSubmit(of: .search) { search() }.onChange(of: albumsMode) { _ in if !query.isEmpty { search() } }
            .toolbar { Button("Search") { search() }.disabled(query.trimmingCharacters(in: .whitespaces).isEmpty) }
            .onDisappear { request?.cancel(); busy = false }
    }
    private func search() {
        request?.cancel(); generation += 1; let token = generation; let mode = albumsMode
        busy = true; error = nil; songs = []; albums = []
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        request = Task { @MainActor in
            do {
                let args = [URLQueryItem(name: "q", value: q), URLQueryItem(name: "type", value: mode ? "albums" : "songs")]
                if mode { let result: [Album] = try await Bridge.request("search", query: args); if !Task.isCancelled && token == generation { albums = result } }
                else { let result: [Track] = try await Bridge.request("search", query: args); if !Task.isCancelled && token == generation { songs = result } }
            } catch { if !Task.isCancelled && token == generation { self.error = error.localizedDescription } }
            if token == generation { busy = false }
        }
    }
}
struct QueueView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        List {
            Toggle("Autoplay similar songs", isOn: $store.autoplay)
            if store.blending { Text("The songs already mixing stay in place.").font(.caption) }
            HStack { Button("Shuffle upcoming") { store.shuffleUpcoming() }; Spacer(); Button("Clear upcoming") { store.clearUpcoming() } }.font(.caption)
            if store.library.queue.isEmpty { Text("Search or import audio to start listening.") }
            ForEach(Array(store.library.queue.enumerated()), id: \.offset) { index, track in
                VStack(alignment: .leading) {
                    if index == store.library.index { Text("NOW PLAYING").font(.caption.bold()).foregroundStyle(.pink) }
                    SongRow(track: track, play: { store.play(index) })
                    let first = store.library.index + (store.blending ? 2 : 1)
                    if index >= first {
                        HStack {
                            Button("Next") { store.move(index, first) }.disabled(index == first)
                            Button("↑") { store.move(index, index - 1) }.disabled(index == first).accessibilityLabel("Move up")
                            Button("↓") { store.move(index, index + 1) }.disabled(index == store.library.queue.count - 1).accessibilityLabel("Move down")
                            Spacer(); Button("Remove", role: .destructive) { store.remove(index) }.disabled(store.blending)
                        }.font(.caption).buttonStyle(.borderless)
                    }
                }
            }
            if !store.library.queue.isEmpty { Button("Stop & clear all", role: .destructive) { store.stopAndClear() } }
        }.navigationTitle("Queue")
    }
}
struct LibraryView: View {
    @EnvironmentObject var store: Store
    @State private var create = false
    @State private var name = ""
    var body: some View {
        List {
            NavigationLink { CollectionView(title: "Liked songs", tracks: store.library.likes) } label: { Label("Liked songs · \(store.library.likes.count)", systemImage: "heart.fill") }
            Section("Playlists") {
                Button("New playlist", systemImage: "plus") { name = ""; create = true }
                ForEach(store.library.playlists) { list in NavigationLink { PlaylistView(id: list.id) } label: {
                    HStack { Cover(url: list.tracks.first?.artwork ?? ""); VStack(alignment: .leading) { Text(list.name); Text("\(list.tracks.count) songs").font(.caption).foregroundStyle(.secondary) } }
                } }.onDelete { indices in store.library.playlists.remove(atOffsets: indices); store.save() }
            }
            Section("Saved albums") {
                ForEach(store.library.albums) { album in NavigationLink { AlbumView(album: album) } label: {
                    HStack { Cover(url: album.artwork); Text(album.title) }
                } }.onDelete { indices in store.library.albums.remove(atOffsets: indices); store.save() }
            }
            Text("Saved on this iPhone. Online songs are not downloaded.").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle("Library").toolbar { EditButton() }
            .alert("New playlist", isPresented: $create) { TextField("Playlist name", text: $name); Button("Create") { store.createPlaylist(name) }; Button("Cancel", role: .cancel) {} }
    }
}
struct CollectionView: View {
    @EnvironmentObject var store: Store
    var title: String
    var tracks: [Track]
    var body: some View { List {
        CollectionActions(tracks: tracks)
        ForEach(Array(tracks.enumerated()), id: \.offset) { index, track in SongRow(track: track, play: { store.playCollection(Array(tracks[index...])) }) }
    }.navigationTitle(title) }
}
struct CollectionActions: View {
    @EnvironmentObject var store: Store
    var tracks: [Track]
    var body: some View { HStack {
        Button("Play all") { store.playCollection(tracks) }; Spacer()
        Button("Shuffle") { store.playCollection(tracks, shuffle: true) }; Spacer()
        Button("Queue") { tracks.forEach { store.enqueue($0) } }
    }.buttonStyle(.borderless).disabled(tracks.isEmpty) }
}
struct PlaylistView: View {
    @EnvironmentObject var store: Store
    var id: UUID
    @State private var renaming = false
    @State private var name = ""
    var index: Int? { store.library.playlists.firstIndex { $0.id == id } }
    var body: some View {
        Group {
            if let i = index {
                List {
                    CollectionActions(tracks: store.library.playlists[i].tracks)
                    ForEach(store.library.playlists[i].tracks) { track in
                        SongRow(track: track, play: {
                            let tracks = store.library.playlists[i].tracks
                            if let start = tracks.firstIndex(where: { $0.id == track.id }) { store.playCollection(Array(tracks[start...])) }
                        })
                    }
                        .onDelete { offsets in store.library.playlists[i].tracks.remove(atOffsets: offsets); store.save() }
                        .onMove { source, target in store.library.playlists[i].tracks.move(fromOffsets: source, toOffset: target); store.save() }
                }.navigationTitle(store.library.playlists[i].name).toolbar {
                    EditButton(); Button("Rename") { name = store.library.playlists[i].name; renaming = true }
                }
            } else { Text("Playlist removed") }
        }.alert("Rename playlist", isPresented: $renaming) {
            TextField("Name", text: $name)
            Button("Save") { if let i = index, !name.trimmingCharacters(in: .whitespaces).isEmpty { store.library.playlists[i].name = String(name.prefix(80)); store.save() } }
            Button("Cancel", role: .cancel) {}
        }
    }
}
struct PlaylistPicker: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    var tracks: [Track]
    @State private var name = ""
    var body: some View { NavigationStack { List {
        Section("New playlist") { TextField("Name", text: $name); Button("Create and add") { store.createPlaylist(name, tracks: tracks); dismiss() }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty) }
        ForEach(store.library.playlists) { list in Button(list.name) { store.add(tracks, to: list.id); dismiss() } }
    }.navigationTitle("Add to playlist").toolbar { Button("Done") { dismiss() } } } }
}
struct AlbumView: View {
    @EnvironmentObject var store: Store
    var album: Album
    @State private var tracks: [Track] = []
    @State private var error: String?
    @State private var busy = true
    @State private var picker = false
    @State private var retry = 0
    var body: some View { List {
        VStack(spacing: 12) { Cover(url: album.artwork, size: 220); Text(album.title).font(.title.bold()); Text(album.artist).foregroundStyle(.secondary)
            Button(store.library.albums.contains(where: { $0.id == album.id }) ? "Unsave album" : "Save album") { store.saveAlbum(album) }
        }.frame(maxWidth: .infinity).listRowBackground(Color.clear)
        if busy { ProgressView("Loading album…") }
        if let error { Text(error); Button("Retry") { retry += 1 } }
        if !tracks.isEmpty { CollectionActions(tracks: tracks); Button("Add album to playlist") { picker = true } }
        ForEach(Array(tracks.enumerated()), id: \.offset) { index, track in SongRow(track: track, play: { store.playCollection(Array(tracks[index...])) }) }
    }.navigationTitle("Album").task(id: retry) {
        busy = true; error = nil
        do { tracks = try await Bridge.request("album", query: [URLQueryItem(name: "id", value: album.id)]) }
        catch { self.error = error.localizedDescription }; busy = false
    }.sheet(isPresented: $picker) { PlaylistPicker(tracks: tracks) } }
}
struct PlayerView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var picker = false
    @State private var lyrics = false
    @State private var queue = false
    @AppStorage("playerDesign") private var design = 0
    @AppStorage("glassAppearance") private var glass = false
    var body: some View { NavigationStack { ScrollView { if let track = store.current {
        VStack(spacing: 24) {
            ZStack {
                if design == 1 {
                    Circle().stroke(.secondary.opacity(0.2), lineWidth: 3)
                    Circle().trim(from: 0, to: store.duration > 0 ? min(1, store.position / store.duration) : 0)
                        .stroke(.mint, style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90))
                    DirectionalCover(track: track, index: store.library.index, size: 215).clipShape(Circle())
                } else { DirectionalCover(track: track, index: store.library.index, size: 270).shadow(radius: 18) }
            }.frame(width: 280, height: 280)
                .gesture(DragGesture(minimumDistance: 30).onEnded { value in
                    let x = value.translation.width, y = value.translation.height
                    if abs(x) > abs(y) * 1.25 && abs(x) > 70 { x < 0 ? store.next() : store.previous() }
                    else if abs(y) > abs(x) * 1.25 && abs(y) > 70 { if y < 0 { queue = true } else { dismiss() } }
                })
            VStack { Text(track.title).font(.title2.bold()); Text(track.artist).foregroundStyle(.secondary) }
            HStack {
                Button(store.library.likes.contains(where: { $0.id == track.id }) ? "♥ Liked" : "♡ Like") { store.like(track) }
                Button("Playlist") { picker = true }
                Button("Lyrics") { lyrics = true }
            }
            PlayerTransport().padding(12).modifier(PlayerGlass(enabled: glass))
            Button("Up next") { queue = true }
            Text(store.blending ? "Blending songs" : "iPhone preview · crossfade").font(.caption).foregroundStyle(.secondary)
        }.padding(28)
    } }.background { AlbumBackdrop(track: store.current) }.preferredColorScheme(.dark)
        .toolbar { Button("Done") { dismiss() } }
        .sheet(isPresented: $picker) { if let track = store.current { PlaylistPicker(tracks: [track]) } }
        .sheet(isPresented: $lyrics) { LyricsView() }
        .sheet(isPresented: $queue) { NavigationStack { QueueView().toolbar { Button("Done") { queue = false } } } }
    } }
}
struct PlayerTransport: View {
    @EnvironmentObject var store: Store
    @State private var scrub: Double?
    private func time(_ seconds: Double) -> String { "\(Int(max(0, seconds)) / 60):\(String(format: "%02d", Int(max(0, seconds)) % 60))" }
    var body: some View {
        VStack(spacing: 8) {
            Slider(value: Binding(get: { scrub ?? min(store.position, max(1, store.duration)) }, set: { scrub = $0 }),
                in: 0...max(1, store.duration), onEditingChanged: { editing in
                    if !editing { if let scrub { store.seek(scrub) }; scrub = nil }
                }).disabled(store.duration <= 0).accessibilityLabel("Song position")
            HStack { Text(time(scrub ?? store.position)); Spacer(); Text(time(store.duration)) }.font(.caption.monospacedDigit())
            HStack {
                Spacer(); Button { store.previous() } label: { Image(systemName: "backward.end.fill").frame(width: 48, height: 48) }.accessibilityLabel("Previous")
                Spacer(); Button { store.playing ? store.pause() : store.resume() } label: { Image(systemName: store.playing ? "pause.fill" : "play.fill").frame(width: 48, height: 48) }.accessibilityLabel(store.playing ? "Pause" : "Play")
                Spacer(); Button { store.next() } label: { Image(systemName: "forward.end.fill").frame(width: 48, height: 48) }.accessibilityLabel("Next"); Spacer()
            }.font(.title)
        }.onChange(of: store.current?.id) { _, _ in scrub = nil }
    }
}
struct SettingsView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var url = Bridge.base
    @State private var token = Secrets.token
    @State private var status = ""
    @AppStorage("appearance") private var appearance = 0
    @AppStorage("playerDesign") private var design = 0
    @AppStorage("glassAppearance") private var glass = false
    var body: some View { NavigationStack { Form {
        Section("Appearance") {
            Picker("Theme", selection: $appearance) { Text("System").tag(0); Text("Light").tag(1); Text("Dark").tag(2) }
            Picker("Player", selection: $design) { Text("Cover").tag(0); Text("Orbit").tag(1) }
            Toggle("Glass controls", isOn: $glass)
        }
        Section("Online music companion") {
            TextField("http://your-mac.local:8787", text: $url).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
            SecureField("Private bridge token", text: $token)
            Button("Save & test connection") {
                UserDefaults.standard.set(url.trimmingCharacters(in: CharacterSet(charactersIn: "/ ").union(.newlines)), forKey: "bridgeURL"); Secrets.token = token
                Task { @MainActor in do { let _: [String: String] = try await Bridge.request("health"); status = "Connected" } catch { status = error.localizedDescription } }
            }
            Text(status).font(.caption)
            Text("The companion Mac or PC must stay awake for online music. Local imports work independently. Use HTTPS outside your trusted home network.").font(.caption)
        }
        Section("Playback") {
            Toggle("Autoplay", isOn: $store.autoplay)
            Text("Crossfade: \(Int(store.fade)) seconds"); Slider(value: $store.fade, in: 0...12, step: 1)
            Text("This iPhone preview does not yet include beat synchronization or music-driven vibrations.").font(.caption)
        }
    }.navigationTitle("Settings").toolbar { Button("Done") { dismiss() } } }
}
