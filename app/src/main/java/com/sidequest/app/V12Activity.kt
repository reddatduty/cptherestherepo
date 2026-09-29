package com.sidequest.app

import android.Manifest
import android.app.Activity
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

private val V12Ink = Color(0xFF0E1014)
private val V12Muted = Color(0xFF747B86)
private val V12Accent = Color(0xFF6557FF)
private val V12Soft = Color(0xFFF4F5F8)
private val V12Alert = Color(0xFFFF6B73)

class V12Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = V12Accent,
                    background = V12Soft,
                    surface = Color.White
                )
            ) {
                SidequestV12()
            }
        }
    }
}

@Composable
private fun SidequestV12() {
    val context = LocalContext.current
    var account by remember { mutableStateOf(FirebaseBackend.currentAccount(context)) }
    if (account == null) {
        AuthScene(onSignedIn = { account = it })
    } else {
        HomeScene(
            account = account!!,
            onSignedOut = {
                FirebaseBackend.signOut(context)
                account = null
            }
        )
    }
}

@Composable
private fun AuthScene(onSignedIn: (Account) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val configured = remember { FirebaseBackend.isConfigured(context) }
    val googleReady = remember(configured) { FirebaseBackend.googleClientId(context) != null }

    var creating by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFFCBD6DF))) {
        AuthMapBackdrop()
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.04f),
                        Color.Black.copy(alpha = 0.10f),
                        Color.Black.copy(alpha = 0.68f)
                    )
                )
            )
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(99.dp))
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(V12Ink), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("sidequest!", color = V12Ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = Color(0xFFFCFCFD),
            shadowElevation = 24.dp
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Text(
                    if (creating) "Create your Sidequest account" else "See what’s really around you",
                    color = V12Ink,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Live map, real public sources, community reports and places worth discovering.",
                    color = V12Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (!configured) {
                    Spacer(Modifier.height(14.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF2D8)) {
                        Text(
                            "Firebase isn’t linked to this APK yet. Add google-services.json to enable real accounts.",
                            Modifier.padding(13.dp),
                            color = Color(0xFF765100),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Rounded.Email, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(18.dp),
                    keyboardActions = KeyboardActions(onDone = {})
                )

                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = V12Alert, fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (!configured) {
                            note = "Firebase project is not connected yet."
                            return@Button
                        }
                        if (email.isBlank() || password.length < 6) {
                            note = "Enter a valid email and a password of at least 6 characters."
                            return@Button
                        }
                        busy = true
                        note = null
                        scope.launch {
                            val result = runCatching {
                                if (creating) FirebaseBackend.signUp(context, email.trim(), password)
                                else FirebaseBackend.signIn(context, email.trim(), password)
                            }
                            busy = false
                            result.onSuccess(onSignedIn).onFailure { note = it.localizedMessage ?: "Authentication failed." }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = V12Ink)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (creating) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (!googleReady || activity == null) {
                            note = "Google sign-in will activate after Firebase is linked and Google is enabled."
                            return@OutlinedButton
                        }
                        busy = true
                        note = null
                        scope.launch {
                            val result = runCatching { FirebaseBackend.signInWithGoogle(activity) }
                            busy = false
                            result.onSuccess(onSignedIn).onFailure { note = it.localizedMessage ?: "Google sign-in failed." }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("G", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", fontWeight = FontWeight.Bold)
                }

                if (!creating) {
                    TextButton(
                        onClick = {
                            if (!configured || email.isBlank()) {
                                note = "Enter your email first."
                            } else {
                                scope.launch {
                                    val r = runCatching { FirebaseBackend.sendPasswordReset(context, email.trim()) }
                                    note = if (r.isSuccess) "Password reset email sent." else r.exceptionOrNull()?.localizedMessage
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Forgot password?", color = V12Muted) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (creating) "Already have an account?" else "New to Sidequest?", color = V12Muted, fontSize = 12.sp)
                    TextButton(onClick = { creating = !creating; note = null }) {
                        Text(if (creating) "Sign in" else "Create account", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScene(account: Account, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var center by remember { mutableStateOf(LatLng(45.9432, 24.9668)) }
    var zoom by remember { mutableDoubleStateOf(6.0) }
    var placeName by remember { mutableStateOf("Romania") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var feed by remember { mutableStateOf(DiscoverFeed()) }
    var reports by remember { mutableStateOf<List<CommunityReport>>(emptyList()) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var selected by remember { mutableStateOf<MapPlace?>(null) }
    var loading by remember { mutableStateOf(false) }
    var exploreOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var radiusKm by remember { mutableIntStateOf(12) }
    var windowHours by remember { mutableIntStateOf(24) }

    val reportPins = remember(reports, center, radiusKm) {
        reports.filter { distanceV12(center.latitude, center.longitude, it.lat, it.lon) <= radiusKm * 2.0 }
            .map {
                MapPlace(
                    id = "report-${it.id}",
                    title = it.title.ifBlank { "Community report" },
                    subtitle = it.details,
                    lat = it.lat,
                    lon = it.lon,
                    source = "Community · unverified",
                    kind = it.category
                )
            }
    }
    val markers = remember(feed, reportPins) { feed.wikiPlaces + feed.places + reportPins }

    fun refresh() {
        loading = true
        scope.launch {
            val publicData = runCatching {
                withContext(Dispatchers.IO) { Api.discover(center.latitude, center.longitude, placeName, radiusKm, windowHours) }
            }
            val community = runCatching {
                withContext(Dispatchers.IO) { FirebaseBackend.loadReports(context) }
            }
            publicData.onSuccess { feed = it }.onFailure { message = "Live sources could not refresh." }
            community.onSuccess { reports = it }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            currentLocationV12(context, fine) { lat, lon ->
                center = LatLng(lat, lon)
                zoom = 14.2
                placeName = "Current area"
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                refresh()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFD8E1E8))) {
        V12Map(
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
            onCameraIdle = { lat, lon, z -> center = LatLng(lat, lon); zoom = z }
        )

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = V12Accent,
                trackColor = Color.Transparent
            )
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.weight(1f).shadow(12.dp, RoundedCornerShape(27.dp)),
                    shape = RoundedCornerShape(27.dp),
                    color = Color.White.copy(alpha = 0.90f)
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it; if (it.isBlank()) results = emptyList() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search anywhere", color = V12Muted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton(onClick = { query = ""; results = emptyList() }) { Icon(Icons.Rounded.Close, null) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.trim().length >= 2) {
                                scope.launch {
                                    results = runCatching { withContext(Dispatchers.IO) { Api.searchPlaces(query.trim()) } }.getOrDefault(emptyList())
                                }
                            }
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                Spacer(Modifier.width(9.dp))
                GlassCircle(Icons.Rounded.Person) { profileOpen = true }
            }

            if (results.isNotEmpty()) {
                Surface(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 12.dp
                ) {
                    Column(Modifier.padding(6.dp)) {
                        results.take(5).forEach { result ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                    placeName = result.name
                                    center = LatLng(result.lat, result.lon)
                                    zoom = 13.7
                                    query = result.name
                                    results = emptyList()
                                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                                    refresh()
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LocationOn, null, tint = V12Accent)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(result.name, fontWeight = FontWeight.Bold, color = V12Ink)
                                    if (result.subtitle.isNotBlank()) Text(result.subtitle, color = V12Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        GlassCircle(
            icon = Icons.Rounded.MyLocation,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
        ) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            } else {
                currentLocationV12(context, fine) { lat, lon ->
                    center = LatLng(lat, lon)
                    zoom = 14.2
                    placeName = "Current area"
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                    refresh()
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 18.dp
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { exploreOpen = true }) {
                    Text(placeName, fontWeight = FontWeight.Black, fontSize = 16.sp, color = V12Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            feed.signal.mentions > 0 -> "${feed.signal.mentions} unusual media mentions"
                            feed.incidents.isNotEmpty() -> "${feed.incidents.size} recent incident articles"
                            else -> "Explore nearby places and activity"
                        },
                        color = V12Muted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Surface(
                    onClick = { exploreOpen = true },
                    shape = CircleShape,
                    color = V12Soft,
                    modifier = Modifier.size(46.dp)
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Explore, null, tint = V12Ink) } }
                Spacer(Modifier.width(7.dp))
                Surface(
                    onClick = { reportOpen = true },
                    shape = CircleShape,
                    color = V12Ink,
                    modifier = Modifier.size(50.dp)
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) } }
            }
        }

        selected?.let { place ->
            ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = Color.White) {
                PlaceSheetV12(place = place, onSave = {
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { FirebaseBackend.savePlace(context, place) } }
                        message = if (r.isSuccess) "Saved to your Firebase account." else r.exceptionOrNull()?.localizedMessage
                    }
                })
            }
        }

        message?.let { text ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = V12Ink
            ) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { message = null }, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
                }
            }
        }
    }

    if (exploreOpen) {
        ModalBottomSheet(onDismissRequest = { exploreOpen = false }, containerColor = Color(0xFFFAFAFC)) {
            ExploreSheetV12(
                placeName = placeName,
                feed = feed,
                reports = reports,
                windowHours = windowHours,
                radiusKm = radiusKm,
                onWindow = { windowHours = it; refresh() },
                onRadius = { radiusKm = it; refresh() },
                onArticle = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                onPlace = { p -> selected = p; exploreOpen = false }
            )
        }
    }

    if (profileOpen) {
        ModalBottomSheet(onDismissRequest = { profileOpen = false }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).padding(bottom = 28.dp)) {
                Text("Account", fontSize = 26.sp, fontWeight = FontWeight.Black, color = V12Ink)
                Spacer(Modifier.height(6.dp))
                Text(account.email, color = V12Muted)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { profileOpen = false; onSignedOut() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp)) {
                    Icon(Icons.Rounded.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }

    if (reportOpen) {
        ReportSheetV12(center = center, onClose = { reportOpen = false }) { category, title, details ->
            scope.launch {
                val r = runCatching {
                    withContext(Dispatchers.IO) { FirebaseBackend.createReport(context, category, title, details, center.latitude, center.longitude) }
                }
                if (r.isSuccess) {
                    reportOpen = false
                    message = "Report posted with an approximate location."
                    refresh()
                } else {
                    message = r.exceptionOrNull()?.localizedMessage ?: "Could not post report."
                }
            }
        }
    }
}

@Composable
private fun AuthMapBackdrop() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember { MapView(context).apply { onCreate(Bundle()) } }
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
        if (v.tag != "ready") {
            v.tag = "ready"
            v.getMapAsync { map ->
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.setAllGesturesEnabled(false)
                map.cameraPosition = CameraPosition.Builder().target(LatLng(47.0, 12.0)).zoom(3.1).build()
                map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"))
            }
        }
    }
}

@Composable
private fun V12Map(
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
            markers.take(140).forEach { place ->
                m.addMarker(MarkerOptions().position(LatLng(place.lat, place.lon)).title(place.title).snippet(place.source))
            }
        }
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(50.dp).shadow(10.dp, CircleShape),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.90f)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = V12Ink) }
    }
}

@Composable
private fun ExploreSheetV12(
    placeName: String,
    feed: DiscoverFeed,
    reports: List<CommunityReport>,
    windowHours: Int,
    radiusKm: Int,
    onWindow: (Int) -> Unit,
    onRadius: (Int) -> Unit,
    onArticle: (String) -> Unit,
    onPlace: (MapPlace) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(placeName, fontSize = 28.sp, fontWeight = FontWeight.Black, color = V12Ink)
                Text("Live public sources + community reports", color = V12Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(6, 24, 168)) { h ->
                        FilterChip(selected = windowHours == h, onClick = { onWindow(h) }, label = { Text(if (h == 168) "7 days" else "$h h") })
                    }
                    items(listOf(5, 12, 25)) { km ->
                        FilterChip(selected = radiusKm == km, onClick = { onRadius(km) }, label = { Text("$km km") })
                    }
                }
            }
        }

        if (feed.signal.mentions > 0) {
            item {
                Surface(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF2D8)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF8B5C00))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${feed.signal.mentions} unusual media mentions", fontWeight = FontWeight.Black, color = V12Ink)
                            Text("Keyword matches only — not proof of paranormal activity.", color = V12Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        val places = feed.wikiPlaces + feed.places
        if (places.isNotEmpty()) {
            item { SectionTitleV12("Places worth opening on the map", "Wikipedia + OpenStreetMap") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(places.take(20), key = { it.id }) { place ->
                        PlaceCardV12(place = place, onClick = { onPlace(place) })
                    }
                }
            }
        }

        if (feed.incidents.isNotEmpty()) {
            item { SectionTitleV12("Recent incident coverage", "Articles matched to this area") }
            items(feed.incidents.take(20)) { article -> NewsRowV12(article) { article.url?.let(onArticle) } }
        }

        if (feed.unusual.isNotEmpty()) {
            item { SectionTitleV12("Unusual mentions", "Recent keyword matches from public coverage") }
            items(feed.unusual.take(20)) { article -> NewsRowV12(article) { article.url?.let(onArticle) } }
        }

        if (reports.isNotEmpty()) {
            item { SectionTitleV12("Community reports", "Unverified reports with approximate locations") }
            items(reports.take(20), key = { it.id }) { report ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(10.dp).padding(top = 4.dp).clip(CircleShape).background(if (report.category == "incident") V12Alert else V12Accent))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(report.title, fontWeight = FontWeight.Bold, color = V12Ink)
                        if (report.details.isNotBlank()) Text(report.details, color = V12Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(report.category, color = V12Muted, fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Text(
                "Community reports are not verified. Do not enter restricted/private locations or confront people. For immediate danger, contact local emergency services.",
                Modifier.padding(20.dp),
                color = V12Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PlaceSheetV12(place: MapPlace, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        if (place.image != null) {
            AsyncImage(
                model = place.image,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(place.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = V12Ink)
        Spacer(Modifier.height(4.dp))
        Text(place.subtitle, color = V12Muted)
        Spacer(Modifier.height(6.dp))
        Text(place.source, color = V12Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = V12Ink)) {
            Icon(Icons.Rounded.BookmarkAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Save place", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportSheetV12(
    center: LatLng,
    onClose: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var category by remember { mutableStateOf("suspicious") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Add a report", fontSize = 26.sp, fontWeight = FontWeight.Black, color = V12Ink)
            Text("The public map position is deliberately approximate.", color = V12Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("incident", "suspicious", "paranormal", "other")) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.replaceFirstChar { it.uppercase() }) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text("What happened?") }, singleLine = true, shape = RoundedCornerShape(17.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(details, { details = it.take(500) }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("Details") }, shape = RoundedCornerShape(17.dp))
            Spacer(Modifier.height(8.dp))
            Text("Do not include private people’s names, phone numbers, exact home addresses, or instructions to enter restricted places.", color = V12Muted, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (title.isNotBlank()) onSubmit(category, title.trim(), details.trim()) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V12Ink)
            ) { Text("Post report", fontWeight = FontWeight.Bold) }
            Text("Approx. center: ${"%.3f".format(center.latitude)}, ${"%.3f".format(center.longitude)}", Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), color = V12Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionTitleV12(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 9.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = V12Ink)
        Text(subtitle, fontSize = 11.sp, color = V12Muted)
    }
}

@Composable
private fun PlaceCardV12(place: MapPlace, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(230.dp).height(166.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Box {
            if (place.image != null) {
                AsyncImage(place.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE5E9F1), Color(0xFFD8DEF5)))))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                Text(place.title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(place.source, color = Color.White.copy(alpha = 0.74f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NewsRowV12(article: NewsItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(82.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFE8EAF0)) {
            if (article.image != null) AsyncImage(article.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Article, null, tint = V12Muted) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(article.title, fontWeight = FontWeight.Bold, color = V12Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(article.domain, article.seenDate).joinToString(" · "), color = V12Muted, fontSize = 10.sp)
        }
    }
}

private fun currentLocationV12(context: android.content.Context, highAccuracy: Boolean, result: (Double, Double) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = if (highAccuracy && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
    try {
        manager.getCurrentLocation(provider, CancellationSignal(), context.mainExecutor) { location ->
            if (location != null) result(location.latitude, location.longitude)
        }
    } catch (_: SecurityException) {
    }
}

private fun distanceV12(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earth = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return earth * 2 * atan2(sqrt(a), sqrt(1 - a))
}
