import Foundation
import Security

struct Track: Codable, Identifiable, Equatable {
    var id: String
    var title: String
    var artist: String
    var durationSeconds: Int = 0
    var local: Bool = false
    var artwork: String = ""
    var automatic: Bool = false
    var music: Bool = true
}
struct Album: Codable, Identifiable, Equatable {
    var id: String
    var title: String
    var artist: String
    var artwork: String = ""
    var trackCount: Int = 0
}
struct Playlist: Codable, Identifiable {
    var id = UUID()
    var name: String
    var tracks: [Track] = []
}
struct Library: Codable {
    var likes: [Track] = []
    var playlists: [Playlist] = []
    var albums: [Album] = []
    var queue: [Track] = []
    var index: Int = -1
}
enum Secrets {
    static var token: String {
        get {
            let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: "MusicTransiBridge", kSecAttrAccount as String: "token",
                kSecReturnData as String: true]
            var result: CFTypeRef?
            guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
                  let data = result as? Data else { return "" }
            return String(data: data, encoding: .utf8) ?? ""
        }
        set {
            let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: "MusicTransiBridge", kSecAttrAccount as String: "token"]
            SecItemDelete(query as CFDictionary)
            var item = query
            item[kSecValueData as String] = Data(newValue.utf8)
            item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(item as CFDictionary, nil)
        }
    }
}
struct Bridge {
    static var base: String { UserDefaults.standard.string(forKey: "bridgeURL") ?? "" }
    static func request<T: Decodable>(_ path: String, query: [URLQueryItem] = [], body: Data? = nil) async throws -> T {
        guard let root = URL(string: base), ["http", "https"].contains(root.scheme ?? ""), root.host != nil else {
            throw NSError(domain: "Bridge", code: 1, userInfo: [NSLocalizedDescriptionKey: "Set your companion service URL and token in Settings first."])
        }
        var components = URLComponents(url: root.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.isEmpty ? nil : query
        var request = URLRequest(url: components.url!, timeoutInterval: 120)
        request.setValue("Bearer \(Secrets.token)", forHTTPHeaderField: "Authorization")
        if let body { request.httpMethod = "POST"; request.httpBody = body; request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw NSError(domain: "Bridge", code: 2, userInfo: [NSLocalizedDescriptionKey: "The companion service could not complete this request. Check its URL, token and connection."])
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
    static func audio(_ track: Track) async throws -> URL {
        if track.local { return Store.documents.appendingPathComponent(track.id) }
        struct Prepared: Decodable { let path: String; let artwork: String }
        let response: Prepared = try await request("prepare", body: JSONEncoder().encode(track))
        guard response.path.hasPrefix("/audio?ticket="), let url = URL(string: base + response.path) else { throw URLError(.badURL) }
        return url
    }
}
