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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val V11Ink = Color(0xFF101216)
private val V11Muted = Color(0xFF737985)
private val V11Accent = Color(0xFF6B5CFF)
private val V11Soft = Color(0xFFF1F2F5)
private val V11Warn = Color(0xFFF2A33A)

private enum class V11Sheet { NONE, DISCOVER, ACCOUNT, REPORT, PLACE }

class V11Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme(lightColorScheme(primary = V11Accent, background = Color(0xFFF6F7F9), surface = Color.White)) {
                V11App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V11App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(FirebaseBackend.currentAccount(context)) }
    var center by remember { mutableStateOf(LatLng(45.9432, 24.9668)) }
    var zoom by remember { mutableDoubleStateOf(6.0) }
    var placeName by remember { mutableStateOf("Romania") }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var feed by remember { mutableStateOf(DiscoverFeed()) }
    var reports by remember { mutableStateOf<List<CommunityReport>>(emptyList()) }
    var selected by remember { mutableStateOf<MapPlace?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var sheet by remember { mutableStateOf(V11Sheet.NONE) }
    var loading by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableIntStateOf(12) }
    var windowHours by remember { mutableIntStateOf(24) }
    var note by remember { mutableStateOf<String?>(null) }

    val reportPins = remember(reports, center, radiusKm) {
        reports.filter { v11Distance(center.latitude, center.longitude, it.lat, it.lon) <= radiusKm * 2 }
            .map { MapPlace("r-${it.id}", it.title.ifBlank { "Community report" }, it.details, it.lat, it.lon, source = "Community · unverified", kind = it.category) }
    }
    val markers = remember(feed, reportPins) { feed.places + feed.wikiPlaces + reportPins }

    fun refresh(open: Boolean = false) {
        loading = true
        scope.launch {
            val a = runCatching { withContext(Dispatchers.IO) { Api.discover(center.latitude, center.longitude, placeName, radiusKm, windowHours) } }
            val b = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.loadReports(context) } }
            a.onSuccess { feed = it }.onFailure { note = "Live sources could not refresh." }
            b.onSuccess { reports = it }
            loading = false
            if (open) sheet = V11Sheet.DISCOVER
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) v11CurrentLocation(context, fine) { lat, lon ->
            center = LatLng(lat, lon); zoom = 14.2; placeName = "Current area"
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom)); refresh()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFDDE4EA))) {
        V11Map(center, zoom, markers, { m ->
            map = m
            m.setOnMarkerClickListener { marker ->
                selected = markers.firstOrNull { it.title == marker.title }
                if (selected != null) sheet = V11Sheet.PLACE
                selected != null
            }
        }) { lat, lon, z -> center = LatLng(lat, lon); zoom = z }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter), color = V11Accent, trackColor = Color.Transparent)

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.weight(1f).border(1.dp, Color.White.copy(.75f), RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp), color = Color.White.copy(.92f), shadowElevation = 12.dp
                ) {
                    TextField(
                        query, { query = it; if (it.isBlank()) searchResults = emptyList() },
                        placeholder = { Text("Search anywhere", color = V11Muted) },
                        leadingIcon = { Box(Modifier.size(28.dp).clip(CircleShape).background(V11Accent), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(16.dp)) } },
                        trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = ""; searchResults = emptyList() }) { Icon(Icons.Rounded.Close, null) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.trim().length >= 2) scope.launch { searchResults = runCatching { withContext(Dispatchers.IO) { Api.searchPlaces(query.trim()) } }.getOrDefault(emptyList()) }
                        }),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(9.dp))
                V11Round(if (account == null) Icons.Rounded.Person else Icons.Rounded.AccountCircle) { sheet = V11Sheet.ACCOUNT }
            }
            if (searchResults.isNotEmpty()) {
                Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(22.dp), color = Color.White.copy(.97f), shadowElevation = 10.dp) {
                    Column(Modifier.padding(6.dp)) {
                        searchResults.take(5).forEach { r ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                query = r.name; searchResults = emptyList(); placeName = r.name; center = LatLng(r.lat, r.lon); zoom = 13.7
                                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom)); refresh()
                            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, null, tint = V11Accent); Spacer(Modifier.width(10.dp))
                                Column { Text(r.name, fontWeight = FontWeight.Bold); if (r.subtitle.isNotBlank()) Text(r.subtitle, color = V11Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }

        V11Round(Icons.Rounded.MyLocation, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            else v11CurrentLocation(context, fine) { lat, lon ->
                center = LatLng(lat, lon); zoom = 14.2; placeName = "Current area"
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom)); refresh()
            }
        }

        Text("© OpenStreetMap contributors · OpenFreeMap", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 86.dp).background(Color.White.copy(.78f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 9.sp, color = V11Muted)

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp).fillMaxWidth().border(1.dp, Color.White.copy(.75f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp), color = Color.White.copy(.93f), shadowElevation = 14.dp
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { sheet = V11Sheet.DISCOVER }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(placeName, fontWeight = FontWeight.Black, color = V11Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (feed.signal.mentions > 0) "${feed.signal.mentions} unusual mentions" else "Nearby places & live coverage", color = if (feed.signal.mentions > 0) V11Warn else V11Muted, fontSize = 11.sp)
                }
                V11Dock(Icons.Rounded.Explore) { sheet = V11Sheet.DISCOVER }; Spacer(Modifier.width(6.dp))
                Surface(Modifier.size(48.dp), onClick = { sheet = if (account == null) V11Sheet.ACCOUNT else V11Sheet.REPORT }, shape = CircleShape, color = V11Ink) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) } }
                Spacer(Modifier.width(6.dp)); V11Dock(if (account == null) Icons.Rounded.Person else Icons.Rounded.AccountCircle) { sheet = V11Sheet.ACCOUNT }
            }
        }

        note?.let { txt ->
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 94.dp, start = 20.dp, end = 20.dp), shape = RoundedCornerShape(18.dp), color = V11Ink) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text(txt, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { note = null }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(17.dp)) } }
            }
        }
    }

    when (sheet) {
        V11Sheet.DISCOVER -> ModalBottomSheet(onDismissRequest = { sheet = V11Sheet.NONE }, containerColor = Color.White) {
            V11Discover(placeName, feed, reports, radiusKm, windowHours, { radiusKm = it; refresh() }, { windowHours = it; refresh() }, { refresh(true) }, { p -> selected = p; sheet = V11Sheet.PLACE }, { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } })
        }
        V11Sheet.ACCOUNT -> ModalBottomSheet(onDismissRequest = { sheet = V11Sheet.NONE }, containerColor = Color.White) {
            V11Account(account, FirebaseBackend.isConfigured(context), { account = it; sheet = V11Sheet.NONE; refresh() }, { FirebaseBackend.signOut(context); account = null; sheet = V11Sheet.NONE })
        }
        V11Sheet.REPORT -> ModalBottomSheet(onDismissRequest = { sheet = V11Sheet.NONE }, containerColor = Color.White) {
            V11Report(center) { category, title, details ->
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.createReport(context, category, title, details, center.latitude, center.longitude) } }
                    if (result.isSuccess) { note = "Report posted with approximate location."; sheet = V11Sheet.NONE; refresh() } else note = result.exceptionOrNull()?.message ?: "Could not post report."
                }
            }
        }
        V11Sheet.PLACE -> selected?.let { p -> ModalBottomSheet(onDismissRequest = { selected = null; sheet = V11Sheet.NONE }, containerColor = Color.White) { V11Place(p, account != null) {
            if (account == null) sheet = V11Sheet.ACCOUNT else scope.launch { val r = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.savePlace(context, p) } }; note = if (r.isSuccess) "Saved." else r.exceptionOrNull()?.message ?: "Could not save." }
        } } }
        V11Sheet.NONE -> Unit
    }
}

@Composable private fun V11Round(icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.size(50.dp), onClick = onClick, shape = CircleShape, color = Color.White.copy(.93f), shadowElevation = 10.dp) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = V11Ink, modifier = Modifier.size(21.dp)) } }
}
@Composable private fun V11Dock(icon: ImageVector, onClick: () -> Unit) { Surface(Modifier.size(44.dp), onClick = onClick, shape = CircleShape, color = V11Soft) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = V11Ink, modifier = Modifier.size(20.dp)) } } }
@Composable private fun V11Pill(text: String, selected: Boolean, onClick: () -> Unit) { Surface(onClick = onClick, shape = RoundedCornerShape(99.dp), color = if (selected) V11Ink else V11Soft) { Text(text, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = if (selected) Color.White else V11Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }

@Composable
private fun V11Discover(placeName: String, feed: DiscoverFeed, reports: List<CommunityReport>, radius: Int, hours: Int, onRadius: (Int) -> Unit, onHours: (Int) -> Unit, onRefresh: () -> Unit, onPlace: (MapPlace) -> Unit, onArticle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(placeName, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("Public sources + community reports", color = V11Muted, fontSize = 12.sp) }; IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, null) } }
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { item { V11Pill("5 km", radius == 5) { onRadius(5) } }; item { V11Pill("12 km", radius == 12) { onRadius(12) } }; item { V11Pill("25 km", radius == 25) { onRadius(25) } }; item { Spacer(Modifier.width(4.dp)) }; item { V11Pill("6h", hours == 6) { onHours(6) } }; item { V11Pill("24h", hours == 24) { onHours(24) } }; item { V11Pill("7d", hours == 168) { onHours(168) } } }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.heightIn(max = 620.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (feed.signal.mentions > 0) item { Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF3DD)) { Column(Modifier.padding(14.dp)) { Text("${feed.signal.mentions} unusual media mentions", fontWeight = FontWeight.Black); Text("Keyword matches only — not evidence of paranormal activity.", color = V11Muted, fontSize = 11.sp) } } }
            val places = (feed.places + feed.wikiPlaces).take(20)
            if (places.isNotEmpty()) { item { V11Header("PLACES", "Documented nearby places") }; items(places) { p -> Row(Modifier.fillMaxWidth().clickable { onPlace(p) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(44.dp), shape = CircleShape, color = V11Soft) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Place, null, tint = V11Accent) } }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(p.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(p.source, color = V11Muted, fontSize = 11.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = V11Muted) } } }
            if (reports.isNotEmpty()) { item { V11Header("COMMUNITY", "Unverified · approximate locations") }; items(reports.take(12)) { r -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) { Icon(Icons.Rounded.Report, null, tint = Color(0xFFFF6A72)); Spacer(Modifier.width(10.dp)); Column { Text(r.title.ifBlank { "Community report" }, fontWeight = FontWeight.Bold); Text(r.details.ifBlank { r.category }, color = V11Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } }
            if (feed.incidents.isNotEmpty()) { item { V11Header("INCIDENT COVERAGE", "Recent local coverage") }; items(feed.incidents.take(12)) { a -> V11News(a, onArticle) } }
            if (feed.articles.isNotEmpty()) { item { V11Header("LOCAL COVERAGE", "Recent articles") }; items(feed.articles.take(14)) { a -> V11News(a, onArticle) } }
            if (places.isEmpty() && reports.isEmpty() && feed.articles.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Nothing useful found here yet.", color = V11Muted) } }
        }
    }
}
@Composable private fun V11Header(a: String, b: String) { Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)) { Text(a, color = V11Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text(b, fontWeight = FontWeight.Bold) } }
@Composable private fun V11News(a: NewsItem, onArticle: (String) -> Unit) { Row(Modifier.fillMaxWidth().clickable(enabled = a.url != null) { a.url?.let(onArticle) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), shape = CircleShape, color = V11Soft) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Article, null, tint = V11Muted) } }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(a.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(a.domain ?: "news", color = V11Muted, fontSize = 10.sp) }; Icon(Icons.Rounded.OpenInNew, null, tint = V11Muted, modifier = Modifier.size(17.dp)) } }

@Composable
private fun V11Account(account: Account?, configured: Boolean, onSigned: (Account) -> Unit, onOut: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var email by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 32.dp)) {
        Text(if (account == null) "sidequest! account" else "Your account", fontSize = 25.sp, fontWeight = FontWeight.Black)
        if (account != null) { Text(account.email, color = V11Muted); Spacer(Modifier.height(20.dp)); OutlinedButton(onClick = onOut, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Sign out", fontWeight = FontWeight.Bold) }; return@Column }
        Text("Save places and post community reports.", color = V11Muted); Spacer(Modifier.height(18.dp))
        if (!configured) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF2DD)) { Column(Modifier.padding(15.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CloudOff, null, tint = V11Warn); Spacer(Modifier.width(8.dp)); Text("Firebase isn't connected", fontWeight = FontWeight.Black) }; Text("Add the Firebase Android config for com.sidequest.app before accounts can work.", color = V11Muted, fontSize = 12.sp) } }; Spacer(Modifier.height(14.dp)) }
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(18.dp)); Spacer(Modifier.height(9.dp)); OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(18.dp)); msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFFF6A72), fontSize = 12.sp) }; Spacer(Modifier.height(12.dp))
        Button(onClick = { if (!configured) { msg = "Firebase config is missing."; return@Button }; if (email.isBlank() || pass.length < 6) { msg = "Use a valid email and 6+ character password."; return@Button }; busy = true; scope.launch { runCatching { FirebaseBackend.signIn(context, email.trim(), pass) }.onSuccess(onSigned).onFailure { msg = it.message ?: "Sign in failed." }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = V11Ink)) { Text("Sign in", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { if (!configured) { msg = "Firebase config is missing."; return@OutlinedButton }; if (email.isBlank() || pass.length < 6) { msg = "Use a valid email and 6+ character password."; return@OutlinedButton }; busy = true; scope.launch { runCatching { FirebaseBackend.signUp(context, email.trim(), pass) }.onSuccess(onSigned).onFailure { msg = it.message ?: "Account creation failed." }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Create account", fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun V11Report(center: LatLng, onSubmit: (String, String, String) -> Unit) { var category by remember { mutableStateOf("safety") }; var title by remember { mutableStateOf("") }; var details by remember { mutableStateOf("") }; Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 32.dp)) { Text("Add a report", fontSize = 25.sp, fontWeight = FontWeight.Black); Text("Unverified public report · approximate location.", color = V11Muted, fontSize = 12.sp); Spacer(Modifier.height(14.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("safety","incident","suspicious","unusual","other")) { c -> V11Pill(c.replaceFirstChar { it.uppercase() }, category == c) { category = c } } }; Spacer(Modifier.height(12.dp)); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Short title") }, singleLine = true, shape = RoundedCornerShape(18.dp)); Spacer(Modifier.height(9.dp)); OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth().height(110.dp), label = { Text("What did you notice?") }, shape = RoundedCornerShape(18.dp)); Spacer(Modifier.height(8.dp)); Text("Map center: %.3f, %.3f".format(center.latitude, center.longitude), color = V11Muted, fontSize = 10.sp); Spacer(Modifier.height(12.dp)); Button(onClick = { if (title.isNotBlank()) onSubmit(category, title.trim(), details.trim()) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = V11Ink)) { Text("Post report", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(8.dp)); Text("Don't use reports to accuse or identify private people.", color = V11Muted, fontSize = 11.sp) } }
@Composable private fun V11Place(p: MapPlace, canSave: Boolean, onSave: () -> Unit) { Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 32.dp)) { Text(p.title, fontSize = 25.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(5.dp)); Text(p.subtitle.ifBlank { p.source }, color = V11Muted); Spacer(Modifier.height(10.dp)); Surface(shape = RoundedCornerShape(99.dp), color = V11Soft) { Text(p.source, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(18.dp)); Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = V11Ink)) { Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(if (canSave) "Save place" else "Sign in to save", fontWeight = FontWeight.Bold) } } }

@Composable
private fun V11Map(center: LatLng, zoom: Double, markers: List<MapPlace>, onReady: (MapLibreMap) -> Unit, onIdle: (Double, Double, Double) -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle; val view = remember { MapView(context).apply { onCreate(Bundle()) } }; var map by remember { mutableStateOf<MapLibreMap?>(null) }
    DisposableEffect(lifecycle, view) { val o = LifecycleEventObserver { _, e -> when (e) { Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume(); Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop(); Lifecycle.Event.ON_DESTROY -> view.onDestroy(); else -> Unit } }; lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) } }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) { v -> if (map == null) v.getMapAsync { m -> map = m; m.uiSettings.isLogoEnabled = false; m.uiSettings.isAttributionEnabled = false; m.cameraPosition = CameraPosition.Builder().target(center).zoom(zoom).build(); m.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")); m.addOnCameraIdleListener { m.cameraPosition.target?.let { c -> onIdle(c.latitude, c.longitude, m.cameraPosition.zoom) } }; onReady(m) } }
    LaunchedEffect(markers, map) { map?.let { m -> m.clear(); markers.take(140).forEach { p -> m.addMarker(MarkerOptions().position(LatLng(p.lat, p.lon)).title(p.title).snippet(p.source)) } } }
}

@Suppress("MissingPermission")
private fun v11CurrentLocation(context: android.content.Context, fine: Boolean, result: (Double, Double) -> Unit) { val manager = context.getSystemService(LocationManager::class.java); val providers = buildList { if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER); if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER); if (isEmpty()) add(LocationManager.PASSIVE_PROVIDER) }; fun attempt(i: Int) { if (i >= providers.size) return; manager.getCurrentLocation(providers[i], CancellationSignal(), context.mainExecutor) { l -> if (l != null) result(l.latitude, l.longitude) else attempt(i + 1) } }; attempt(0) }
private fun v11Distance(a: Double, b: Double, c: Double, d: Double): Double { val r = 6371.0; val x = Math.toRadians(c - a); val y = Math.toRadians(d - b); val q = sin(x / 2).pow(2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(y / 2).pow(2); return r * 2 * atan2(sqrt(q), sqrt(1 - q)) }
