package com.sidequest.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

private val Ink = Color(0xFF101216)
private val SoftInk = Color(0xFF2A2E36)
private val Muted = Color(0xFF737A88)
private val Paper = Color(0xFFF6F6F3)
private val Card = Color(0xFFFDFDFC)
private val Violet = Color(0xFF6E5AF7)
private val Border = Color(0xFFE4E5E8)
private val Warning = Color(0xFFFFB33D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Violet,
                    background = Paper,
                    surface = Card,
                    onSurface = Ink
                )
            ) { SidequestApp() }
        }
    }
}

private enum class Screen { AUTH, MAP }

@Composable
private fun SidequestApp() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf(if (session != null) Screen.MAP else Screen.AUTH) }

    when (screen) {
        Screen.AUTH -> AuthScreen(
            onSession = {
                store.save(it)
                session = it
                screen = Screen.MAP
            },
            onGuest = { screen = Screen.MAP }
        )
        Screen.MAP -> MapScreen(
            session = session,
            onSignIn = { screen = Screen.AUTH },
            onLogout = {
                store.clear()
                session = null
                screen = Screen.AUTH
            }
        )
    }
}

@Composable
private fun AuthScreen(onSession: (Session) -> Unit, onGuest: () -> Unit) {
    val scope = rememberCoroutineScope()
    var createMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (email.isBlank() || !email.contains("@")) {
            note = "Enter a valid email."
            return
        }
        if (password.length < 6) {
            note = "Password must have at least 6 characters."
            return
        }
        busy = true
        note = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (createMode) Api.signUp(email.trim(), password) else Api.signIn(email.trim(), password)
                }
            }.onSuccess { s ->
                if (s != null) onSession(s) else note = "Could not create the account."
            }.onFailure { note = it.message ?: "Authentication failed." }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = Ink) {
                    Text("S", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("sidequest", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ink)
                    Text("map what is happening around you", color = Muted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(38.dp))
            Text(if (createMode) "Create your account" else "Welcome back", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                if (createMode) "Save places and keep your Sidequest profile synced with Firebase."
                else "Sign in to your Sidequest profile, or explore the map without an account.",
                color = Muted,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEDEDEA)) {
                Row(Modifier.padding(4.dp)) {
                    AuthTab("Sign in", !createMode, Modifier.weight(1f)) { createMode = false; note = null }
                    AuthTab("Create account", createMode, Modifier.weight(1f)) { createMode = true; note = null }
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border)
            )

            note?.let {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFECEC)) {
                    Text(it, Modifier.padding(12.dp), color = Color(0xFF9D2A2A), fontSize = 13.sp)
                }
            }

            if (!Api.firebaseConfigured()) {
                Spacer(Modifier.height(12.dp))
                Text("Firebase project config is still missing from this build.", color = Warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (createMode) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onGuest, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Explore without an account", color = SoftInk, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AuthTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(Modifier.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Ink else Muted)
        }
    }
}

@Composable
private fun MapScreen(session: Session?, onSignIn: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(LatLng(45.9432, 24.9668)) }
    var zoom by remember { mutableDoubleStateOf(6.0) }
    var placeName by remember { mutableStateOf("Romania") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var feed by remember { mutableStateOf(DiscoverFeed()) }
    var loading by remember { mutableStateOf(false) }
    var feedOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var radius by remember { mutableIntStateOf(12) }
    var windowHours by remember { mutableIntStateOf(24) }
    var mapStyle by remember { mutableStateOf("Liberty") }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var selected by remember { mutableStateOf<MapPlace?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val markers = remember(feed) { feed.places + feed.wikiPlaces }

    fun loadArea(lat: Double, lon: Double, name: String, openFeed: Boolean = false) {
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { Api.discover(lat, lon, name, radius, windowHours, session) }
            }.onSuccess {
                center = LatLng(lat, lon)
                placeName = name
                feed = it
                if (openFeed) feedOpen = true
            }.onFailure { toast = it.message ?: "Could not refresh this area." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadArea(center.latitude, center.longitude, placeName) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            getCurrentLocation(context, fine) { lat, lon ->
                center = LatLng(lat, lon)
                zoom = 14.5
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                loadArea(lat, lon, "Current area")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFDDE5EA))) {
        NativeMap(
            center = center,
            zoom = zoom,
            markers = markers,
            styleName = mapStyle,
            onMapReady = { map = it },
            onCameraIdle = { lat, lon, z -> center = LatLng(lat, lon); zoom = z },
            onMarker = { selected = it }
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp))
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.weight(1f).shadow(12.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.94f)
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search city or place", color = Muted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = emptyList() }) { Icon(Icons.Rounded.Close, null) }
                        },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            scope.launch {
                                results = runCatching { withContext(Dispatchers.IO) { Api.searchPlaces(query) } }.getOrDefault(emptyList())
                            }
                        })
                    )
                }
                Spacer(Modifier.width(9.dp))
                RoundButton(Icons.Rounded.Tune) { settingsOpen = true }
            }

            AnimatedVisibility(results.isNotEmpty()) {
                Surface(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.97f),
                    shadowElevation = 12.dp
                ) {
                    Column(Modifier.padding(6.dp)) {
                        results.take(5).forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    query = r.name
                                    results = emptyList()
                                    center = LatLng(r.lat, r.lon)
                                    zoom = 13.5
                                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                                    loadArea(r.lat, r.lon, r.name)
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LocationOn, null, tint = Violet)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(r.name, fontWeight = FontWeight.Bold, color = Ink)
                                    if (r.subtitle.isNotBlank()) Text(r.subtitle, color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RoundButton(Icons.Rounded.MyLocation) {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                else getCurrentLocation(context, fine) { lat, lon ->
                    center = LatLng(lat, lon)
                    zoom = 14.5
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                    loadArea(lat, lon, "Current area")
                }
            }
            RoundButton(Icons.Rounded.Refresh) { loadArea(center.latitude, center.longitude, placeName) }
        }

        if (loading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Violet)

        if (feed.signal.mentions > 0) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 106.dp).clickable { feedOpen = true },
                shape = RoundedCornerShape(99.dp),
                color = Color(0xF5FFF2D9),
                shadowElevation = 5.dp
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Warning))
                    Spacer(Modifier.width(7.dp))
                    Text("${feed.signal.mentions} unusual media mentions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        selected?.let { place ->
            PlacePreview(
                p = place,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 92.dp),
                onClose = { selected = null },
                onSave = {
                    if (session == null) {
                        toast = "Sign in to save places."
                    } else {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { Api.savePlace(session, place) } }
                                .onSuccess { toast = "Saved." }
                                .onFailure { toast = it.message ?: "Could not save." }
                        }
                    }
                }
            )
        }

        Surface(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth().shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.95f)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(placeName, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${markers.size} mapped places · ${feed.articles.size} recent articles", color = Muted, fontSize = 11.sp)
                }
                Button(onClick = { feedOpen = true }, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text("Open feed", fontWeight = FontWeight.Bold)
                }
            }
        }

        toast?.let { text ->
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
                shape = RoundedCornerShape(99.dp),
                color = Ink
            ) {
                Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontSize = 12.sp)
            }
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(2200)
                toast = null
            }
        }

        AnimatedVisibility(feedOpen) {
            FeedSheet(
                feed = feed,
                placeName = placeName,
                onClose = { feedOpen = false },
                onPlace = { p ->
                    selected = p
                    feedOpen = false
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 15.0))
                },
                onArticle = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            )
        }

        AnimatedVisibility(settingsOpen) {
            SettingsSheet(
                radius = radius,
                window = windowHours,
                style = mapStyle,
                onRadius = { radius = it },
                onWindow = { windowHours = it },
                onStyle = { mapStyle = it },
                onClose = { settingsOpen = false },
                onRefresh = {
                    settingsOpen = false
                    loadArea(center.latitude, center.longitude, placeName)
                },
                session = session,
                onSignIn = onSignIn,
                onLogout = onLogout
            )
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        Modifier.size(50.dp).shadow(10.dp, CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Ink) }
    }
}

@Composable
private fun NativeMap(
    center: LatLng,
    zoom: Double,
    markers: List<MapPlace>,
    styleName: String,
    onMapReady: (MapLibreMap) -> Unit,
    onCameraIdle: (Double, Double, Double) -> Unit,
    onMarker: (MapPlace) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember { MapView(context).apply { onCreate(Bundle()) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> view.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) { v ->
        if (map == null) {
            v.getMapAsync { m ->
                map = m
                m.uiSettings.isLogoEnabled = false
                m.cameraPosition = CameraPosition.Builder().target(center).zoom(zoom).build()
                m.setStyle(Style.Builder().fromUri(styleUri(styleName)))
                m.addOnCameraIdleListener {
                    m.cameraPosition.target?.let { c -> onCameraIdle(c.latitude, c.longitude, m.cameraPosition.zoom) }
                }
                onMapReady(m)
            }
        }
    }

    LaunchedEffect(styleName, map) { map?.setStyle(Style.Builder().fromUri(styleUri(styleName))) }
    LaunchedEffect(markers, map) {
        map?.let { m ->
            m.clear()
            markers.take(120).forEach { p ->
                m.addMarker(MarkerOptions().position(LatLng(p.lat, p.lon)).title(p.title).snippet(p.source))
            }
            m.setOnMarkerClickListener { marker ->
                markers.firstOrNull { it.title == marker.title }?.let(onMarker)
                true
            }
        }
    }
}

private fun styleUri(s: String) = when (s) {
    "Bright" -> "https://tiles.openfreemap.org/styles/bright"
    "Positron" -> "https://tiles.openfreemap.org/styles/positron"
    else -> "https://tiles.openfreemap.org/styles/liberty"
}

@Composable
private fun FeedSheet(feed: DiscoverFeed, placeName: String, onClose: () -> Unit, onPlace: (MapPlace) -> Unit, onArticle: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClose)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.74f).clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Paper,
            shadowElevation = 24.dp
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(placeName, fontSize = 27.sp, fontWeight = FontWeight.Black, color = Ink)
                            Text("Live area feed · ${feed.signal.window}", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
                    }
                }
                if (feed.signal.mentions > 0) item { SignalCard(feed.signal) }
                val places = feed.wikiPlaces + feed.places
                if (places.isNotEmpty()) {
                    item { SectionTitle("PLACES", "Documented map and encyclopedia sources") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(places.take(18), key = { it.id }) { p -> PlaceCard(p) { onPlace(p) } }
                        }
                    }
                }
                if (feed.articles.isNotEmpty()) {
                    item { SectionTitle("RECENT COVERAGE", "Articles matched to this area") }
                    items(feed.articles.take(25)) { article -> NewsCard(article) { article.url?.let(onArticle) } }
                }
                item {
                    Text(
                        "Sources: OpenStreetMap/Overpass, Wikipedia/Wikimedia and GDELT. Keyword signals are not proof of paranormal activity. Do not enter restricted or unsafe structures.",
                        Modifier.padding(20.dp),
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalCard(s: Signal) {
    Surface(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF2D8)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF8A5B00))
            Spacer(Modifier.width(11.dp))
            Column {
                Text("${s.mentions} unusual media mentions", fontWeight = FontWeight.Black, color = Ink)
                Text(s.disclaimer, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Ink)
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun PlaceCard(p: MapPlace, onClick: () -> Unit) {
    Surface(
        Modifier.width(225.dp).height(180.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFE8E9EB),
        shadowElevation = 3.dp
    ) {
        Box {
            if (p.image != null) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Color(0xFFE5E7EB)))
            Surface(Modifier.align(Alignment.BottomStart).padding(10.dp), shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.93f)) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Text(p.title, color = Ink, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(p.source, color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun NewsCard(a: NewsItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(84.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFE7E8EA)) {
            if (a.image != null) AsyncImage(a.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Article, null, tint = Muted) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontWeight = FontWeight.Bold, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(a.domain, a.seenDate).joinToString(" · "), color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlacePreview(p: MapPlace, modifier: Modifier, onClose: () -> Unit, onSave: () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.96f), shadowElevation = 14.dp) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFE7E8EA)) {
                if (p.image != null) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocationOn, null) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.source, color = Muted, fontSize = 10.sp)
            }
            IconButton(onClick = onSave) { Icon(Icons.Rounded.BookmarkAdd, null, tint = Violet) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
        }
    }
}

@Composable
private fun SettingsSheet(
    radius: Int,
    window: Int,
    style: String,
    onRadius: (Int) -> Unit,
    onWindow: (Int) -> Unit,
    onStyle: (String) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    session: Session?,
    onSignIn: () -> Unit,
    onLogout: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)).clickable(onClick = onClose)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Paper
        ) {
            Column(Modifier.padding(20.dp).padding(bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Map settings", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
                }
                Text("Search radius", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 12, 25).forEach { FilterChip(selected = radius == it, onClick = { onRadius(it) }, label = { Text("$it km") }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("News window", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 24, 168).forEach { FilterChip(selected = window == it, onClick = { onWindow(it) }, label = { Text(if (it == 168) "7 days" else "$it h") }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Map style", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Liberty", "Bright", "Positron").forEach { s -> FilterChip(selected = style == s, onClick = { onStyle(s) }, label = { Text(s) }) }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRefresh, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text("Apply & refresh", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(9.dp))
                if (session == null) {
                    OutlinedButton(onClick = { onClose(); onSignIn() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("Sign in") }
                } else {
                    OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Rounded.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text(session.email, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun getCurrentLocation(context: android.content.Context, high: Boolean, result: (Double, Double) -> Unit) {
    val lm = context.getSystemService(LocationManager::class.java)
    val provider = if (high && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
    try {
        lm.getCurrentLocation(provider, CancellationSignal(), context.mainExecutor) { location ->
            if (location != null) result(location.latitude, location.longitude)
        }
    } catch (_: SecurityException) {
    }
}
