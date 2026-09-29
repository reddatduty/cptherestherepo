package com.sidequest.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.TimeUnit

private val Ink = Color(0xFF0F1117)
private val Muted = Color(0xFF707684)
private val Accent = Color(0xFF6657F5)
private val Accent2 = Color(0xFFFF5F8F)
private val PageBg = Color(0xFFF5F6FA)

private enum class EntryStage { INTRO, AUTH }
private enum class AuthMethod { EMAIL, PHONE }

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        FirebaseBootstrap.ensure(this)

        if (FirebaseBootstrap.currentSession() != null) {
            openMap()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    background = PageBg,
                    surface = Color.White
                )
            ) {
                EntryRoot(onAuthenticated = ::openMap)
            }
        }
    }

    private fun openMap() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
private fun EntryRoot(onAuthenticated: () -> Unit) {
    var stage by remember { mutableStateOf(EntryStage.INTRO) }
    Box(Modifier.fillMaxSize().background(PageBg)) {
        EntryMapBackground(Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.05f),
                        Color.Transparent,
                        PageBg.copy(alpha = 0.18f),
                        PageBg.copy(alpha = 0.92f)
                    )
                )
            )
        )

        BrandPill(Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp))

        when (stage) {
            EntryStage.INTRO -> IntroPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                onContinue = { stage = EntryStage.AUTH }
            )
            EntryStage.AUTH -> AuthPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                onAuthenticated = onAuthenticated
            )
        }
    }
}

@Composable
private fun BrandPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.90f),
        shadowElevation = 10.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(Accent, Accent2))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Explore, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("sidequest!", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Ink)
        }
    }
}

@Composable
private fun IntroPanel(modifier: Modifier = Modifier, onContinue: () -> Unit) {
    GlassPanel(modifier) {
        Text(
            "See the world differently.",
            color = Ink,
            fontSize = 34.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "Explore real places, nearby stories, unusual media mentions and community reports — all pinned to a live map.",
            color = Muted,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            Text("Get started", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AuthPanel(modifier: Modifier = Modifier, onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var createMode by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(AuthMethod.EMAIL) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun requireFirebase(): Boolean {
        if (!FirebaseBootstrap.ready()) {
            note = "Firebase is not connected to this build yet."
            return false
        }
        return true
    }

    fun emailSubmit() {
        if (!requireFirebase()) return
        if (!email.contains("@")) { note = "Enter a valid email."; return }
        if (password.length < 6) { note = "Password must have at least 6 characters."; return }
        busy = true
        note = null
        scope.launch {
            runCatching {
                if (createMode) {
                    FirebaseBootstrap.auth().createUserWithEmailAndPassword(email.trim(), password).await()
                } else {
                    FirebaseBootstrap.auth().signInWithEmailAndPassword(email.trim(), password).await()
                }
            }.onSuccess { onAuthenticated() }
                .onFailure { note = friendlyAuthError(it) }
            busy = false
        }
    }

    fun googleSubmit() {
        if (!requireFirebase()) return
        if (BuildConfig.FIREBASE_WEB_CLIENT_ID.isBlank()) {
            note = "Google sign-in needs the Firebase Web client ID."
            return
        }
        busy = true
        note = null
        scope.launch {
            runCatching {
                val manager = CredentialManager.create(context)
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.FIREBASE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = manager.getCredential(context, request)
                val google = GoogleIdTokenCredential.createFrom(result.credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
                FirebaseBootstrap.auth().signInWithCredential(firebaseCredential).await()
            }.onSuccess { onAuthenticated() }
                .onFailure { note = friendlyAuthError(it) }
            busy = false
        }
    }

    fun phoneSubmit() {
        if (!requireFirebase()) return
        val id = verificationId
        if (id != null) {
            if (code.length < 4) { note = "Enter the SMS code."; return }
            busy = true
            scope.launch {
                runCatching {
                    val credential = PhoneAuthProvider.getCredential(id, code.trim())
                    FirebaseBootstrap.auth().signInWithCredential(credential).await()
                }.onSuccess { onAuthenticated() }
                    .onFailure { note = friendlyAuthError(it) }
                busy = false
            }
            return
        }

        if (!phone.startsWith("+") || phone.length < 8) {
            note = "Use the full phone number, for example +40…"
            return
        }
        busy = true
        note = null
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch {
                    runCatching { FirebaseBootstrap.auth().signInWithCredential(credential).await() }
                        .onSuccess { onAuthenticated() }
                        .onFailure { note = friendlyAuthError(it) }
                    busy = false
                }
            }

            override fun onVerificationFailed(error: FirebaseException) {
                note = friendlyAuthError(error)
                busy = false
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                busy = false
                note = "Code sent. Check your SMS."
            }
        }
        val options = PhoneAuthOptions.newBuilder(FirebaseBootstrap.auth())
            .setPhoneNumber(phone.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    GlassPanel(modifier) {
        Text(
            if (createMode) "Create your account" else "Welcome back",
            color = Ink,
            fontSize = 29.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Sign in to sync saved places and community reports.",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(17.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0F1F5)).padding(4.dp)
        ) {
            ModePill("Sign in", !createMode, Modifier.weight(1f)) { createMode = false; note = null }
            ModePill("Create", createMode, Modifier.weight(1f)) { createMode = true; note = null }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialButton("Google", Modifier.weight(1f), enabled = !busy, onClick = ::googleSubmit)
            SocialButton("Phone", Modifier.weight(1f), enabled = !busy) {
                method = AuthMethod.PHONE
                note = null
            }
        }

        Spacer(Modifier.height(12.dp))

        if (method == AuthMethod.EMAIL) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; note = null },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Mail, null) },
                placeholder = { Text("Email") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
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
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!busy) emailSubmit() })
            )
        } else {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; note = null },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Phone, null) },
                placeholder = { Text("+40 7xx xxx xxx") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            if (verificationId != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; note = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("SMS code") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!busy) phoneSubmit() })
                )
            }
            TextButton(onClick = { method = AuthMethod.EMAIL; verificationId = null; note = null }) {
                Text("Use email instead", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        }

        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = if (it.startsWith("Code sent")) Accent else Color(0xFFB3261E), fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { if (method == AuthMethod.EMAIL) emailSubmit() else phoneSubmit() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text(
                    when {
                        method == AuthMethod.PHONE && verificationId != null -> "Verify code"
                        method == AuthMethod.PHONE -> "Send SMS code"
                        createMode -> "Create account"
                        else -> "Sign in"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.82f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(30.dp))
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SocialButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.70f))
    ) {
        Text(label, color = Ink, fontWeight = FontWeight.Bold)
    }
}

private fun friendlyAuthError(error: Throwable): String {
    val raw = error.message.orEmpty().lowercase()
    return when {
        "email-already-in-use" in raw || "already in use" in raw -> "An account already exists for this email."
        "invalid-credential" in raw || "wrong-password" in raw || "password is invalid" in raw -> "Email or password is incorrect."
        "user-not-found" in raw || "no user record" in raw -> "No account exists for this email."
        "invalid-phone-number" in raw -> "That phone number is not valid."
        "invalid-verification-code" in raw -> "The SMS code is incorrect."
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
