package com.sidequest.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private val EntryInk = Color(0xFF111217)
private val EntryMuted = Color(0xFF747984)
private val EntryBlue = Color(0xFF536DFF)
private val EntrySurface = Color(0xFFF9FAFC)

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        val store = SessionStore(this)
        if (store.load() != null) {
            openMap()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = EntryBlue,
                    background = EntrySurface,
                    surface = Color.White
                )
            ) {
                EntryScreen(
                    onAuthenticated = { openMap() },
                    onGuest = {
                        store.setGuest()
                        openMap()
                    }
                )
            }
        }
    }

    private fun openMap() {
        getSharedPreferences("sidequest", 0).edit().putBoolean("intro", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
private fun EntryScreen(onAuthenticated: () -> Unit, onGuest: () -> Unit) {
    val scope = rememberCoroutineScope()
    var createMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(EntrySurface)) {
        EntryMapBackground()
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.10f)))

        Surface(
            modifier = Modifier
                .padding(start = 18.dp, top = 52.dp)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 10.dp
        ) {
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(Modifier.size(28.dp), shape = CircleShape, color = EntryInk) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text("sidequest", fontWeight = FontWeight.Black, fontSize = 16.sp, color = EntryInk)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color.White,
            shadowElevation = 24.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Text(
                    "Your area, on one map.",
                    fontSize = 29.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black,
                    color = EntryInk
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Local incidents, unusual reports and documented places — clearly sourced.",
                    color = EntryMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0F2F6)
                ) {
                    Row(Modifier.padding(4.dp)) {
                        ModeButton("Sign in", !createMode, Modifier.weight(1f)) {
                            createMode = false
                            note = null
                        }
                        ModeButton("Create account", createMode, Modifier.weight(1f)) {
                            createMode = true
                            note = null
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Mail, null) },
                    placeholder = { Text("Email") },
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    placeholder = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(17.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!busy) submitAuth(createMode, email, password, scope, { busy = it }, { note = it }, onAuthenticated)
                    })
                )

                note?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = if (it.startsWith("Firebase")) Color(0xFFB35A00) else Color(0xFFB3261E), fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        submitAuth(createMode, email, password, scope, { busy = it }, { note = it }, onAuthenticated)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EntryInk)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(if (createMode) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }

                TextButton(onClick = onGuest, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Explore without an account", color = EntryMuted, fontWeight = FontWeight.SemiBold)
                }

                if (!FirebaseBootstrap.isConfigured) {
                    Text(
                        "Firebase project config is not in this build yet.",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = Color(0xFF8D5A00),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Surface(
            modifier = modifier,
            onClick = onClick,
            shape = RoundedCornerShape(13.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontWeight = FontWeight.Bold, color = EntryInk, fontSize = 13.sp)
            }
        }
    } else {
        TextButton(onClick = onClick, modifier = modifier) {
            Text(label, color = EntryMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

private fun submitAuth(
    createMode: Boolean,
    email: String,
    password: String,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    setNote: (String?) -> Unit,
    onAuthenticated: () -> Unit
) {
    if (email.isBlank() || !email.contains("@")) {
        setNote("Enter a valid email.")
        return
    }
    if (password.length < 6) {
        setNote("Password must have at least 6 characters.")
        return
    }
    if (!FirebaseBootstrap.ready()) {
        setNote("Firebase project config is missing from this build.")
        return
    }

    setBusy(true)
    setNote(null)
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                if (createMode) Api.signUp(email.trim(), password) else Api.signIn(email.trim(), password)
            }
        }.onSuccess {
            if (it != null) onAuthenticated()
        }.onFailure {
            setNote(it.message ?: "Authentication failed.")
        }
        setBusy(false)
    }
}

@Composable
private fun EntryMapBackground() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.uiSettings.apply {
                    isScrollGesturesEnabled = false
                    isZoomGesturesEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = false
                }
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(45.94, 24.97))
                    .zoom(5.6)
                    .build()
                map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"))
            }
        }
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

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}
