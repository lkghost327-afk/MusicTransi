import SwiftUI
import AVFoundation
import MediaPlayer

@MainActor final class Store: NSObject, ObservableObject {
    @Published var library = Library()
    @Published var playing = false
    @Published var position = 0.0
    @Published var duration = 0.0
    @Published var loading = false
    @Published var message: String?
    @Published var blending = false
    @Published var autoplay = true { didSet { UserDefaults.standard.set(autoplay, forKey: "autoplay") } }
    @Published var fade = 6.0 { didSet { UserDefaults.standard.set(fade, forKey: "fade") } }
    nonisolated static var documents: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }
    var current: Track? { library.queue.indices.contains(library.index) ? library.queue[library.index] : nil }
    private var active = AVPlayer()
    private var incoming = AVPlayer()
    private var timer: Timer?
    private var generation = 0
    private var prepared = -1
    private var preparing = false
    private var radioSeed: String?
    private var blendStart = 0.0
    private var loadTask: Task<Void, Never>?
    private var prepareTask: Task<Void, Never>?
    private var radioTask: Task<Void, Never>?
    private var resumeAfterInterruption = false
    private var waitingForRadio = false
    private var recentListening: [Track] = []
    private var heardID: String?
    private var heardSeconds = 0.0
    private var historyTick = ProcessInfo.processInfo.systemUptime
    private var historyRecorded = false

    override init() {
        super.init()
        if let data = UserDefaults.standard.data(forKey: "recentListening"),
           let saved = try? JSONDecoder().decode([Track].self, from: data) { recentListening = Array(saved.prefix(30)) }
        let file = Self.documents.appendingPathComponent("library.json")
        if FileManager.default.fileExists(atPath: file.path) {
            do { library = try JSONDecoder().decode(Library.self, from: Data(contentsOf: file)) }
            catch { message = "Couldn't read your saved library. The original file has been kept." }
        }
        if UserDefaults.standard.object(forKey: "autoplay") != nil { autoplay = UserDefaults.standard.bool(forKey: "autoplay") }
        if UserDefaults.standard.object(forKey: "fade") != nil { fade = UserDefaults.standard.double(forKey: "fade") }
        do { try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default) }
        catch { message = error.localizedDescription }
        NotificationCenter.default.addObserver(self, selector: #selector(interrupted(_:)), name: AVAudioSession.interruptionNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(routeChanged(_:)), name: AVAudioSession.routeChangeNotification, object: nil)
        let remote = MPRemoteCommandCenter.shared()
        remote.playCommand.addTarget { [weak self] _ in Task { @MainActor in self?.resume() }; return .success }
        remote.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.pause() }; return .success }
        remote.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.next() }; return .success }
        remote.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.previous() }; return .success }
        remote.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(e.positionTime) }; return .success
        }
        timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in Task { @MainActor in self?.tick() } }
    }
    func save() {
        do { try JSONEncoder().encode(library).write(to: Self.documents.appendingPathComponent("library.json"), options: .atomic) }
        catch { message = "Couldn't save your library: \(error.localizedDescription)" }
    }
    func like(_ track: Track) {
        if library.likes.contains(where: { $0.id == track.id }) { library.likes.removeAll { $0.id == track.id } }
        else { library.likes.insert(track, at: 0) }; save()
    }
    func createPlaylist(_ name: String, tracks: [Track] = []) {
        let name = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80))
        guard !name.isEmpty else { return }
        var seen = Set<String>()
        library.playlists.append(Playlist(name: name, tracks: tracks.filter { seen.insert($0.id).inserted })); save()
    }
    func add(_ tracks: [Track], to id: UUID) {
        guard let i = library.playlists.firstIndex(where: { $0.id == id }) else { return }
        var ids = Set(library.playlists[i].tracks.map(\.id))
        library.playlists[i].tracks += tracks.filter { ids.insert($0.id).inserted }; save()
    }
    func saveAlbum(_ album: Album) {
        if library.albums.contains(where: { $0.id == album.id }) { library.albums.removeAll { $0.id == album.id } }
        else { library.albums.append(album) }; save()
    }
    func importFiles(_ urls: [URL]) {
        for url in urls {
            let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let name = UUID().uuidString + "." + url.pathExtension
                try FileManager.default.copyItem(at: url, to: Self.documents.appendingPathComponent(name))
                enqueue(Track(id: name, title: url.deletingPathExtension().lastPathComponent, artist: "On this iPhone", local: true))
            } catch { message = error.localizedDescription }
        }
    }
    func playCollection(_ tracks: [Track], shuffle: Bool = false) {
        guard !tracks.isEmpty else { return }; cancelIncoming()
        library.queue = shuffle ? tracks.shuffled() : tracks; play(0)
    }
    func enqueue(_ track: Track, next: Bool = false) {
        var track = track; track.automatic = false
        let first = max(0, library.index + (blending ? 2 : 1))
        let index = next ? min(first, library.queue.count) : (library.queue.indices.first { $0 >= first && library.queue[$0].automatic } ?? library.queue.count)
        if !blending { cancelIncoming() }
        library.queue.insert(track, at: index); save()
        if current == nil { play(0) } else { prepareNext() }
    }
    func move(_ from: Int, _ to: Int) {
        let first = library.index + (blending ? 2 : 1)
        guard from >= first, to >= first, library.queue.indices.contains(from), library.queue.indices.contains(to) else { return }
        if !blending { cancelIncoming() }
        var track = library.queue.remove(at: from); track.automatic = false
        library.queue.insert(track, at: to); save(); prepareNext()
    }
    func remove(_ index: Int) {
        guard !blending, index > library.index, library.queue.indices.contains(index) else { return }
        cancelIncoming(); library.queue.remove(at: index); save(); prepareNext()
    }
    func clearUpcoming() {
        autoplay = false; radioTask?.cancel()
        let keep = max(0, library.index + (blending ? 2 : 1))
        if !blending { cancelIncoming() }
        library.queue = Array(library.queue.prefix(keep)); save()
    }
    func shuffleUpcoming() {
        let first = max(0, library.index + (blending ? 2 : 1))
        if !blending { cancelIncoming() }
        library.queue = Array(library.queue.prefix(first)) + library.queue.dropFirst(first).shuffled().map { t in var t = t; t.automatic = false; return t }
        save(); prepareNext()
    }
    func stopAndClear() {
        generation += 1; loadTask?.cancel(); radioTask?.cancel(); pause(); cancelIncoming()
        active.replaceCurrentItem(with: nil); library.queue = []; library.index = -1; position = 0; duration = 0; loading = false; save(); nowPlaying()
    }
    func play(_ index: Int) {
        guard library.queue.indices.contains(index) else { return }
        waitingForRadio = false
        generation += 1; let token = generation
        loadTask?.cancel(); radioTask?.cancel(); radioSeed = nil
        pause(); cancelIncoming(); active.replaceCurrentItem(with: nil)
        library.index = index; position = 0; duration = 0; loading = true; message = nil; save()
        let track = library.queue[index]
        loadTask = Task {
            do {
                let url = try await Bridge.audio(track)
                guard !Task.isCancelled, token == generation else { return }
                active.replaceCurrentItem(with: AVPlayerItem(url: url)); loading = false; resume(); prepareNext(); radio()
            } catch { if token == generation { loading = false; message = error.localizedDescription } }
        }
    }
    func resume() {
        guard current != nil else { return }
        if active.currentItem == nil { if !loading { play(library.index) }; return }
        do { try AVAudioSession.sharedInstance().setActive(true); playing = true; active.play(); if blending { incoming.play() }; nowPlaying() }
        catch { message = error.localizedDescription }
    }
    func pause() { waitingForRadio = false; playing = false; active.pause(); incoming.pause(); nowPlaying() }
    func next() {
        if library.index + 1 < library.queue.count { play(library.index + 1) }
        else { pause(); waitingForRadio = autoplay && current?.local == false; radio() }
    }
    func previous() { if position > 3 { seek(0) } else if library.index > 0 { play(library.index - 1) } }
    func seek(_ seconds: Double) { cancelIncoming(); active.seek(to: CMTime(seconds: max(0, min(seconds, duration)), preferredTimescale: 600)); prepareNext() }
    private func cancelIncoming() {
        prepareTask?.cancel(); incoming.pause(); incoming.replaceCurrentItem(with: nil)
        blending = false; preparing = false; prepared = -1; active.volume = 1
    }
    private func prepareNext() {
        let index = library.index + 1
        guard !blending, !preparing, prepared != index, library.queue.indices.contains(index) else { return }
        preparing = true; let token = generation; let track = library.queue[index]
        prepareTask = Task {
            do {
                let url = try await Bridge.audio(track)
                guard !Task.isCancelled, token == generation, library.queue.indices.contains(index), library.queue[index].id == track.id else { return }
                incoming.replaceCurrentItem(with: AVPlayerItem(url: url)); incoming.volume = 0; prepared = index; preparing = false
            } catch { if !Task.isCancelled { preparing = false } }
        }
    }
    private func radio() {
        guard autoplay, let seed = current, !seed.local, radioSeed != seed.id, library.queue.count - library.index < 4 else { return }
        radioSeed = seed.id; let token = generation
        struct Request: Encodable { var seed: Track; var excluded: [Track] }
        radioTask = Task {
            do {
                let exclusions = recentListening + Array(library.queue.suffix(470))
                let tracks: [Track] = try await Bridge.request("radio", body: JSONEncoder().encode(Request(seed: seed, excluded: exclusions)))
                guard !Task.isCancelled, token == generation, autoplay else { return }
                var ids = Set((recentListening + library.queue).map(\.id))
                library.queue += tracks.filter { ids.insert($0.id).inserted }.map { t in var t = t; t.automatic = true; return t }
                save()
                if waitingForRadio && library.queue.indices.contains(library.index + 1) { play(library.index + 1) }
                else { waitingForRadio = false; prepareNext() }
            } catch { if !Task.isCancelled { waitingForRadio = false; message = "Autoplay unavailable. You can still add songs manually." } }
        }
    }
    private func tick() {
        let now = ProcessInfo.processInfo.systemUptime
        let elapsed = min(1, max(0, now - historyTick)); historyTick = now
        if heardID != current?.id { heardID = current?.id; heardSeconds = 0; historyRecorded = false }
        if playing, active.timeControlStatus == .playing, let track = current, !historyRecorded {
            heardSeconds += elapsed
            if heardSeconds >= 30 {
                recentListening.removeAll { $0.id == track.id }; recentListening.insert(track, at: 0)
                recentListening = Array(recentListening.prefix(30)); historyRecorded = true
                if let data = try? JSONEncoder().encode(recentListening) { UserDefaults.standard.set(data, forKey: "recentListening") }
            }
        }
        guard let item = active.currentItem else { return }
        if item.status == .failed { message = item.error?.localizedDescription ?? "Playback failed"; pause(); return }
        let p = active.currentTime().seconds, d = item.duration.seconds
        position = p.isFinite ? p : 0; duration = d.isFinite ? d : 0
        guard playing, duration > 0 else { return }
        let length = min(max(0, fade), duration / 4)
        if !blending, length > 0, duration - position <= length, prepared == library.index + 1, incoming.currentItem?.status == .readyToPlay {
            blending = true; blendStart = position; incoming.play()
        }
        if blending {
            let progress = min(1, max(0, (position - blendStart) / max(0.1, length)))
            active.volume = Float(cos(progress * .pi / 2)); incoming.volume = Float(sin(progress * .pi / 2))
            if progress >= 1 || position >= duration - 0.05 {
                active.pause(); swap(&active, &incoming); incoming.replaceCurrentItem(with: nil)
                active.volume = 1; library.index += 1; prepared = -1; blending = false; radioSeed = nil
                save(); prepareNext(); radio()
            }
        } else if position >= duration - 0.05 { next() }
        nowPlaying()
    }
    private func nowPlaying() {
        guard let current else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [MPMediaItemPropertyTitle: current.title, MPMediaItemPropertyArtist: current.artist,
            MPMediaItemPropertyPlaybackDuration: duration, MPNowPlayingInfoPropertyElapsedPlaybackTime: position, MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0]
    }
    @objc private func interrupted(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt else { return }
        if raw == AVAudioSession.InterruptionType.began.rawValue { resumeAfterInterruption = playing; pause() }
        else if resumeAfterInterruption, let options = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt,
                AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) { resume() }
    }
    @objc private func routeChanged(_ note: Notification) {
        if (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { pause() }
    }
}
