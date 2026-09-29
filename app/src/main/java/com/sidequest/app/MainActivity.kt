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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.*

private val Ink = Color(0xFF101218)
private val Muted = Color(0xFF6F7682)
private val Violet = Color(0xFF6957FF)
private val SurfaceSoft = Color(0xFFF7F8FB)
private val Alert = Color(0xFFFF6D73)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Violet,
                    background = SurfaceSoft,
                    surface = Color.White
                )
            ) {
                SidequestApp()
            }
        }
    }
}

@Composable
private fun SidequestApp() {
    val context = LocalContext.current
    var account by remember { mutableStateOf(FirebaseBackend.currentAccount(context)) }
    MapHome(account = account, onAccountChanged = { account = it })
}

@Composable
private fun MapHome(account: Account?, onAccountChanged: (Account?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var center by remember { mutableStateOf(LatLng(45.9432, 24.9668)) }
    var zoom by remember { mutableDoubleStateOf(6.0) }
    var placeName by remember { mutableStateOf("Romania") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var feed by remember { mutableStateOf(DiscoverFeed()) }
    var reports by remember { mutableStateOf<List<CommunityReport>>(emptyList()) }
    var selected by remember { mutableStateOf<MapPlace?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var feedOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableIntStateOf(12) }
    var windowHours by remember { mutableIntStateOf(24) }
    var message by remember { mutableStateOf<String?>(null) }

    val nearbyReports = remember(reports, center, radiusKm) {
        reports.filter { distanceKm(center.latitude, center.longitude, it.lat, it.lon) <= radiusKm * 2.0 }
    }
    val reportMarkers = remember(nearbyReports) {
        nearbyReports.map {
            MapPlace(
                id = "report-${it.id}",
                title = it.title.ifBlank { "Community report" },
                subtitle = it.details,
                lat = it.lat,
                lon = it.lon,
                source = "Community report",
                kind = it.category
            )
        }
    }
    val markers = remember(feed, reportMarkers) { feed.places + feed.wikiPlaces + reportMarkers }

    fun refresh(openFeed: Boolean = false) {
        loading = true
        scope.launch {
            val publicData = runCatching {
                withContext(Dispatchers.IO) {
                    Api.discover(center.latitude, center.longitude, placeName, radiusKm, windowHours)
                }
            }
            val community = runCatching {
                withContext(Dispatchers.IO) { FirebaseBackend.loadReports(context) }
            }
            publicData.onSuccess { feed = it }.onFailure { message = "Live sources could not refresh." }
            community.onSuccess { reports = it }
            loading = false
            if (openFeed) feedOpen = true
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            getCurrentLocation(context, fine) { lat, lon ->
                center = LatLng(lat, lon)
                zoom = 14.2
                placeName = "Current area"
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                refresh()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFDDE5EC))) {
        NativeMap(
            center = center,
            zoom = zoom,
            markers = markers,
            onMapReady = { m ->
                map = m
                m.setOnMarkerClickListener { marker ->
                    selected = markers.firstOrNull { it.title == marker.title }
                    selected != null
                }
            },
            onCameraIdle = { lat, lon, z ->
                center = LatLng(lat, lon)
                zoom = z
            }
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp))
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f).shadow(14.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.94f)
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search city, county or area", color = Muted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; results = emptyList() }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            scope.launch {
                                results = runCatching {
                                    withContext(Dispatchers.IO) { Api.searchPlaces(query) }
                                }.getOrDefault(emptyList())
                            }
                        })
                    )
                }
                Spacer(Modifier.width(9.dp))
                RoundButton(Icons.Rounded.Person, account != null) { accountOpen = true }
            }

            if (results.isNotEmpty()) {
                Surface(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.97f),
                    shadowElevation = 12.dp
                ) {
                    Column(Modifier.padding(6.dp)) {
                        results.take(5).forEach { r ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        placeName = r.name
                                        center = LatLng(r.lat, r.lon)
                                        zoom = 13.7
                                        query = r.name
                                        results = emptyList()
                                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                                        refresh()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LocationOn, null, tint = Violet)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(r.name, fontWeight = FontWeight.Bold)
                                    if (r.subtitle.isNotBlank()) Text(r.subtitle, color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniChip("${radiusKm} km") {
                radiusKm = when (radiusKm) { 5 -> 12; 12 -> 25; else -> 5 }
                refresh()
            }
            if (feed.signal.mentions > 0) {
                MiniChip("${feed.signal.mentions} unusual mentions", warning = true) { feedOpen = true }
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RoundButton(Icons.Rounded.MyLocation) {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                } else {
                    getCurrentLocation(context, fine) { lat, lon ->
                        center = LatLng(lat, lon)
                        zoom = 14.2
                        placeName = "Current area"
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                        refresh()
                    }
                }
            }
            RoundButton(Icons.Rounded.Refresh) { refresh() }
        }

        if (loading) {
            LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Violet)
        }

        selected?.let { place ->
            PlacePreview(
                place = place,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 116.dp),
                onClose = { selected = null },
                onSave = {
                    if (account == null) {
                        accountOpen = true
                    } else {
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.savePlace(context, place) } }
                            message = if (result.isSuccess) "Saved to Firebase." else result.exceptionOrNull()?.message
                        }
                    }
                }
            )
        }

        NearbyBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            placeName = placeName,
            reportCount = nearbyReports.size,
            incidentCount = feed.incidents.size,
            onOpen = { feedOpen = true },
            onAdd = {
                if (account == null) accountOpen = true else reportOpen = true
            }
        )

        message?.let { text ->
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 114.dp, start = 18.dp, end = 18.dp),
                shape = RoundedCornerShape(14.dp),
                color = Ink
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { message = null }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White)
                    }
                }
            }
        }

        if (feedOpen) {
            FeedSheet(
                placeName = placeName,
                feed = feed,
                reports = nearbyReports,
                windowHours = windowHours,
                onWindow = { windowHours = it; refresh() },
                onClose = { feedOpen = false },
                onPlace = { p ->
                    selected = p
                    feedOpen = false
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 15.0))
                },
                onArticle = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            )
        }

        if (accountOpen) {
            AccountSheet(
                account = account,
                configured = FirebaseBackend.isConfigured(context),
                onClose = { accountOpen = false },
                onSignedIn = { onAccountChanged(it); accountOpen = false; refresh() },
                onSignedOut = {
                    FirebaseBackend.signOut(context)
                    onAccountChanged(null)
                    accountOpen = false
                }
            )
        }

        if (reportOpen) {
            ReportSheet(
                center = center,
                onClose = { reportOpen = false },
                onSubmit = { category, title, details ->
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                FirebaseBackend.createReport(context, category, title, details, center.latitude, center.longitude)
                            }
                        }
                        if (result.isSuccess) {
                            reportOpen = false
                            message = "Report posted. Location is intentionally approximate."
                            refresh()
                        } else {
                            message = result.exceptionOrNull()?.message
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun NativeMap(
    center: LatLng,
    zoom: Double,
    markers: List<MapPlace>,
    onMapReady: (MapLibreMap) -> Unit,
    onCameraIdle: (Double, Double, Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember { MapView(context).apply { onCreate(Bundle()) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
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
                m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"))
                m.addOnCameraIdleListener {
                    m.cameraPosition.target?.let { c -> onCameraIdle(c.latitude, c.longitude, m.cameraPosition.zoom) }
                }
                onMapReady(m)
            }
        }
    }

    LaunchedEffect(markers, map) {
        map?.let { m ->
            m.clear()
            markers.take(140).forEach { p ->
                m.addMarker(
                    MarkerOptions().position(LatLng(p.lat, p.lon)).title(p.title).snippet(p.source)
                )
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        Modifier.size(50.dp).shadow(11.dp, CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) Violet else Color.White.copy(alpha = 0.94f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (active) Color.White else Ink)
        }
    }
}

@Composable
private fun MiniChip(text: String, warning: Boolean = false, onClick: () -> Unit) {
    Surface(
        Modifier.clip(RoundedCornerShape(99.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(99.dp),
        color = if (warning) Color(0xFFFFF0D2) else Color.White.copy(alpha = 0.92f),
        shadowElevation = 7.dp
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (warning) Color(0xFF8B5C00) else Ink)
    }
}

@Composable
private fun NearbyBar(
    modifier: Modifier,
    placeName: String,
    reportCount: Int,
    incidentCount: Int,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth().height(92.dp).shadow(18.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.96f)
    ) {
        Row(Modifier.fillMaxSize().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(placeName, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("$reportCount community reports · $incidentCount incident articles", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onOpen) { Icon(Icons.Rounded.KeyboardArrowRight, null) }
            FloatingActionButton(onClick = onAdd, modifier = Modifier.size(54.dp), shape = CircleShape, containerColor = Violet, contentColor = Color.White) {
                Icon(Icons.Rounded.Add, null)
            }
        }
    }
}

@Composable
private fun FeedSheet(
    placeName: String,
    feed: DiscoverFeed,
    reports: List<CommunityReport>,
    windowHours: Int,
    onWindow: (Int) -> Unit,
    onClose: () -> Unit,
    onPlace: (MapPlace) -> Unit,
    onArticle: (String) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClose)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.78f).clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = SurfaceSoft
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(placeName, fontSize = 27.sp, fontWeight = FontWeight.Black)
                            Text("Nearby activity", color = Muted)
                        }
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(6, 24, 168)) { h ->
                            FilterChip(selected = windowHours == h, onClick = { onWindow(h) }, label = { Text(if (h == 168) "7 days" else "$h h") })
                        }
                    }
                }

                if (reports.isNotEmpty()) {
                    item { SectionHeader("COMMUNITY REPORTS", "Unverified user reports · approximate map positions") }
                    items(reports.take(20), key = { it.id }) { r -> ReportRow(r) }
                }

                if (feed.incidents.isNotEmpty()) {
                    item { SectionHeader("INCIDENT COVERAGE", "Recent articles matched to this area") }
                    items(feed.incidents.take(15)) { a -> NewsRow(a) { a.url?.let(onArticle) } }
                }

                if (feed.unusual.isNotEmpty()) {
                    item { SectionHeader("UNUSUAL MENTIONS", "Keyword matches only — not proof of paranormal activity") }
                    items(feed.unusual.take(15)) { a -> NewsRow(a) { a.url?.let(onArticle) } }
                }

                val places = feed.wikiPlaces + feed.places
                if (places.isNotEmpty()) {
                    item { SectionHeader("DOCUMENTED PLACES", "OpenStreetMap and Wikipedia") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(places.take(20), key = { it.id }) { p -> PlaceCard(p) { onPlace(p) } }
                        }
                    }
                }

                item {
                    Text(
                        "Community reports are not verified. Do not confront people or enter restricted places. For immediate danger, contact local emergency services.",
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
private fun AccountSheet(
    account: Account?,
    configured: Boolean,
    onClose: () -> Unit,
    onSignedIn: (Account) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClose)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(22.dp).padding(bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (account == null) "Account" else "Signed in", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
                }

                if (account != null) {
                    Text(account.email, color = Muted)
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = onSignedOut, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                    return@Column
                }

                if (!configured) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF2D8)) {
                        Text(
                            "Firebase code is ready, but this build is not linked to a Firebase project yet. Add app/google-services.json and enable Email/Password in Firebase Authentication.",
                            Modifier.padding(14.dp),
                            color = Color(0xFF775300),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
                note?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Alert, fontSize = 12.sp) }
                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (!configured) { note = "Firebase project is not connected yet."; return@Button }
                        if (email.isBlank() || password.length < 6) { note = "Use a valid email and at least 6 characters."; return@Button }
                        busy = true
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.signIn(context, email.trim(), password) } }
                            busy = false
                            result.onSuccess(onSignedIn).onFailure { note = it.localizedMessage ?: "Sign in failed." }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Sign in", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (!configured) { note = "Firebase project is not connected yet."; return@OutlinedButton }
                        if (email.isBlank() || password.length < 6) { note = "Use a valid email and at least 6 characters."; return@OutlinedButton }
                        busy = true
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.signUp(context, email.trim(), password) } }
                            busy = false
                            result.onSuccess(onSignedIn).onFailure { note = it.localizedMessage ?: "Account creation failed." }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(17.dp)
                ) { Text("Create account", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ReportSheet(center: LatLng, onClose: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var category by remember { mutableStateOf("suspicious") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onClose)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(22.dp).padding(bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add report", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
                }
                Text("Map position is saved approximately, not as an exact address.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("incident", "suspicious", "paranormal", "other")) { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.replaceFirstChar { it.uppercase() }) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(title, { title = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text("What happened?") }, shape = RoundedCornerShape(16.dp), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(details, { details = it.take(500) }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("Details") }, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(8.dp))
                Text("Do not include private people's names, phone numbers, exact home addresses, or instructions to confront anyone.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { if (title.isNotBlank()) onSubmit(category, title.trim(), details.trim()) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("Post report", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text("Approx. center: ${"%.3f".format(center.latitude)}, ${"%.3f".format(center.longitude)}", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun PlacePreview(place: MapPlace, modifier: Modifier, onClose: () -> Unit, onSave: () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.97f), shadowElevation = 14.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(54.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFE8EBF0)) {
                if (place.image != null) AsyncImage(place.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocationOn, null) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(place.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place.source, color = Muted, fontSize = 11.sp)
            }
            IconButton(onClick = onSave) { Icon(Icons.Rounded.BookmarkAdd, null, tint = Violet) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Muted)
        Text(subtitle, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun ReportRow(report: CommunityReport) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(10.dp).padding(top = 4.dp).clip(CircleShape).background(if (report.category == "incident") Alert else Violet))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(report.title, fontWeight = FontWeight.Bold)
            if (report.details.isNotBlank()) Text(report.details, color = Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(report.category, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NewsRow(article: NewsItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(76.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFE7E9EE)) {
            if (article.image != null) AsyncImage(article.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Article, null, tint = Muted) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(article.title, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(article.domain, article.seenDate).joinToString(" · "), color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlaceCard(place: MapPlace, onClick: () -> Unit) {
    Surface(Modifier.width(210.dp).height(150.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp) {
        Box {
            if (place.image != null) AsyncImage(place.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Color(0xFFE8EAF0)))
            Surface(Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = Ink.copy(alpha = 0.80f)) {
                Column(Modifier.padding(11.dp)) {
                    Text(place.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(place.source, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                }
            }
        }
    }
}

private fun getCurrentLocation(context: android.content.Context, highAccuracy: Boolean, result: (Double, Double) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = if (highAccuracy && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
    try {
        manager.getCurrentLocation(provider, CancellationSignal(), context.mainExecutor) { location ->
            if (location != null) result(location.latitude, location.longitude)
        }
    } catch (_: SecurityException) {
    }
}

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earth = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
}
