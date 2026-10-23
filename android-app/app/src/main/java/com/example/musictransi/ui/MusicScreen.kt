package com.example.musictransi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musictransi.playback.*
import com.example.musictransi.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Rose: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun MusicScreen(s: MixState, player: MixService, onLocal: () -> Unit, onStart: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSearchHistory by remember { mutableStateOf(false) }
    val searchInteraction = remember { MutableInteractionSource() }
    val searchPressed by searchInteraction.collectIsPressedAsState()
    LaunchedEffect(searchPressed) { if (searchPressed) showSearchHistory = true }
    val searchHistory by player.searchHistory.tracks.collectAsState()
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<Album>>(emptyList()) }
    var albumsMode by rememberSaveable { mutableStateOf(false) }
    var album by remember { mutableStateOf<Album?>(null) }
    var songMenu by remember { mutableStateOf<Track?>(null) }
    var addingTracks by remember { mutableStateOf<List<Track>?>(null) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    val library by player.library.state.collectAsState()
    val listeningHistory by player.listeningHistory.tracks.collectAsState()
    val personalMixes = remember { PersonalMixes() }
    var mixRetry by remember { mutableIntStateOf(0) }
    var consumedMixRetry by remember { mutableIntStateOf(0) }
    var selectedMix by remember { mutableStateOf<SuggestedMix?>(null) }
    var namingMix by remember { mutableStateOf<SuggestedMix?>(null) }
    var savedMix by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val suggestions = remember { HomeSuggestions() }
    var suggestionsRetry by remember { mutableIntStateOf(0) }
    var consumedSuggestionsRetry by remember { mutableIntStateOf(0) }
    val suggestionSeed = s.current?.takeUnless { it.local }
        ?: s.queue.take((s.index + 1).coerceAtLeast(0)).lastOrNull { !it.local }
        ?: library.likes.firstOrNull { !it.local }
    val homeVisible = tab == 0 && !expanded && album == null
    LaunchedEffect(homeVisible, listeningHistory, library.likes, library.loading, mixRetry) {
        if (homeVisible && !library.loading) {
            personalMixes.refresh(listeningHistory, library.likes, mixRetry != consumedMixRetry)
            consumedMixRetry = mixRetry
        }
    }
    LaunchedEffect(suggestionSeed?.id, homeVisible, suggestionsRetry) {
        if (homeVisible && suggestionSeed != null) {
            suggestions.refresh(suggestionSeed, s.queue.take((s.index + 1).coerceAtLeast(0)), suggestionsRetry != consumedSuggestionsRetry)
            consumedSuggestionsRetry = suggestionsRetry
        }
    }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var searchGeneration by remember { mutableIntStateOf(0) }
    fun libraryEdit(action: suspend () -> Unit) { scope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { libraryError = "Couldn't update your library. Please try again." }
    } }
    fun playList(tracks: List<Track>, index: Int, shuffle: Boolean) { onStart(); player.playCollection(tracks, index, shuffle); expanded = true }
    fun queueList(tracks: List<Track>) { onStart(); player.enqueueCollection(tracks) }
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !expanded && !dark
            isAppearanceLightNavigationBars = !expanded && !dark
        }
    }
    fun search(text: String = query, albums: Boolean = albumsMode) {
        if (text.isBlank()) return
        showSearchHistory = false; query = text; tab = 1; album = null; albumsMode = albums; keyboard?.hide(); searchJob?.cancel()
        val token = ++searchGeneration
        searchJob = scope.launch {
            searching = true; error = null; searched = true
            try { if (albums) albumResults = YouTubeSource.searchAlbums(text.trim()) else results = YouTubeSource.search(text.trim()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { results = emptyList(); albumResults = emptyList(); error = "Couldn’t search. Check your connection and try again." }
            finally { if (token == searchGeneration) searching = false }
        }
    }
    fun toggle() { if (s.playing) player.pause() else { onStart(); player.play() } }
    BackHandler(expanded) { expanded = false }
    BackHandler(!expanded && album != null) { album = null }
    if (expanded && s.current != null) {
        val currentTrack = s.current!!
        FullPlayer(s, player, { expanded = false }, { settings = true },
            { expanded = false; album = null; tab = 2 }, ::toggle, onStart,
            library.likes.any { it.id == currentTrack.id }, { libraryEdit { player.library.toggleLike(currentTrack) } }, { addingTracks = listOf(currentTrack) })
    } else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(24.dp, 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MUSICTRANSI", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text(if (album != null) "Album" else listOf("Home", "Search", "Up next", "Library")[tab], color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { settings = true }) { Text("Mix", color = Rose) }
        }
        if (album != null) {
            val currentAlbum = album!!
            AlbumScreen(currentAlbum, library.albums.any { it.id == currentAlbum.id }, Modifier.weight(1f), { album = null },
                { libraryEdit { player.library.toggleAlbum(currentAlbum) } }, ::playList, ::queueList, { songMenu = it }, { addingTracks = it })
        } else if (tab == 3) LibraryScreen(library, player.library, Modifier.weight(1f), ::playList, ::queueList, { songMenu = it }, { album = it })
        else LazyColumn(Modifier.weight(1f).then(screenEntrance(tab)), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
            if (tab == 0) {
                item {
                    Section("Top Picks for You")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            EditorialCard("YOUR STATION", s.current?.title ?: "Find your next favourite",
                                s.current?.artist ?: "Start with a song. Let the music follow.", s.current,
                                listOf(Color(0xFFAD350C), Color(0xFFDF8C14))) {
                                if (s.current != null) expanded = true else tab = 1
                            }
                        }
                        if (library.likes.isNotEmpty()) item {
                            EditorialCard("MADE BY YOU", "On repeat", "Your liked songs, together", library.likes.first(),
                                listOf(Color(0xFF663D85), Color(0xFFAD4673))) { playList(library.likes, 0, true) }
                        }
                        suggestions.tracks.take(3).forEach { track -> item(key = track.id) {
                            EditorialCard("DISCOVER", track.title, track.artist, track,
                                listOf(Color(0xFF183F61), Color(0xFF376C77))) {
                                onStart(); player.enqueue(track, playNow = true); expanded = true
                            }
                        } }
                    }
                }
                item { Section("Recently Played") }
                item {
                    val recent = (listOfNotNull(s.current?.takeUnless { s.loading }) + listeningHistory).distinctBy { it.id }.take(10)
                    if (recent.isEmpty()) Text("Songs you listen to will appear here.", color = Muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(recent, key = { it.id }) { track ->
                            Column(Modifier.width(156.dp).clickable { onStart(); player.enqueue(track, playNow = true); expanded = true }) {
                                Artwork(track.artwork, Modifier.size(156.dp).clip(RoundedCornerShape(12.dp)), trackId = track.id)
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Section("Made for you")
                    Text("Built from songs you listen to and like", color = Muted, fontSize = 12.sp)
                    if (personalMixes.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    if (personalMixes.failed) Text("Couldn't finish refreshing mixes. Check your connection and retry.", color = Muted, fontSize = 12.sp)
                    if (personalMixes.incomplete && !personalMixes.loading) Text("Not enough distinct songs yet for every mix. Each playlist needs at least 10; refresh to try again.", color = Muted, fontSize = 12.sp)
                    if (!personalMixes.loading && personalMixes.mixes.isEmpty() && !personalMixes.incomplete) Text("Listen to a song for 30 seconds or like a favourite to start your mixes.", color = Muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) {
                        items(personalMixes.mixes, key = { it.title }) { mix ->
                            EditorialCard("${mix.tracks.size} SONGS", mix.title, mix.description, mix.tracks.firstOrNull(),
                                listOf(Color(0xFF514778), Color(0xFF9E4F66))) { selectedMix = mix }
                        }
                    }
                    TextButton(onClick = { mixRetry++ }, enabled = !personalMixes.loading) { Text("Refresh mixes") }
                }
                item {
                    Section("Suggested for you")
                    if (suggestionSeed == null) Text("Play or like a song to discover more music you'll enjoy.", color = Muted)
                    else {
                        Text("Because you listened to ${suggestionSeed.title}", color = Muted, fontSize = 12.sp)
                        if (suggestions.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        if (suggestions.error) Text("Couldn't load suggestions. Check your connection.", color = Muted)
                        else if (!suggestions.loading && suggestions.tracks.isEmpty()) Text("No fresh suggestions for this song yet.", color = Muted)
                        TextButton(onClick = { suggestionsRetry++ }, enabled = !suggestions.loading) { Text("Refresh suggestions") }
                    }
                }
                items(if (suggestionSeed == null) emptyList() else suggestions.tracks, key = { "suggested-${it.id}" }) { track ->
                    SongRow(track, false, { onStart(); player.enqueue(track, playNow = true); expanded = true },
                        { onStart(); player.enqueue(track) }, "+", { songMenu = track })
                }
                item { TextButton(onClick = onLocal, modifier = Modifier.padding(top = 14.dp)) { Text("＋ Add music from your phone", color = Rose) } }
            }
            if (tab == 1) {
                item {
                    OutlinedTextField(query, { query = it; showSearchHistory = it.isBlank() }, Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) showSearchHistory = true }, singleLine = true, interactionSource = searchInteraction,
                        placeholder = { Text(if (albumsMode) "Album or artist…" else "Artists, songs, a favourite…") }, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                        trailingIcon = { TextButton(onClick = { search() }, enabled = query.isNotBlank() && !searching) { Text("Go") } })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Songs", "Albums").forEachIndexed { index, label ->
                            FilterChip(selected = albumsMode == (index == 1), onClick = {
                                albumsMode = index == 1; results = emptyList(); albumResults = emptyList(); searched = false
                                if (query.isNotBlank()) search(query, albumsMode)
                            }, label = { Text(label) })
                        }
                    }
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    error?.let { Text(it, color = Rose, modifier = Modifier.padding(vertical = 16.dp)) }
                    if (!showSearchHistory) Section(if (searched) (if (albumsMode) "Albums" else "Songs") else "Start somewhere you love")
                    if (!searched && !showSearchHistory) BrowseCategories { search(it, false) }
                    if (!showSearchHistory && searched && (if (albumsMode) albumResults.isEmpty() else results.isEmpty()) && !searching && error == null) Text("No results found. Try another title.", color = Muted)
                }
                if (showSearchHistory) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent searches", Modifier.weight(1f), color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { libraryEdit { player.searchHistory.clear() } }, enabled = searchHistory.isNotEmpty()) { Text("Clear") }
                            TextButton(onClick = { showSearchHistory = false; keyboard?.hide() }) { Text("Done") }
                        }
                        if (searchHistory.isEmpty()) Text("Songs you play from search results will appear here.", color = Muted)
                    }
                    items(searchHistory, key = { "search-history-${it.id}" }) { track ->
                        SongRow(track, false, {
                            libraryEdit { player.searchHistory.record(track) }; keyboard?.hide(); showSearchHistory = false
                            onStart(); player.enqueue(track, playNow = true); expanded = true
                        }, { libraryEdit { player.searchHistory.remove(track.id) } }, "×", { songMenu = track })
                    }
                } else if (albumsMode) items(albumResults, key = { it.id }) { item -> AlbumRow(item) { album = item } }
                else items(results) { track -> SongRow(track, false,
                    { libraryEdit { player.searchHistory.record(track) }; keyboard?.hide(); onStart(); player.enqueue(track, playNow = true); expanded = true }, { onStart(); player.enqueue(track) }, "+", { songMenu = track }) }
            }
            if (tab == 2) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Autoplay  ∞", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            Text("Related songs keep the music going", color = Muted, fontSize = 12.sp)
                        }
                        Switch(s.autoplay, player::setAutoplay)
                    }
                    if (s.findingRelated) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)); Text("Finding your next songs…", color = Muted, fontSize = 12.sp) }
                    if (s.current?.local == true && s.autoplay) Text("Autoplay is available for online songs. Local files use your queue.", color = Muted, fontSize = 12.sp)
                    Row { TextButton(onClick = onLocal) { Text("Add files") }; TextButton(onClick = player::clearUpcoming) { Text("Clear upcoming") }
                        if (s.autoplay) TextButton(onClick = player::retryRadio) { Text("Retry radio") } }
                    Row { TextButton(onClick = player::shuffleUpcoming) { Text("Shuffle upcoming") }
                        TextButton(onClick = player::clearQueue) { Text("Stop & clear all") } }
                    if (s.blend > 0f) Text("The two songs already mixing stay in place.", color = Muted, fontSize = 12.sp)
                    if (s.queue.isEmpty()) Text("Choose a song in Search to start your station.", color = Muted)
                }
                itemsIndexed(s.queue) { index, track -> Column { SongRow(track, index == s.index,
                    { onStart(); player.playAt(index); expanded = true },
                    { if (index != s.index && s.blend == 0f) player.removeQueued(index) }, if (index == s.index) "♪" else "×", { songMenu = track })
                    val first = QueuePolicy.nextSlot(s.index, s.blend > 0f)
                    if (index >= first) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { player.moveQueued(index, first) }, enabled = index > first) { Text("Play next") }
                        TextButton(onClick = { player.moveQueued(index, index - 1) }, enabled = index > first,
                            modifier = Modifier.semantics { contentDescription = "Move ${track.title} up in queue" }) { Text("↑") }
                        TextButton(onClick = { player.moveQueued(index, index + 1) }, enabled = index < s.queue.lastIndex,
                            modifier = Modifier.semantics { contentDescription = "Move ${track.title} down in queue" }) { Text("↓") }
                    }
                } }
            }
            s.message?.let { message -> item { Text(message, color = Rose, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)) } }
        }
        s.current?.let { track ->
            SwipeArtwork(track.id, left = { onStart(); player.next() }, right = { onStart(); player.previous() },
                up = { expanded = true }, down = {}, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant).playerGlass(s.glassAppearance, dark).clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artwork, Modifier.size(45.dp).clip(RoundedCornerShape(7.dp)), trackId = track.id)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text(if (s.loading) "Loading…" else track.artist, color = Muted, maxLines = 1, fontSize = 11.sp)
                }
                Transport(if (s.playing) "Pause" else "Play", Ink, ::toggle)
                Transport("Next", Ink, { onStart(); player.next() })
            }
            }
        }
        MusicNavigation(tab) { tab = it; album = null }
    }

    selectedMix?.let { mix -> SuggestedMixDialog(mix, { selectedMix = null },
        { playList(mix.tracks, 0, false); selectedMix = null },
        { queueList(mix.tracks); selectedMix = null }, { namingMix = mix; selectedMix = null }) }
    namingMix?.let { mix -> PlaylistNameDialog(mix.title, { namingMix = null }, heading = "Save suggested playlist") { name ->
        namingMix = null
        libraryEdit { player.library.createPlaylist(name, mix.tracks); savedMix = name }
    } }
    savedMix?.let { name -> AlertDialog(onDismissRequest = { savedMix = null }, title = { Text("Playlist saved") },
        text = { Text("$name is now in your Library. You can edit or download it there.") },
        confirmButton = { TextButton(onClick = { savedMix = null; expanded = false; album = null; tab = 3 }) { Text("Open Library") } }) }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Make it flow") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Listening history", fontWeight = FontWeight.Bold)
            Text("Mixes use up to 80 recent songs, stored on this phone. Songs count after 30 seconds of playback.", fontSize = 12.sp)
            TextButton(onClick = { libraryEdit { player.listeningHistory.clear() } }) { Text("Clear listening history") }
            Text("Appearance", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, name ->
                    FilterChip(selected = s.themeMode == i, onClick = { player.setThemeMode(i) }, label = { Text(name) })
                }
            }
            Text("Player design", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Ambient", "Studio", "Vinyl", "Orbit", "Record").forEachIndexed { i, name ->
                    FilterChip(selected = s.playerStyle == i, onClick = { player.setPlayerStyle(i) }, label = { Text(name) })
                }
            }
            Setting("Liquid glass", s.glassAppearance, player::setGlassAppearance)
            Text("Translucent panels and luminous edges. Works with every player design.", fontSize = 12.sp)
            Text("Swipe artwork: left/right to change songs, up for queue, down to collapse. Swipe the mini-player up to expand.", fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            Text("Transition · ${s.crossfadeSeconds} seconds")
            Slider(s.crossfadeSeconds.toFloat(), { player.setCrossfade(it.toInt()) }, valueRange = 0f..16f, steps = 15)
            Setting("Bass handoff", s.bassBlend, player::setBassBlend)
            Text("Gradually trades low frequencies between songs where the phone supports equalization.", fontSize = 12.sp)
            Setting("Balance song levels", s.normalize, player::setNormalize)
            Text("Gently reduces loud tracks. Confident, compatible beats use tempo matching; other tracks use a short blend.", fontSize = 12.sp)
            Setting("Autoplay", s.autoplay, player::setAutoplay)
            Text("Music-radio album tracks only, with same-artist songs as fallback. Repeated versions and altered uploads are filtered.", fontSize = 12.sp)
            Setting("Feel the music", s.haptics, player::setHaptics, s.hapticsSupported)
            Text(s.hapticStatus, fontSize = 12.sp)
            if (s.hapticsSupported) {
                var timing by remember(s.hapticAdvanceMs) { mutableFloatStateOf(s.hapticAdvanceMs.toFloat()) }
                Text("Pulse timing: ${timing.toInt()} ms", fontSize = 12.sp)
                Slider(timing, { timing = it }, valueRange = -150f..150f, steps = 59,
                    onValueChangeFinished = { player.setHapticAdvance(timing.toInt()) })
                Text("Positive values move pulses earlier; negative values delay them. Adjust for your phone or headphones. Applies to audio pulse mode.", fontSize = 12.sp)
                Text("Pulse strength", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(s.hapticIntensity, player::setHapticIntensity, valueRange = .2f..1f)
                TextButton(onClick = player::testVibration) { Text("Test vibration") }
                Text("Audio pulse mode works on Android 8+. Timing varies with Bluetooth; Android may suppress background vibration.", fontSize = 12.sp)
            }
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { player.stopPlayback(); settings = false }) { Text("Stop playback") } })
    songMenu?.let { track -> AlertDialog(onDismissRequest = { songMenu = null }, title = { Text(track.title) }, text = {
        Column {
            TextButton(onClick = { libraryEdit { player.library.toggleLike(track) }; songMenu = null }) {
                Text(if (library.likes.any { it.id == track.id }) "♥ Remove from liked songs" else "♡ Like song") }
            TextButton(onClick = { addingTracks = listOf(track); songMenu = null }) { Text("Add to playlist") }
            TextButton(onClick = { onStart(); player.playNext(track); songMenu = null }) { Text("Play next") }
            TextButton(onClick = { queueList(listOf(track)); songMenu = null }) { Text("Play later · before Autoplay") }
        }
    }, confirmButton = { TextButton(onClick = { songMenu = null }) { Text("Done") } }) }
    addingTracks?.let { tracks -> PlaylistPicker(tracks, library, player.library) { addingTracks = null } }
    libraryError?.let { message -> AlertDialog(onDismissRequest = { libraryError = null }, title = { Text("Library") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { libraryError = null }) { Text("OK") } }) }
}

@Composable private fun Section(text: String) { Text(text, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) }
@Composable private fun Setting(title: String, checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, change, enabled = enabled) }
}
@Composable private fun SongRow(track: Track, current: Boolean, play: () -> Unit, action: () -> Unit, label: String, more: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.artwork, Modifier.size(54.dp).clip(RoundedCornerShape(9.dp)), trackId = track.id)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = if (current) Rose else Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.automatic) Text("AUTOPLAY", fontSize = 9.sp, letterSpacing = 1.sp, color = Rose)
        }
        TextButton(onClick = action, enabled = !current, modifier = Modifier.semantics { contentDescription = if (label == "+") "Add ${track.title} to queue" else "Remove ${track.title}" }) { Text(label, color = Rose, fontSize = 23.sp) }
        more?.let { IconButton(onClick = it, modifier = Modifier.semantics { contentDescription = "Song options for ${track.title}" }) { Text("•••", color = Rose) } }
    }
}

@Composable private fun FullPlayer(s: MixState, player: MixService, close: () -> Unit, settings: () -> Unit, queue: () -> Unit, toggle: () -> Unit, onStart: () -> Unit,
    liked: Boolean, like: () -> Unit, addPlaylist: () -> Unit) {
    val track = s.current!!
    var lyricsOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    if (queueOpen) PlayerQueueDialog(s, { queueOpen = false }, toggle, { onStart(); player.previous() },
        { onStart(); player.next() }, player::seek, { onStart(); player.playAt(it) }, player::setAutoplay,
        player::shuffleUpcoming, { queueOpen = false; queue() })
    if (lyricsOpen) LyricsDialog(track, s.positionMs, s.durationMs, s.playing, player::seek, toggle,
        { onStart(); player.previous() }, { onStart(); player.next() }, { lyricsOpen = false })
    val cover = coverBitmap(track.artwork, track.id)
    var targetTint by remember { mutableStateOf(coverTint(cover)) }
    LaunchedEffect(cover) { if (cover != null) targetTint = coverTint(cover) }
    val tint by animateColorAsState(targetTint, tween(1400), label = "Flowing album colour")
    val scale by animateFloatAsState(if (s.playing) 1f else .94f, tween(350), label = "Album size")
    val background = if (s.playerStyle == 1) listOf(Color(0xFF25262C), Color(0xFF101115))
        else if (s.playerStyle == 3) listOf(Color(0xFF123C40), Color(0xFF0A141F))
        else if (s.playerStyle == 2) listOf(tint.copy(red = tint.red * .6f), Color(0xFF111114))
        else listOf(tint, Color(0xFF1D1520))
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(background)).then(screenEntrance("player")).systemBarsPadding()) {
        if (s.playerStyle == 0) {
            Crossfade(track, animationSpec = tween(1200), label = "Ambient artwork fade") { backdrop ->
            val backdropCover = coverBitmap(backdrop.artwork, backdrop.id)
            Crossfade(backdropCover, animationSpec = tween(700), label = "Ambient cover arrival") { image ->
            Artwork(backdrop.artwork, Modifier.fillMaxWidth().height(maxHeight * .72f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = .4f }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.White, .45f to Color.White, .82f to Color.White.copy(alpha = .25f), 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }, trackId = backdrop.id, bitmap = image)
            }
            }
        }
        val artworkSize = minOf(maxWidth - 56.dp, maxHeight * (if (maxHeight < 650.dp) .34f else .43f))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).playerGlass(s.glassAppearance), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close, modifier = Modifier.size(48.dp).semantics { contentDescription = "Collapse player" }) {
                    Canvas(Modifier.size(22.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * .18f, size.height * .38f)
                            lineTo(size.width * .5f, size.height * .68f)
                            lineTo(size.width * .82f, size.height * .38f)
                        }
                        drawPath(path, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Text(if (s.blend > 0) "IN THE MIX" else "NOW PLAYING", color = Color.White.copy(alpha = .6f), letterSpacing = 2.sp, fontSize = 10.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { player.setHaptics(!s.haptics) }, enabled = s.hapticsSupported) {
                    Text(if (s.haptics) "Vibes on" else "Vibes off", color = if (s.haptics) Color(0xFFFF9BAB) else Color.White.copy(alpha = .6f), fontSize = 12.sp)
                }
                TextButton(onClick = settings) { Text("•••", color = Color.White) }
            }
            SwipeArtwork(track.id, left = { onStart(); player.next() }, right = { onStart(); player.previous() },
                up = { queueOpen = true }, down = close, modifier = Modifier.semantics { contentDescription = "Swipe artwork to change songs or open queue" }) {
            TrackArtworkTransition(track, s.index) { shownTrack ->
            val shownCover = coverBitmap(shownTrack.artwork, shownTrack.id)
            Crossfade(shownCover, animationSpec = tween(500), label = "Cover arrival") { image ->
            if (s.playerStyle == 3) {
                OrbitPlayer(shownTrack, image, artworkSize, s.positionMs, s.durationMs)
            } else if (s.playerStyle == 2 || s.playerStyle == 4) {
                VinylPlayer(shownTrack, image, s.playing && !s.loading && !lyricsOpen && !queueOpen, artworkSize, recordOnly = s.playerStyle == 4)
            } else {
                val frame = if (s.playerStyle == 1) Modifier.background(Color(0xFFF2EDE5)).padding(12.dp) else Modifier
                Artwork(shownTrack.artwork, Modifier.padding(vertical = 12.dp).size(artworkSize).scale(scale)
                    .shadow(22.dp, RoundedCornerShape(if (s.playerStyle == 1) 4.dp else 15.dp))
                    .clip(RoundedCornerShape(if (s.playerStyle == 1) 4.dp else 15.dp)).then(frame), trackId = shownTrack.id, bitmap = image)
            }
            }
            }
            }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = .6f), fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton(onClick = like) { Text(if (liked) "♥ Liked" else "♡ Like", color = Color(0xFFFF9BAB)) }
                    TextButton(onClick = addPlaylist) { Text("＋ Playlist", color = Color.White.copy(alpha = .8f)) }
                    TextButton(onClick = { lyricsOpen = true }) { Text("Lyrics", color = Color.White.copy(alpha = .8f)) }
                }
                s.message?.let { Text(it, color = Color(0xFFFFBAC6), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
            Slider(seeking ?: if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f, { seeking = it }, enabled = s.durationMs > 0,
                onValueChangeFinished = { seeking?.let { player.seek((it * s.durationMs).toLong()) }; seeking = null },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = .7f), inactiveTrackColor = Color.White.copy(alpha = .15f)), modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time(s.positionMs), color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                Text(if (s.loading) "Buffering…" else s.mixStatus, color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                Text("−${time((s.durationMs - s.positionMs).coerceAtLeast(0))}", color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 22.dp).playerGlass(s.glassAppearance).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Transport("Previous", Color.White, { onStart(); player.previous() }, true)
                Transport(if (s.playing) "Pause" else "Play", Color.White, toggle, true)
                Transport("Next", Color.White, { onStart(); player.next() }, true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { player.setAutoplay(!s.autoplay) }) { Text("∞  Autoplay", color = if (s.autoplay) Color(0xFFFF9BAB) else Color.White.copy(alpha = .5f)) }
                TextButton(onClick = queue) { Text("Up next  ☰", color = Color.White) }
            }
            Text(s.queue.getOrNull(s.index + 1)?.let { "Next · ${it.title}" } ?: if (s.findingRelated) "Finding a song to follow…" else "End of queue",
                color = Color.White.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 20.dp))
            TextButton(onClick = player::notInterested) { Text("Not interested · skip this song", color = Color.White.copy(alpha = .65f), fontSize = 12.sp) }
        }
    }
}

@Composable internal fun Transport(name: String, color: Color, click: () -> Unit, large: Boolean = false) {
    IconButton(onClick = click, modifier = Modifier.size(if (large) 64.dp else 44.dp).semantics { contentDescription = name }) {
        Canvas(Modifier.size(if (large) 34.dp else 21.dp)) {
            val w = size.width; val h = size.height
            fun triangle(left: Float, right: Float, backwards: Boolean = false) {
                drawPath(Path().apply { moveTo(if (backwards) right else left, 0f); lineTo(if (backwards) left else right, h / 2); lineTo(if (backwards) right else left, h); close() }, color)
            }
            when (name) {
                "Pause" -> { drawRoundRect(color, size = androidx.compose.ui.geometry.Size(w * .32f, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
                    drawRoundRect(color, topLeft = Offset(w * .65f, 0f), size = androidx.compose.ui.geometry.Size(w * .32f, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)) }
                "Next" -> { triangle(0f, w * .55f); triangle(w * .45f, w) }
                "Previous" -> { triangle(0f, w * .55f, true); triangle(w * .45f, w, true) }
                else -> triangle(w * .1f, w * .95f)
            }
        }
    }
}
private fun time(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
