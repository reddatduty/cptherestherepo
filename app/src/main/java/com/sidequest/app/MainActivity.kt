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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
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

private val Ink = Color(0xFF111217)
private val Muted = Color(0xFF737985)
private val SurfaceSoft = Color(0xFFF6F7F9)
private val Accent = Color(0xFF6E5BFF)
private val Warning = Color(0xFFFFB84A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val firebaseReady = FirebaseService.initialize(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    background = SurfaceSoft,
                    surface = Color.White
                )
            ) {
                SidequestApp(firebaseReady)
            }
        }
    }
}

@Composable
private fun SidequestApp(firebaseReady:Boolean) {
    var user by remember { mutableStateOf(FirebaseService.currentUser()) }
    MapScreen(firebaseReady=firebaseReady,user=user,onUser={user=it})
}

@Composable
private fun MapScreen(firebaseReady:Boolean,user:FirebaseUser?,onUser:(FirebaseUser?)->Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(LatLng(45.9432,24.9668)) }
    var zoom by remember { mutableDoubleStateOf(6.1) }
    var placeName by remember { mutableStateOf("Romania") }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var feed by remember { mutableStateOf(DiscoverFeed()) }
    var reports by remember { mutableStateOf<List<CommunityReport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var feedOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MapPlace?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf("Liberty") }

    val communityMarkers = remember(reports) {
        reports.map {
            MapPlace(
                id="report-${it.id}",
                title=it.title,
                subtitle="Community report · ${it.category}",
                lat=it.lat,
                lon=it.lon,
                image=null,
                source="Community",
                kind=it.category
            )
        }
    }
    val markers = remember(feed,reports) { feed.places + feed.wikiPlaces + communityMarkers }

    fun refresh(openFeed:Boolean=false) {
        loading = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    Api.discover(center.latitude,center.longitude,placeName,12,24)
                }
            }
            result.onSuccess { feed = it }
            if(firebaseReady) {
                runCatching { FirebaseService.loadApprovedReports() }.onSuccess { reports = it }
            }
            loading = false
            if(openFeed) feedOpen = true
        }
    }

    LaunchedEffect(Unit) { refresh(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if(fine || coarse) {
            getCurrentLocation(context,fine) { lat,lon ->
                center = LatLng(lat,lon)
                zoom = 14.5
                placeName = "Current area"
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center,zoom))
                refresh(false)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFDDE7F0))) {
        NativeMap(
            center=center,
            zoom=zoom,
            markers=markers,
            styleName=mapStyle,
            onMapReady={ m ->
                map=m
                m.setOnMarkerClickListener { marker ->
                    selected = markers.firstOrNull { it.title == marker.title }
                    selected != null
                }
            },
            onCameraIdle={ lat,lon,z -> center=LatLng(lat,lon);zoom=z }
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+10.dp))
            Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(
                    modifier=Modifier.weight(1f).shadow(12.dp,RoundedCornerShape(24.dp)),
                    shape=RoundedCornerShape(24.dp),
                    color=Color.White.copy(.92f)
                ) {
                    TextField(
                        value=query,
                        onValueChange={query=it},
                        modifier=Modifier.fillMaxWidth(),
                        placeholder={Text("Search a city or place",color=Muted)},
                        leadingIcon={Icon(Icons.Rounded.Search,null)},
                        trailingIcon={
                            if(query.isNotEmpty()) IconButton(onClick={query="";searchResults=emptyList()}) {
                                Icon(Icons.Rounded.Close,null)
                            }
                        },
                        singleLine=true,
                        colors=TextFieldDefaults.colors(
                            focusedContainerColor=Color.Transparent,
                            unfocusedContainerColor=Color.Transparent,
                            focusedIndicatorColor=Color.Transparent,
                            unfocusedIndicatorColor=Color.Transparent
                        ),
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                        keyboardActions=KeyboardActions(onSearch={
                            scope.launch {
                                searchResults = withContext(Dispatchers.IO) { Api.searchPlaces(query) }
                            }
                        })
                    )
                }
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Rounded.Person) { accountOpen=true }
            }

            AnimatedVisibility(searchResults.isNotEmpty()) {
                Surface(
                    Modifier.padding(horizontal=14.dp,vertical=8.dp).fillMaxWidth(),
                    shape=RoundedCornerShape(20.dp),
                    color=Color.White.copy(.97f),
                    shadowElevation=10.dp
                ) {
                    Column(Modifier.padding(6.dp)) {
                        searchResults.take(5).forEach { result ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    center=LatLng(result.lat,result.lon)
                                    zoom=13.5
                                    placeName=result.name
                                    query=result.name
                                    searchResults=emptyList()
                                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center,zoom))
                                    refresh(false)
                                }.padding(12.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LocationOn,null,tint=Accent)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(result.name,fontWeight=FontWeight.Bold)
                                    if(result.subtitle.isNotBlank()) Text(result.subtitle,color=Muted,fontSize=12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }

        Surface(
            Modifier.align(Alignment.TopStart)
                .padding(start=14.dp,top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+76.dp),
            shape=RoundedCornerShape(99.dp),
            color=Color.White.copy(.88f),
            shadowElevation=7.dp
        ) {
            Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("sidequest!",fontWeight=FontWeight.Black,fontSize=12.sp)
                Spacer(Modifier.width(8.dp))
                Text("${markers.size} nearby",color=Muted,fontSize=11.sp)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            CircleButton(Icons.Rounded.MyLocation) {
                val fine = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
                if(!fine && !coarse) {
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                } else {
                    getCurrentLocation(context,fine) { lat,lon ->
                        center=LatLng(lat,lon);zoom=14.5;placeName="Current area"
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center,zoom));refresh(false)
                    }
                }
            }
            CircleButton(Icons.Rounded.Refresh) { refresh(false) }
            CircleButton(Icons.Rounded.Layers) {
                mapStyle = when(mapStyle) { "Liberty"->"Bright";"Bright"->"Positron";else->"Liberty" }
            }
        }

        if(feed.signal.mentions>0) {
            Surface(
                Modifier.align(Alignment.TopCenter)
                    .padding(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+124.dp)
                    .clickable { feedOpen=true },
                shape=RoundedCornerShape(99.dp),
                color=Color(0xFFFDF1D1),
                shadowElevation=7.dp
            ) {
                Row(Modifier.padding(horizontal=13.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Warning))
                    Spacer(Modifier.width(7.dp))
                    Text("${feed.signal.mentions} unusual mentions · ${feed.signal.window}",fontSize=11.sp,fontWeight=FontWeight.Bold)
                }
            }
        }

        if(loading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(),color=Accent)

        selected?.let { place ->
            PlacePreview(
                place,
                Modifier.align(Alignment.BottomCenter).padding(start=14.dp,end=14.dp,bottom=94.dp),
                onClose={selected=null},
                onSave={
                    if(user==null) accountOpen=true else scope.launch { runCatching { FirebaseService.savePlace(place) } }
                }
            )
        }

        BottomDock(
            Modifier.align(Alignment.BottomCenter),
            onFeed={refresh(true)},
            onAdd={reportOpen=true},
            onAccount={accountOpen=true}
        )

        AnimatedVisibility(feedOpen) {
            FeedPanel(
                feed=feed,
                reports=reports,
                placeName=placeName,
                onClose={feedOpen=false},
                onPlace={p->selected=p;feedOpen=false;map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat,p.lon),15.0))},
                onArticle={url->runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
            )
        }

        AnimatedVisibility(accountOpen) {
            AccountPanel(
                firebaseReady=firebaseReady,
                user=user,
                onClose={accountOpen=false},
                onUser=onUser
            )
        }

        AnimatedVisibility(reportOpen) {
            ReportPanel(
                firebaseReady=firebaseReady,
                user=user,
                center=center,
                areaName=placeName,
                onClose={reportOpen=false},
                onNeedAccount={reportOpen=false;accountOpen=true},
                onSubmitted={reportOpen=false;refresh(false)}
            )
        }
    }
}

@Composable
private fun NativeMap(
    center:LatLng,
    zoom:Double,
    markers:List<MapPlace>,
    styleName:String,
    onMapReady:(MapLibreMap)->Unit,
    onCameraIdle:(Double,Double,Double)->Unit
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val view=remember { MapView(context).apply { onCreate(Bundle()) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(lifecycle,view) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_START->view.onStart()
                Lifecycle.Event.ON_RESUME->view.onResume()
                Lifecycle.Event.ON_PAUSE->view.onPause()
                Lifecycle.Event.ON_STOP->view.onStop()
                Lifecycle.Event.ON_DESTROY->view.onDestroy()
                else->Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(factory={view},modifier=Modifier.fillMaxSize()) { mapView ->
        if(map==null) {
            mapView.getMapAsync { m ->
                map=m
                m.uiSettings.isLogoEnabled=false
                m.cameraPosition=CameraPosition.Builder().target(center).zoom(zoom).build()
                m.setStyle(Style.Builder().fromUri(styleUri(styleName)))
                m.addOnCameraIdleListener {
                    m.cameraPosition.target?.let { c -> onCameraIdle(c.latitude,c.longitude,m.cameraPosition.zoom) }
                }
                onMapReady(m)
            }
        }
    }

    LaunchedEffect(markers,map) {
        map?.let { m ->
            m.clear()
            markers.take(140).forEach { p ->
                m.addMarker(MarkerOptions().position(LatLng(p.lat,p.lon)).title(p.title).snippet(p.source))
            }
        }
    }

    LaunchedEffect(styleName,map) {
        map?.setStyle(Style.Builder().fromUri(styleUri(styleName)))
    }
}

private fun styleUri(style:String)=when(style) {
    "Bright"->"https://tiles.openfreemap.org/styles/bright"
    "Positron"->"https://tiles.openfreemap.org/styles/positron"
    else->"https://tiles.openfreemap.org/styles/liberty"
}

@Composable
private fun CircleButton(icon:ImageVector,onClick:()->Unit) {
    Surface(
        Modifier.size(50.dp).shadow(10.dp,CircleShape).clickable(onClick=onClick),
        shape=CircleShape,
        color=Color.White.copy(.92f)
    ) { Box(contentAlignment=Alignment.Center) { Icon(icon,null,tint=Ink) } }
}

@Composable
private fun BottomDock(modifier:Modifier,onFeed:()->Unit,onAdd:()->Unit,onAccount:()->Unit) {
    Surface(
        modifier.padding(start=18.dp,end=18.dp,bottom=14.dp).height(68.dp).widthIn(max=360.dp).shadow(18.dp,RoundedCornerShape(25.dp)),
        shape=RoundedCornerShape(25.dp),
        color=Ink
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            DockButton(Icons.Rounded.Explore,"Feed",onFeed)
            FloatingActionButton(
                onClick=onAdd,
                containerColor=Accent,
                contentColor=Color.White,
                modifier=Modifier.size(52.dp),
                shape=CircleShape
            ) { Icon(Icons.Rounded.Add,null) }
            DockButton(Icons.Rounded.Person,"Account",onAccount)
        }
    }
}

@Composable
private fun DockButton(icon:ImageVector,label:String,onClick:()->Unit) {
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick=onClick).padding(vertical=8.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        Icon(icon,null,tint=Color.White)
        Text(label,color=Color.White.copy(.72f),fontSize=10.sp)
    }
}

@Composable
private fun FeedPanel(feed:DiscoverFeed,reports:List<CommunityReport>,placeName:String,onClose:()->Unit,onPlace:(MapPlace)->Unit,onArticle:(String)->Unit) {
    OverlayPanel(onClose=onClose,height=.76f) {
        LazyColumn(contentPadding=PaddingValues(bottom=26.dp)) {
            item {
                Row(Modifier.padding(20.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(placeName,fontSize=27.sp,fontWeight=FontWeight.Black)
                        Text("live nearby data",color=Muted,fontSize=12.sp)
                    }
                    IconButton(onClick=onClose) { Icon(Icons.Rounded.Close,null) }
                }
            }
            if(feed.signal.mentions>0) item {
                Surface(Modifier.padding(horizontal=18.dp).fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=Color(0xFFFDF1D1)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${feed.signal.mentions} unusual-media mentions",fontWeight=FontWeight.Black)
                        Text(feed.signal.disclaimer,color=Muted,fontSize=11.sp)
                    }
                }
            }
            val places=feed.wikiPlaces+feed.places
            if(places.isNotEmpty()) {
                item { SectionTitle("PLACES","OpenStreetMap + Wikipedia") }
                item {
                    LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        items(places.take(18),key={it.id}) { p -> PlaceCard(p){onPlace(p)} }
                    }
                }
            }
            if(reports.isNotEmpty()) {
                item { SectionTitle("COMMUNITY REPORTS","Approved reports only") }
                items(reports.take(20),key={it.id}) { r ->
                    Column(Modifier.fillMaxWidth().clickable { onPlace(MapPlace("report-${r.id}",r.title,r.description,r.lat,r.lon,null,"Community",r.category)) }.padding(horizontal=20.dp,vertical=10.dp)) {
                        Text(r.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${r.category} · ${r.areaName}",color=Muted,fontSize=11.sp)
                    }
                }
            }
            if(feed.articles.isNotEmpty()) {
                item { SectionTitle("RECENT COVERAGE","Current articles for this area") }
                items(feed.articles.take(25)) { a -> NewsCard(a){a.url?.let(onArticle)} }
            }
            item {
                Text(
                    "Community reports are unverified until moderated. Do not use the app as an emergency service or confront people. For immediate danger, contact local emergency services.",
                    Modifier.padding(20.dp),color=Muted,fontSize=11.sp,lineHeight=16.sp
                )
            }
        }
    }
}

@Composable
private fun AccountPanel(firebaseReady:Boolean,user:FirebaseUser?,onClose:()->Unit,onUser:(FirebaseUser?)->Unit) {
    val scope=rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    OverlayPanel(onClose=onClose,height=.64f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Account",fontSize=27.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
                IconButton(onClick=onClose) { Icon(Icons.Rounded.Close,null) }
            }
            if(!firebaseReady) {
                Surface(shape=RoundedCornerShape(18.dp),color=Color(0xFFFFF0F0)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Firebase project not connected",fontWeight=FontWeight.Black)
                        Spacer(Modifier.height(5.dp))
                        Text("The app code is now Firebase-based, but this build still needs your Firebase project config before account creation can work.",color=Muted,fontSize=13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Upload google-services.json for com.sidequest.app and I can finish the live build.",color=Muted,fontSize=12.sp)
                return@Column
            }

            if(user!=null) {
                Surface(shape=RoundedCornerShape(20.dp),color=SurfaceSoft) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(user.email ?: "Signed in",fontWeight=FontWeight.Black)
                        Text("Firebase Authentication",color=Muted,fontSize=12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick={FirebaseService.signOut();onUser(null)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
                    Text("Sign out")
                }
            } else {
                Text("Email + password",fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email,{email=it},modifier=Modifier.fillMaxWidth(),label={Text("Email")},singleLine=true,shape=RoundedCornerShape(18.dp))
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth(),label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),shape=RoundedCornerShape(18.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick={
                        if(email.isBlank() || password.length<6) { message="Use a valid email and at least 6 characters.";return@Button }
                        busy=true
                        scope.launch {
                            runCatching { FirebaseService.signIn(email,password) }
                                .onSuccess { onUser(it);message="Signed in." }
                                .onFailure { message=it.message ?: "Sign in failed." }
                            busy=false
                        }
                    },
                    modifier=Modifier.fillMaxWidth().height(52.dp),
                    enabled=!busy,
                    shape=RoundedCornerShape(18.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Ink)
                ) { Text("Sign in",fontWeight=FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick={
                        if(email.isBlank() || password.length<6) { message="Use a valid email and at least 6 characters.";return@OutlinedButton }
                        busy=true
                        scope.launch {
                            runCatching { FirebaseService.signUp(email,password) }
                                .onSuccess { onUser(it);message="Account created." }
                                .onFailure { message=it.message ?: "Account creation failed." }
                            busy=false
                        }
                    },
                    modifier=Modifier.fillMaxWidth().height(52.dp),
                    enabled=!busy,
                    shape=RoundedCornerShape(18.dp)
                ) { Text("Create account",fontWeight=FontWeight.Bold) }
                message?.let { Spacer(Modifier.height(10.dp));Text(it,color=Muted,fontSize=12.sp) }
            }
        }
    }
}

@Composable
private fun ReportPanel(firebaseReady:Boolean,user:FirebaseUser?,center:LatLng,areaName:String,onClose:()->Unit,onNeedAccount:()->Unit,onSubmitted:()->Unit) {
    val scope=rememberCoroutineScope()
    var category by remember { mutableStateOf("safety") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    OverlayPanel(onClose=onClose,height=.68f) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Add report",fontSize=27.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
                IconButton(onClick=onClose) { Icon(Icons.Rounded.Close,null) }
            }
            if(!firebaseReady || user==null) {
                Text(if(!firebaseReady) "Firebase needs to be connected before reports can be posted." else "Sign in before posting a community report.",color=Muted)
                Spacer(Modifier.height(12.dp))
                if(user==null) Button(onClick=onNeedAccount,shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink)) { Text("Open account") }
                return@Column
            }
            Text("Category",fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                items(listOf("safety","incident","suspicious","paranormal","other")) { c ->
                    FilterChip(selected=category==c,onClick={category=c},label={Text(c)})
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("Short title")},shape=RoundedCornerShape(18.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(details,{details=it},Modifier.fillMaxWidth().height(130.dp),label={Text("What did you observe?")},shape=RoundedCornerShape(18.dp))
            Spacer(Modifier.height(10.dp))
            Text("Reports are submitted for moderation. Do not include private people's names, phone numbers, home addresses, or accusations presented as fact.",color=Muted,fontSize=11.sp,lineHeight=16.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick={
                    if(title.isBlank() || details.isBlank()) { message="Add a title and details.";return@Button }
                    busy=true
                    scope.launch {
                        runCatching {
                            FirebaseService.submitReport(
                                CommunityReport(category=category,title=title,description=details,lat=center.latitude,lon=center.longitude,areaName=areaName)
                            )
                        }.onSuccess { onSubmitted() }.onFailure { message=it.message ?: "Could not submit report." }
                        busy=false
                    }
                },
                enabled=!busy,
                modifier=Modifier.fillMaxWidth().height(52.dp),
                shape=RoundedCornerShape(18.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Ink)
            ) { Text("Submit for review",fontWeight=FontWeight.Bold) }
            message?.let { Spacer(Modifier.height(10.dp));Text(it,color=Muted,fontSize=12.sp) }
        }
    }
}

@Composable
private fun OverlayPanel(onClose:()->Unit,height:Float,content:@Composable ()->Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.22f)).clickable(onClick=onClose)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(height).clickable { },
            shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),
            color=Color(0xFFFAFAFB),
            shadowElevation=20.dp
        ) { content() }
    }
}

@Composable
private fun SectionTitle(title:String,subtitle:String) {
    Column(Modifier.padding(start=20.dp,end=20.dp,top=22.dp,bottom=10.dp)) {
        Text(title,fontSize=12.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp,color=Muted)
        Text(subtitle,fontSize=11.sp,color=Muted)
    }
}

@Composable
private fun PlaceCard(place:MapPlace,onClick:()->Unit) {
    Surface(Modifier.width(220.dp).height(170.dp).clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=3.dp) {
        Box {
            if(place.image!=null) AsyncImage(place.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFD8E6F3),Color(0xFFEAE6F8)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,Color(0xC8111217)))))
            Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                Text(place.title,color=Color.White,fontWeight=FontWeight.Black,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(place.source,color=Color.White.copy(.78f),fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun NewsCard(article:NewsItem,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Surface(Modifier.size(82.dp),shape=RoundedCornerShape(18.dp),color=Color(0xFFE7E9ED)) {
            if(article.image!=null) AsyncImage(article.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.Article,null,tint=Muted) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(article.title,fontWeight=FontWeight.Bold,maxLines=3,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(article.domain,article.seenDate).joinToString(" · "),color=Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun PlacePreview(place:MapPlace,modifier:Modifier,onClose:()->Unit,onSave:()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=Color.White.copy(.96f),shadowElevation=14.dp) {
        Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp),shape=RoundedCornerShape(16.dp),color=Color(0xFFE7E9ED)) {
                if(place.image!=null) AsyncImage(place.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.LocationOn,null) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(place.title,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(place.source,color=Muted,fontSize=10.sp)
            }
            IconButton(onClick=onSave) { Icon(Icons.Rounded.BookmarkAdd,null,tint=Accent) }
            IconButton(onClick=onClose) { Icon(Icons.Rounded.Close,null) }
        }
    }
}

private fun getCurrentLocation(context:android.content.Context,highAccuracy:Boolean,result:(Double,Double)->Unit) {
    val manager=context.getSystemService(LocationManager::class.java)
    val provider=if(highAccuracy && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
    try {
        manager.getCurrentLocation(provider,CancellationSignal(),context.mainExecutor) { location ->
            if(location!=null) result(location.latitude,location.longitude)
        }
    } catch(_:SecurityException) { }
}
