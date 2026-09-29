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
import androidx.compose.ui.graphics.Brush
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

private val Ink=Color(0xFF11141B)
private val Muted=Color(0xFF727B8B)
private val Violet=Color(0xFF775BFF)
private val Pink=Color(0xFFD75CFF)
private val Danger=Color(0xFFFF6C76)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent{ MaterialTheme(colorScheme=lightColorScheme(primary=Violet,background=Color(0xFFF6F7FB),surface=Color.White)){ SidequestApp() } }
    }
}

private enum class AppScreen{INTRO,AUTH,MAP}

@Composable
private fun SidequestApp(){
    val context=LocalContext.current
    val store=remember{SessionStore(context)}
    var session by remember{ mutableStateOf(store.load()) }
    val introDone=context.getSharedPreferences("sidequest",0).getBoolean("intro",false)
    var screen by remember{ mutableStateOf(if(session!=null) AppScreen.MAP else if(introDone) AppScreen.AUTH else AppScreen.INTRO) }
    when(screen){
        AppScreen.INTRO->IntroScreen{
            context.getSharedPreferences("sidequest",0).edit().putBoolean("intro",true).apply();screen=AppScreen.AUTH
        }
        AppScreen.AUTH->AuthScreen(onSession={store.save(it);session=it;screen=AppScreen.MAP},onPreview={screen=AppScreen.MAP})
        AppScreen.MAP->MapScreen(session=session,onLogout={store.clear();session=null;screen=AppScreen.AUTH})
    }
}

@Composable
private fun IntroScreen(onDone:()->Unit){
    var page by remember{ mutableIntStateOf(0) }
    val copy=listOf(
        "A map with a pulse." to "Search anywhere on Earth and see real places, real photographs and current local coverage.",
        "Real sources, not fake pins." to "OpenStreetMap, Wikimedia and recent news stay clearly separated so you know where every item came from.",
        "Find a sidequest." to "Save public, documented ruins and historic places worth exploring safely from legal public areas."
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFD9E8FF),Color(0xFFEFE7FF),Color(0xFFF8F9FC))))){
        Box(Modifier.fillMaxWidth().height(430.dp)){
            NativeMap(LatLng(45.9432,24.9668),5.6,emptyList(),"Liberty",{}, {_,_,_->})
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,Color(0xFFF8F9FC)))))
            Surface(Modifier.padding(start=20.dp,top=60.dp),shape=RoundedCornerShape(99.dp),color=Color.White.copy(.78f),shadowElevation=12.dp){Text("LIVE WORLD MAP",Modifier.padding(horizontal=16.dp,vertical=9.dp),fontWeight=FontWeight.Black,fontSize=11.sp)}
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp,30.dp)){
            Text("sidequest!",color=Violet,fontWeight=FontWeight.Black,fontSize=19.sp)
            Spacer(Modifier.height(12.dp));Text(copy[page].first,fontSize=38.sp,lineHeight=40.sp,fontWeight=FontWeight.Black,color=Ink)
            Spacer(Modifier.height(12.dp));Text(copy[page].second,fontSize=16.sp,lineHeight=23.sp,color=Muted)
            Spacer(Modifier.height(24.dp));Row(verticalAlignment=Alignment.CenterVertically){
                Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(3){i->Box(Modifier.height(7.dp).width(if(i==page)28.dp else 7.dp).clip(CircleShape).background(if(i==page)Ink else Color(0xFFCDD3DD)))}}
                Button(onClick={if(page<2)page++ else onDone()},shape=RoundedCornerShape(22.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink)){Text(if(page==2)"Join sidequest!" else "Next",fontWeight=FontWeight.Bold)}
            }
        }
    }
}

@Composable
private fun AuthScreen(onSession:(Session)->Unit,onPreview:()->Unit){
    val scope=rememberCoroutineScope();var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var note by remember{mutableStateOf<String?>(null)}
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDDEAFF),Color(0xFFF1E9FF),Color(0xFFF8F9FC))))){
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
            Surface(Modifier.size(72.dp),shape=RoundedCornerShape(24.dp),color=Violet,shadowElevation=18.dp){Box(contentAlignment=Alignment.Center){Text("S",color=Color.White,fontSize=32.sp,fontWeight=FontWeight.Black)}}
            Spacer(Modifier.height(20.dp));Text("Join sidequest!",fontSize=38.sp,fontWeight=FontWeight.Black,color=Ink);Text("Email accounts and cloud sync are live on the free backend.",color=Muted)
            Spacer(Modifier.height(22.dp));OutlinedTextField(email,{email=it},Modifier.fillMaxWidth(),label={Text("Email")},singleLine=true,shape=RoundedCornerShape(20.dp))
            Spacer(Modifier.height(10.dp));OutlinedTextField(pass,{pass=it},Modifier.fillMaxWidth(),label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),shape=RoundedCornerShape(20.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done))
            Spacer(Modifier.height(14.dp));Button(onClick={if(email.isBlank()||pass.length<6){note="Enter a valid email and password.";return@Button};busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){Api.signIn(email.trim(),pass)}}.onSuccess(onSession).onFailure{note=it.message};busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink)){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White)else Text("Sign in with email",fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(9.dp));OutlinedButton(onClick={if(email.isBlank()||pass.length<6){note="Enter a valid email and password.";return@OutlinedButton};busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){Api.signUp(email.trim(),pass)}}.onSuccess{if(it!=null)onSession(it)else note="Account created. Confirm the email, then sign in."}.onFailure{note=it.message};busy=false}},modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(20.dp)){Text("Create account",fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(18.dp));SocialButton("G","Continue with Google"){note="Google is prepared in the app, but needs a free Google OAuth client ID before it can go live."};Spacer(Modifier.height(8.dp));SocialButton("#","Continue with phone"){note="SMS login is disabled in the 100% free build because SMS providers charge per message."}
            note?.let{Spacer(Modifier.height(12.dp));Text(it,color=Muted,fontSize=13.sp)}
            TextButton(onClick=onPreview,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Explore without an account",color=Muted)}
        }
    }
}

@Composable
private fun SocialButton(letter:String,text:String,onClick:()->Unit){OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(20.dp)){Surface(Modifier.size(30.dp),shape=CircleShape,color=Color(0xFFF0F2F6)){Box(contentAlignment=Alignment.Center){Text(letter,fontWeight=FontWeight.Black)}};Spacer(Modifier.width(12.dp));Text(text,fontWeight=FontWeight.Bold,color=Ink)}}

@Composable
private fun MapScreen(session:Session?,onLogout:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var center by remember{mutableStateOf(LatLng(45.9432,24.9668))};var zoom by remember{mutableDoubleStateOf(6.0)};var placeName by remember{mutableStateOf("Romania")};var query by remember{mutableStateOf("")};var results by remember{mutableStateOf<List<SearchPlace>>(emptyList())};var feed by remember{mutableStateOf(DiscoverFeed())};var loading by remember{mutableStateOf(false)};var feedOpen by remember{mutableStateOf(false)};var settingsOpen by remember{mutableStateOf(false)};var radius by remember{mutableIntStateOf(12)};var windowHours by remember{mutableIntStateOf(24)};var mapStyle by remember{mutableStateOf("Liberty")};var map by remember{mutableStateOf<MapLibreMap?>(null)};var selected by remember{mutableStateOf<MapPlace?>(null)}
    val markers=remember(feed){feed.places+feed.wikiPlaces}
    fun refresh(){loading=true;scope.launch{runCatching{withContext(Dispatchers.IO){Api.discover(center.latitude,center.longitude,placeName,radius,windowHours,session)}}.onSuccess{feed=it;feedOpen=true};loading=false}}
    LaunchedEffect(Unit){refresh()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){g->if(g[Manifest.permission.ACCESS_COARSE_LOCATION]==true||g[Manifest.permission.ACCESS_FINE_LOCATION]==true)getCurrentLocation(context,g[Manifest.permission.ACCESS_FINE_LOCATION]==true){a,b->center=LatLng(a,b);zoom=14.5;placeName="Current area";map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center,zoom));refresh()}}
    Box(Modifier.fillMaxSize().background(Color(0xFFDDE8F4))){
        NativeMap(center,zoom,markers,mapStyle,onMapReady={m->map=m;m.setOnMarkerClickListener{mk->selected=markers.firstOrNull{it.title==mk.title};selected!=null}},onCameraIdle={a,b,z->center=LatLng(a,b);zoom=z})
        Column(Modifier.fillMaxSize()){
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+10.dp));Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(Modifier.weight(1f).shadow(14.dp,RoundedCornerShape(28.dp)),shape=RoundedCornerShape(28.dp),color=Color.White.copy(.80f)){TextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Search any place in the world",color=Muted)},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query="";results=emptyList()}){Icon(Icons.Rounded.Close,null)}},colors=TextFieldDefaults.colors(unfocusedContainerColor=Color.Transparent,focusedContainerColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={scope.launch{results=runCatching{withContext(Dispatchers.IO){Api.searchPlaces(query)}}.getOrDefault(emptyList())}}))}
                Spacer(Modifier.width(9.dp));GlassIcon(Icons.Rounded.Person,true){feedOpen=true}
            }
            AnimatedVisibility(results.isNotEmpty()){Surface(Modifier.padding(horizontal=14.dp,vertical=8.dp).fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Color.White.copy(.95f),shadowElevation=12.dp){Column(Modifier.padding(8.dp)){results.take(5).forEach{r->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable{placeName=r.name;center=LatLng(r.lat,r.lon);zoom=13.5;query=r.name;results=emptyList();map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center,zoom));refresh()}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.LocationOn,null,tint=Violet);Spacer(Modifier.width(10.dp));Column{Text(r.name,fontWeight=FontWeight.Bold);if(r.subtitle.isNotBlank())Text(r.subtitle,color=Muted,fontSize=12.sp)}}}}}}
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){GlassIcon(Icons.Rounded.MyLocation){val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(!fine&&!coarse)permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))else getCurrentLocation(context,fine){a,b->center=LatLng(a,b);zoom=14.5;placeName="Current area";map?.animateCamera(CameraUpdateFactory.newLatLngZoom(center,zoom));refresh()}};GlassIcon(Icons.Rounded.Refresh){refresh()};GlassIcon(Icons.Rounded.Layers){settingsOpen=true}}
        if(feed.signal.mentions>0)Surface(Modifier.align(Alignment.TopCenter).padding(top=106.dp).clickable{feedOpen=true},shape=RoundedCornerShape(99.dp),color=Color(0xEEFFF2D3),shadowElevation=8.dp){Row(Modifier.padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(if(feed.signal.level=="high")Danger else Color(0xFFFFB23D)));Spacer(Modifier.width(8.dp));Text("${feed.signal.mentions} unusual media mentions · ${feed.signal.window}",fontSize=12.sp,fontWeight=FontWeight.Bold)}}
        if(loading)LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(),color=Violet)
        selected?.let{p->PlacePreview(p,Modifier.align(Alignment.BottomCenter).padding(start=14.dp,end=14.dp,bottom=104.dp),onClose={selected=null},onSave={if(session!=null)scope.launch{runCatching{withContext(Dispatchers.IO){Api.savePlace(session,p)}}}})}
        BottomDock(Modifier.align(Alignment.BottomCenter),onFeed={feedOpen=true},onSettings={settingsOpen=true})
        AnimatedVisibility(feedOpen){FeedSheet(feed,placeName,onClose={feedOpen=false},onPlace={p->selected=p;feedOpen=false;map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat,p.lon),15.0))},onArticle={u->runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)))}})}
        AnimatedVisibility(settingsOpen){SettingsSheet(radius,windowHours,mapStyle,onRadius={radius=it},onWindow={windowHours=it},onStyle={mapStyle=it;map?.setStyle(styleUri(it))},onClose={settingsOpen=false},onRefresh={settingsOpen=false;refresh()},session=session,onLogout=onLogout)}
    }
}

@Composable
private fun NativeMap(center:LatLng,zoom:Double,markers:List<MapPlace>,styleName:String,onMapReady:(MapLibreMap)->Unit,onCameraIdle:(Double,Double,Double)->Unit){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle;val view=remember{MapView(context).apply{onCreate(Bundle())}};var map by remember{mutableStateOf<MapLibreMap?>(null)}
    DisposableEffect(lifecycle,view){val o=LifecycleEventObserver{_,e->when(e){Lifecycle.Event.ON_START->view.onStart();Lifecycle.Event.ON_RESUME->view.onResume();Lifecycle.Event.ON_PAUSE->view.onPause();Lifecycle.Event.ON_STOP->view.onStop();Lifecycle.Event.ON_DESTROY->view.onDestroy();else->Unit}};lifecycle.addObserver(o);onDispose{lifecycle.removeObserver(o)}}
    AndroidView(factory={view},modifier=Modifier.fillMaxSize()){v->if(map==null)v.getMapAsync{m->map=m;m.uiSettings.isLogoEnabled=false;m.cameraPosition=CameraPosition.Builder().target(center).zoom(zoom).build();m.setStyle(Style.Builder().fromUri(styleUri(styleName)));m.addOnCameraIdleListener{val c=m.cameraPosition.target;onCameraIdle(c.latitude,c.longitude,m.cameraPosition.zoom)};onMapReady(m)}}
    LaunchedEffect(markers,map){map?.let{m->m.clear();markers.take(120).forEach{p->m.addMarker(MarkerOptions().position(LatLng(p.lat,p.lon)).title(p.title).snippet(p.source))}}}
}

private fun styleUri(s:String)=when(s){"Bright"->"https://tiles.openfreemap.org/styles/bright";"Positron"->"https://tiles.openfreemap.org/styles/positron";else->"https://tiles.openfreemap.org/styles/liberty"}

@Composable
private fun GlassIcon(icon:androidx.compose.ui.graphics.vector.ImageVector,accent:Boolean=false,onClick:()->Unit){Surface(Modifier.size(52.dp).shadow(12.dp,CircleShape).clickable(onClick=onClick),shape=CircleShape,color=if(accent)Violet else Color.White.copy(.82f)){Box(contentAlignment=Alignment.Center){Icon(icon,null,tint=if(accent)Color.White else Ink)}}}

@Composable
private fun BottomDock(modifier:Modifier,onFeed:()->Unit,onSettings:()->Unit){Surface(modifier.padding(start=16.dp,end=16.dp,bottom=14.dp).fillMaxWidth().height(76.dp).shadow(20.dp,RoundedCornerShape(30.dp)),shape=RoundedCornerShape(30.dp),color=Color.White.copy(.86f)){Row(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceAround){Dock(Icons.Rounded.Explore,"Explore",onFeed);Dock(Icons.Rounded.Bookmark,"Saved",onFeed);FloatingActionButton(onClick=onFeed,containerColor=Violet,contentColor=Color.White,shape=CircleShape,modifier=Modifier.size(58.dp)){Icon(Icons.Rounded.Add,null)};Dock(Icons.Rounded.Settings,"Settings",onSettings)}}}
@Composable private fun Dock(i:androidx.compose.ui.graphics.vector.ImageVector,t:String,on:()->Unit){Column(Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick=on).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(i,null,tint=Muted);Text(t,fontSize=10.sp,color=Muted,fontWeight=FontWeight.Medium)}}

@Composable
private fun FeedSheet(feed:DiscoverFeed,placeName:String,onClose:()->Unit,onPlace:(MapPlace)->Unit,onArticle:(String)->Unit){Box(Modifier.fillMaxSize().background(Color.Black.copy(.18f)).clickable(onClick=onClose)){Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.74f).clickable(enabled=false){},shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp),color=Color(0xFFF8F9FC),shadowElevation=24.dp){LazyColumn(contentPadding=PaddingValues(bottom=30.dp)){item{Row(Modifier.padding(20.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(placeName,fontSize=28.sp,fontWeight=FontWeight.Black);Text("Live feed · ${feed.signal.window}",color=Muted)};IconButton(onClick=onClose){Icon(Icons.Rounded.Close,null)}}};if(feed.signal.mentions>0)item{SignalCard(feed.signal)};val places=feed.wikiPlaces+feed.places;if(places.isNotEmpty()){item{SectionTitle("PLACES WORTH A LOOK","Documented public sources")};item{LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){items(places.take(18),key={it.id}){p->PlaceCard(p){onPlace(p)}}}}};if(feed.articles.isNotEmpty()){item{SectionTitle("RECENT COVERAGE","Real articles matched to this area")};items(feed.articles.take(25)){a->NewsCard(a){a.url?.let(onArticle)}}};item{Text("Sources: OpenStreetMap/Overpass · Wikipedia/Wikimedia · GDELT. Unusual-media signals are keyword matches, not proof of paranormal activity. Do not enter restricted or unsafe structures.",Modifier.padding(20.dp),color=Muted,fontSize=11.sp,lineHeight=16.sp)}}}}}

@Composable private fun SignalCard(s:Signal){Surface(Modifier.padding(horizontal=18.dp).fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Color(0xFFFFF2D4)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(42.dp),shape=CircleShape,color=Color(0xFFFFCA60)){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.AutoAwesome,null)}};Spacer(Modifier.width(12.dp));Column{Text("${s.mentions} unusual media mentions",fontWeight=FontWeight.Black);Text(s.disclaimer,color=Muted,fontSize=11.sp,lineHeight=15.sp)}}}}
@Composable private fun SectionTitle(a:String,b:String){Column(Modifier.padding(start=20.dp,end=20.dp,top=24.dp,bottom=10.dp)){Text(a,fontSize=12.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp,color=Muted);Text(b,fontSize=12.sp,color=Muted)}}
@Composable private fun PlaceCard(p:MapPlace,on:()->Unit){Surface(Modifier.width(230.dp).height(190.dp).clickable(onClick=on),shape=RoundedCornerShape(26.dp),color=Color.White,shadowElevation=4.dp){Box{if(p.image!=null)AsyncImage(p.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFD7E7FF),Color(0xFFE9DEFF)))));Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,Color(0xCC11141B)))));Column(Modifier.align(Alignment.BottomStart).padding(14.dp)){Text(p.title,color=Color.White,fontWeight=FontWeight.Black,maxLines=2,overflow=TextOverflow.Ellipsis);Text(p.source,color=Color.White.copy(.82f),fontSize=11.sp)}}}}
@Composable private fun NewsCard(a:NewsItem,on:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=on).padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(92.dp),shape=RoundedCornerShape(20.dp),color=Color(0xFFE5E8EF)){if(a.image!=null)AsyncImage(a.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Article,null,tint=Muted)}};Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(a.title,fontWeight=FontWeight.Bold,maxLines=3,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(5.dp));Text(listOfNotNull(a.domain,a.seenDate).joinToString(" · "),color=Muted,fontSize=11.sp)}}}
@Composable private fun PlacePreview(p:MapPlace,m:Modifier,onClose:()->Unit,onSave:()->Unit){Surface(m.fillMaxWidth(),shape=RoundedCornerShape(26.dp),color=Color.White.copy(.94f),shadowElevation=16.dp){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(58.dp),shape=RoundedCornerShape(18.dp),color=Color(0xFFE7EAF1)){if(p.image!=null)AsyncImage(p.image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Rounded.LocationOn,null)}};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(p.title,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis);Text(p.source,color=Muted,fontSize=11.sp)};IconButton(onClick=onSave){Icon(Icons.Rounded.BookmarkAdd,null,tint=Violet)};IconButton(onClick=onClose){Icon(Icons.Rounded.Close,null)}}}}

@Composable
private fun SettingsSheet(radius:Int,window:Int,style:String,onRadius:(Int)->Unit,onWindow:(Int)->Unit,onStyle:(String)->Unit,onClose:()->Unit,onRefresh:()->Unit,session:Session?,onLogout:()->Unit){Box(Modifier.fillMaxSize().background(Color.Black.copy(.16f)).clickable(onClick=onClose)){Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(enabled=false){},shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp),color=Color(0xFFF8F9FC)){Column(Modifier.padding(20.dp).padding(bottom=18.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("Map & live data",fontSize=28.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Rounded.Close,null)}};Text("Search radius",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(5,12,25).forEach{FilterChip(selected=radius==it,onClick={onRadius(it)},label={Text("$it km")})}};Spacer(Modifier.height(14.dp));Text("News window",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(6,24,168).forEach{FilterChip(selected=window==it,onClick={onWindow(it)},label={Text(if(it==168)"7 days" else "$it h")})}};Spacer(Modifier.height(14.dp));Text("Map style",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Liberty","Bright","Positron").forEach{s->FilterChip(selected=style==s,onClick={onStyle(s)},label={Text(s)})}};Spacer(Modifier.height(18.dp));Button(onClick=onRefresh,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink)){Text("Apply & refresh",fontWeight=FontWeight.Bold)};session?.let{Spacer(Modifier.height(10.dp));OutlinedButton(onClick=onLogout,Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Text("Sign out · ${it.email}")}}}}}}

private fun getCurrentLocation(context:android.content.Context,high:Boolean,result:(Double,Double)->Unit){val lm=context.getSystemService(LocationManager::class.java);val provider=if(high&&lm.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER;try{lm.getCurrentLocation(provider,CancellationSignal(),context.mainExecutor){l->if(l!=null)result(l.latitude,l.longitude)}}catch(_:SecurityException){}}
