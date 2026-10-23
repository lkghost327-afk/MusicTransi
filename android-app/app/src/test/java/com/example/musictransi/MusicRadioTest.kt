package com.example.musictransi
import com.example.musictransi.source.MusicRadio
import org.junit.Assert.*
import org.junit.Test
class MusicRadioTest {
    @Test fun musicRadioRejectsVideosAndUnknownTypes() {
        fun item(id: String, type: String) = """{"playlistPanelVideoRenderer":{"videoId":"$id","title":{"runs":[{"text":"Song"}]},"shortBylineText":{"runs":[{"text":"Artist"}]},"lengthText":{"simpleText":"3:24"},"navigationEndpoint":{"watchEndpoint":{"watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}}}}}"""
        val json = """{"contents":[${item("abcdefghijk", "MUSIC_VIDEO_TYPE_ATV")},${item("lmnopqrstuv", "MUSIC_VIDEO_TYPE_OMV")},${item("12345678901", "MUSIC_VIDEO_TYPE_UGC")}]}"""
        val tracks = MusicRadio.parse(json)
        assertEquals(1, tracks.size)
        assertTrue(tracks.single().music)
        assertEquals(204, tracks.single().durationSeconds)
    }
}
