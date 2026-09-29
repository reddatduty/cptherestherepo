package com.sidequest.app

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

private val HomeInk = Color(0xFF111216)
private val HomeMuted = Color(0xFF747984)
private val HomeAccent = Color(0xFF6C5CE7)
private val HomeBg = Color(0xFFF6F7F9)
private val HomeWarning = Color(0xFFF4A340)

private enum class HomeSheet { NONE, NEARBY, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        val store = SessionStore(this)
        val session = store.load()
        if (session == null) {
            startActivity(Intent(this, EntryActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = HomeAccent,
                    background = HomeBg,
                    surface = Color.White
                )
            ) {
                SidequestHome(session = session, onLogout = { store.clear() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidequestHome(session: Session, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var center by remember { mutableStateOf(LatLng(45.9432, 24.9668)) }
    var zoom by remember { mutableDoubleStateOf(6.0) }
    var placeName by remember { mutableStateOf("Romania") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var feed by remember { mutableStateOf(DiscoverFeed()) }
    var loading by remember { mutableStateOf(false) }
    var radius by remember { mutableIntStateOf(12) }
    var windowHours by remember { mutableIntStateOf(24) }
    var sheet by remember { mutableStateOf(HomeSheet.NONE) }
    var selected by remember { mutableStateOf<MapPlace?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val markers = remember(feed) { feed.places + feed.wikiPlaces }

    fun refreshAt(
        lat: Double = center.latitude,
        lon: Double = center.longitude,
        name: String = placeName,
        openSheet: Boolean = false
    ) {
        loading = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Api.discover(lat, lon, name, radius, windowHours, session)
                }
            }.onSuccess {
                feed = it
                if (openSheet) sheet = HomeSheet.NEARBY
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshAt() }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            getCurrentLocation(context, fine) { lat, lon ->
                center = LatLng(lat, lon)
                zoom = 14.2
                placeName = "Current area"
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                refreshAt(lat, lon, "Current area")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HomeBg)) {
        NativeMap(
            center = center,
            zoom = zoom,
            markers = markers,
            onMapReady = { map = it },
            onCameraIdle = { lat, lon, z ->
                center = LatLng(lat, lon)
                zoom = z
            },
            onMarker = { title -> selected = markers.firstOrNull { it.title == title } }
        )

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = HomeAccent
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(9.dp, RoundedCornerShape(23.dp)),
                    shape = RoundedCornerShape(23.dp),
                    color = Color.White.copy(alpha = 0.96f)
                ) {
                    TextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.isBlank()) results = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search a city or place", color = HomeMuted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
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
                            if (query.length >= 2) {
                                scope.launch {
                                    results = runCatching {
                                        withContext(Dispatchers.IO) { Api.searchPlaces(query.trim()) }
                                    }.getOrDefault(emptyList())
                                }
                            }
                        })
                    )
                }

                Spacer(Modifier.width(9.dp))
                RoundMapButton(Icons.Rounded.Person) { sheet = HomeSheet.SETTINGS }
            }

            if (results.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    shadowElevation = 10.dp
                ) {
                    Column(Modifier.padding(6.dp)) {
                        results.take(5).forEach { result ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(15.dp))
                                    .clickable {
                                        query = result.name
                                        results = emptyList()
                                        placeName = result.name
                                        center = LatLng(result.lat, result.lon)
                                        zoom = 13.5
                                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                                        refreshAt(result.lat, result.lon, result.name)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LocationOn, null, tint = HomeAccent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(result.name, fontWeight = FontWeight.Bold, color = HomeInk)
                                    if (result.subtitle.isNotBlank()) {
                                        Text(result.subtitle, color = HomeMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RoundMapButton(Icons.Rounded.MyLocation) {
                val fine = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!fine && !coarse) {
                    locationPermission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                } else {
                    getCurrentLocation(context, fine) { lat, lon ->
                        center = LatLng(lat, lon)
                        zoom = 14.2
                        placeName = "Current area"
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                        refreshAt(lat, lon, "Current area")
                    }
                }
            }
            RoundMapButton(Icons.Rounded.Refresh) { refreshAt() }
            RoundMapButton(Icons.Rounded.Tune) { sheet = HomeSheet.SETTINGS }
        }

        selected?.let { place ->
            PlacePeek(
                place = place,
                canSave = session.userId != "guest",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 104.dp),
                onClose = { selected = null },
                onSave = {
                    if (session.userId != "guest") {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { Api.savePlace(session, place) } }
                        }
                    }
                }
            )
        }

        Text(
            "© OpenStreetMap contributors · OpenFreeMap",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
                .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 9.sp,
            color = HomeMuted
        )

        HomeBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onMap = { sheet = HomeSheet.NONE; selected = null },
            onNearby = { sheet = HomeSheet.NEARBY },
            onProfile = { sheet = HomeSheet.SETTINGS }
        )
    }

    if (sheet == HomeSheet.NEARBY) {
        ModalBottomSheet(
            onDismissRequest = { sheet = HomeSheet.NONE },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NearbySheet(
                feed = feed,
                placeName = placeName,
                loading = loading,
                onRefresh = { refreshAt(openSheet = true) },
                onPlace = { place ->
                    selected = place
                    sheet = HomeSheet.NONE
                    center = LatLng(place.lat, place.lon)
                    zoom = 15.0
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
                },
                onArticle = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            )
        }
    }

    if (sheet == HomeSheet.SETTINGS) {
        ModalBottomSheet(
            onDismissRequest = { sheet = HomeSheet.NONE },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            SettingsSheet(
                session = session,
                radius = radius,
                windowHours = windowHours,
                onRadius = { radius = it },
                onWindow = { windowHours = it },
                onApply = {
                    sheet = HomeSheet.NONE
                    refreshAt()
                },
                onLogout = onLogout
            )
        }
    }
}

@Composable
private fun RoundMapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp),
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 7.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = HomeInk, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun HomeBottomBar(
    modifier: Modifier,
    onMap: () -> Unit,
    onNearby: () -> Unit,
    onProfile: () -> Unit
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = Color.White.copy(alpha = 0.97f),
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeNavItem(Icons.Rounded.Map, "Map", true, onMap)
            HomeNavItem(Icons.Rounded.Explore, "Nearby", false, onNearby)
            HomeNavItem(Icons.Rounded.Person, "Account", false, onProfile)
        }
    }
}

@Composable
private fun HomeNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (active) HomeAccent else HomeMuted, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (active) HomeAccent else HomeMuted)
    }
}

@Composable
private fun PlacePeek(
    place: MapPlace,
    canSave: Boolean,
    modifier: Modifier,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 14.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(74.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFE9EAF0)
            ) {
                if (place.image != null) {
                    AsyncImage(place.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFFDDE3FF), Color(0xFFEDE5FF)))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Place, null, tint = HomeAccent)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.title, fontWeight = FontWeight.Black, color = HomeInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(place.source, color = HomeMuted, fontSize = 11.sp)
            }
            if (canSave) {
                IconButton(onClick = onSave) { Icon(Icons.Rounded.BookmarkBorder, null, tint = HomeAccent) }
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = HomeMuted) }
        }
    }
}

@Composable
private fun NearbySheet(
    feed: DiscoverFeed,
    placeName: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onPlace: (MapPlace) -> Unit,
    onArticle: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 690.dp).padding(bottom = 28.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Nearby", fontSize = 27.sp, fontWeight = FontWeight.Black, color = HomeInk)
                    Text(placeName, color = HomeMuted, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null)
                }
            }
        }

        if (feed.signal.mentions > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF5DF)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(36.dp), shape = CircleShape, color = HomeWarning.copy(alpha = 0.22f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF9A6400), modifier = Modifier.size(19.dp))
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text(
                                "${feed.signal.mentions} unusual media mentions · ${feed.signal.window}",
                                fontWeight = FontWeight.Bold,
                                color = HomeInk,
                                fontSize = 13.sp
                            )
                            Text(feed.signal.disclaimer, color = HomeMuted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }

        val places = feed.places + feed.wikiPlaces
        if (places.isNotEmpty()) {
            item { SheetTitle("PLACES", "Documented nearby places") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(places.take(16), key = { it.id }) { place ->
                        NearbyPlaceCard(place, onClick = { onPlace(place) })
                    }
                }
            }
        }

        if (feed.incidents.isNotEmpty()) {
            item { SheetTitle("CURRENT COVERAGE", "Recent incident-related news mentions") }
            items(feed.incidents.take(8)) { article ->
                NewsRow(article) { article.url?.let(onArticle) }
            }
        } else if (feed.articles.isNotEmpty()) {
            item { SheetTitle("LOCAL COVERAGE", "Recent articles mentioning this area") }
            items(feed.articles.take(8)) { article ->
                NewsRow(article) { article.url?.let(onArticle) }
            }
        }

        if (places.isEmpty() && feed.articles.isEmpty() && !loading) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.ExploreOff, null, tint = HomeMuted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing useful found in this area yet.", color = HomeMuted)
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(kicker: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 9.dp)) {
        Text(kicker, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Black, color = HomeMuted)
        Text(subtitle, fontSize = 13.sp, color = HomeMuted)
    }
}

@Composable
private fun NearbyPlaceCard(place: MapPlace, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(210.dp).height(156.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFE9EAF0)
    ) {
        Box {
            if (place.image != null) {
                AsyncImage(place.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFFDDE4FF), Color(0xFFECE4FF)))
                    )
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xC8000000)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                Text(place.title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(place.source, color = Color.White.copy(alpha = 0.80f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NewsRow(article: NewsItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(76.dp), shape = RoundedCornerShape(17.dp), color = Color(0xFFE9EAF0)) {
            if (article.image != null) {
                AsyncImage(article.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Article, null, tint = HomeMuted)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(article.title, fontWeight = FontWeight.Bold, color = HomeInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(article.domain, article.seenDate).joinToString(" · "),
                color = HomeMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = HomeMuted)
    }
}

@Composable
private fun SettingsSheet(
    session: Session,
    radius: Int,
    windowHours: Int,
    onRadius: (Int) -> Unit,
    onWindow: (Int) -> Unit,
    onApply: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Black, color = HomeInk)
        Spacer(Modifier.height(4.dp))
        Text(
            if (session.userId == "guest") "Guest session" else session.email,
            color = HomeMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(22.dp))
        Text("Area radius", fontWeight = FontWeight.Bold, color = HomeInk)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 12, 25).forEach { value ->
                FilterChip(
                    selected = radius == value,
                    onClick = { onRadius(value) },
                    label = { Text("$value km") }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("News window", fontWeight = FontWeight.Bold, color = HomeInk)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 24, 168).forEach { value ->
                val label = if (value == 168) "7 days" else "$value h"
                FilterChip(
                    selected = windowHours == value,
                    onClick = { onWindow(value) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HomeInk)
        ) {
            Text("Apply and refresh", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(17.dp)
        ) {
            Icon(if (session.userId == "guest") Icons.Rounded.Login else Icons.Rounded.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text(if (session.userId == "guest") "Sign in" else "Sign out")
        }
    }
}

@Composable
private fun NativeMap(
    center: LatLng,
    zoom: Double,
    markers: List<MapPlace>,
    onMapReady: (MapLibreMap) -> Unit,
    onCameraIdle: (Double, Double, Double) -> Unit,
    onMarker: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var internalMap by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onDestroy() }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            internalMap = map
            map.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
            }
            map.cameraPosition = CameraPosition.Builder().target(center).zoom(zoom).build()
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) {
                map.clear()
                markers.forEach { place ->
                    map.addMarker(MarkerOptions().position(LatLng(place.lat, place.lon)).title(place.title))
                }
            }
            map.setOnMarkerClickListener { marker ->
                marker.title?.let(onMarker)
                true
            }
            map.addOnCameraIdleListener {
                val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
                onCameraIdle(target.latitude, target.longitude, map.cameraPosition.zoom)
            }
            onMapReady(map)
        }
    }

    LaunchedEffect(markers, internalMap) {
        internalMap?.let { map ->
            map.clear()
            markers.forEach { place ->
                map.addMarker(MarkerOptions().position(LatLng(place.lat, place.lon)).title(place.title))
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun getCurrentLocation(
    context: Context,
    fine: Boolean,
    onLocation: (Double, Double) -> Unit
) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val preferred = if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        LocationManager.GPS_PROVIDER
    } else {
        LocationManager.NETWORK_PROVIDER
    }

    try {
        manager.getCurrentLocation(
            preferred,
            CancellationSignal(),
            context.mainExecutor
        ) { location ->
            if (location != null) onLocation(location.latitude, location.longitude)
        }
    } catch (_: SecurityException) {
    } catch (_: IllegalArgumentException) {
    }
}
