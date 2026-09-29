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
import androidx.compose.ui.graphics.Brush
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

private val EntryInk = Color(0xFF111216)
private val EntryMuted = Color(0xFF747984)
private val EntryAccent = Color(0xFF6C5CE7)
private val EntryBg = Color(0xFFF5F6F8)

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
                    primary = EntryAccent,
                    background = EntryBg,
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

    Box(Modifier.fillMaxSize().background(EntryBg)) {
        EntryMapBackground(Modifier.fillMaxWidth().height(390.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(390.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, EntryBg.copy(alpha = 0.20f), EntryBg)
                    )
                )
        )

        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 18.dp, top = 12.dp)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 8.dp
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(Modifier.size(29.dp), shape = CircleShape, color = EntryInk) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("sidequest", fontWeight = FontWeight.Black, fontSize = 16.sp, color = EntryInk)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color.White,
            shadowElevation = 20.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Text(
                    "See what’s happening nearby.",
                    fontSize = 31.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = EntryInk
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Search places, check current local coverage and save spots you want to revisit.",
                    color = EntryMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(18.dp))

                Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFFF1F2F5)) {
                    Row(Modifier.padding(4.dp)) {
                        EntryModeButton("Sign in", !createMode, Modifier.weight(1f)) {
                            createMode = false
                            note = null
                        }
                        EntryModeButton("Create account", createMode, Modifier.weight(1f)) {
                            createMode = true
                            note = null
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; note = null },
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
                    onValueChange = { password = it; note = null },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    placeholder = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(17.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!busy) submitEntryAuth(
                            createMode, email, password, scope,
                            { busy = it }, { note = it }, onAuthenticated
                        )
                    })
                )

                note?.let {
                    Spacer(Modifier.height(9.dp))
                    Text(it, color = Color(0xFFB3261E), fontSize = 12.sp, lineHeight = 16.sp)
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        submitEntryAuth(
                            createMode, email, password, scope,
                            { busy = it }, { note = it }, onAuthenticated
                        )
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
                    Text("Continue without an account", color = EntryMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EntryModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Surface(
            modifier = modifier,
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
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

private fun submitEntryAuth(
    createMode: Boolean,
    email: String,
    password: String,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    setNote: (String?) -> Unit,
    onAuthenticated: () -> Unit
) {
    if (email.isBlank() || !email.contains("@")) {
        setNote("Enter a valid email address.")
        return
    }
    if (password.length < 6) {
        setNote("Password must have at least 6 characters.")
        return
    }
    if (!FirebaseBootstrap.ready()) {
        setNote("Firebase isn’t connected to this build yet.")
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
            setNote(friendlyAuthError(it))
        }
        setBusy(false)
    }
}

private fun friendlyAuthError(error: Throwable): String {
    val raw = error.message.orEmpty().lowercase()
    return when {
        "email address is already in use" in raw || "email-already-in-use" in raw -> "An account already exists for this email."
        "password is invalid" in raw || "invalid credential" in raw || "wrong-password" in raw -> "Email or password is incorrect."
        "no user record" in raw || "user-not-found" in raw -> "No account exists for this email."
        "network" in raw -> "Couldn’t reach Firebase. Check your internet connection."
        "too many" in raw -> "Too many attempts. Try again later."
        else -> error.message ?: "Authentication failed."
    }
}

@Composable
private fun EntryMapBackground(modifier: Modifier = Modifier) {
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
                    .zoom(5.8)
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

    AndroidView(factory = { mapView }, modifier = modifier)
}
