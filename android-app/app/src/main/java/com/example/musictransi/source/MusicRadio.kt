package com.example.musictransi.source

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** Music's radio endpoint, not YouTube's general related-video feed. */
object MusicRadio {
    fun fetch(seed: Track): List<Track> {
        val id = ArtworkPolicy.videoId(seed.id) ?: return emptyList()
        val body = JsonWriter.string().`object`().`object`("context").`object`("client")
            .value("clientName", "WEB_REMIX").value("clientVersion", YoutubeParsingHelper.getYoutubeMusicClientVersion())
            .value("hl", "en").end().end().value("videoId", id).value("playlistId", "RDAMVM$id")
            .value("isAudioOnly", true).value("enablePersistentPlaylistPanel", true).end().done()
        val request = okhttp3.Request.Builder().url("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
        YoutubeParsingHelper.getYoutubeMusicHeaders().forEach { (key, values) -> values.forEach { request.addHeader(key, it) } }
        return YouTubeSource.http.newCall(request.build()).execute().use {
            check(it.isSuccessful) { "Music radio returned ${it.code}" }
            parse(it.body!!.string())
        }
    }

    fun parse(json: String): List<Track> {
        val tracks = mutableListOf<Track>()
        fun text(obj: JsonObject): String = obj.getString("simpleText") ?: obj.getArray("runs", JsonArray())
            .filterIsInstance<JsonObject>().joinToString("") { it.getString("text", "") }
        fun visit(node: Any?) {
            when (node) {
                is JsonObject -> {
                    val r = node.getObject("playlistPanelVideoRenderer")
                    if (node.containsKey("playlistPanelVideoRenderer")) {
                        val endpoint = r.getObject("navigationEndpoint")?.getObject("watchEndpoint")
                        val type = endpoint?.getObject("watchEndpointMusicSupportedConfigs")
                            ?.getObject("watchEndpointMusicConfig")?.getString("musicVideoType")
                        // Fail closed: accept album audio; official videos and user uploads are not radio candidates.
                        if (type == "MUSIC_VIDEO_TYPE_ATV") {
                            val id = r.getString("videoId", "")
                            val length = text(r.getObject("lengthText", JsonObject())).split(":")
                                .fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0) }
                            val byline = r.getObject("shortBylineText", JsonObject()).getArray("runs", JsonArray())
                                .filterIsInstance<JsonObject>().firstOrNull()?.getString("text").orEmpty()
                            val thumbnail = r.getObject("thumbnail", JsonObject()).getArray("thumbnails", JsonArray())
                                .filterIsInstance<JsonObject>().maxByOrNull { it.getInt("width", 0) }?.getString("url").orEmpty()
                            if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) tracks += Track("https://www.youtube.com/watch?v=$id",
                                text(r.getObject("title", JsonObject())), byline, length, artwork = thumbnail, music = true)
                        }
                    } else node.values.forEach(::visit)
                }
                is JsonArray -> node.forEach(::visit)
            }
        }
        visit(JsonParser.`object`().from(json))
        return tracks.distinctBy { it.id }
    }
}
