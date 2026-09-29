package com.sidequest.app

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object Api {
    // Fill these from Firebase project settings / google-services.json.
    // Firebase config values identify the project; they are not server secrets.
    private const val FIREBASE_API_KEY = "FIREBASE_API_KEY_HERE"
    private const val FIREBASE_PROJECT_ID = "FIREBASE_PROJECT_ID_HERE"

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .build()

    fun firebaseConfigured(): Boolean =
        !FIREBASE_API_KEY.endsWith("_HERE") && !FIREBASE_PROJECT_ID.endsWith("_HERE")

    fun searchPlaces(query: String): List<SearchPlace> {
        if (query.isBlank()) return emptyList()
        val url = "https://photon.komoot.io/api/?limit=8&q=" + URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", "Sidequest/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val root = JSONObject(res.body.string())
            val arr = root.optJSONArray("features") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: JSONObject()
                val name = p.optString("name").ifBlank {
                    p.optString("city").ifBlank { p.optString("country") }
                }
                if (name.isBlank()) return@mapNotNull null
                val subtitle = listOf(
                    p.optString("city"),
                    p.optString("state"),
                    p.optString("country")
                ).filter { it.isNotBlank() && it != name }.distinct().joinToString(" · ")
                SearchPlace(name, subtitle, coords.optDouble(1), coords.optDouble(0))
            }
        }
    }

    fun discover(
        lat: Double,
        lon: Double,
        placeName: String,
        radiusKm: Int,
        windowHours: Int,
        session: Session?
    ): DiscoverFeed {
        val radius = radiusKm.coerceIn(1, 25)
        val window = windowHours.coerceIn(1, 168)

        val overpass = CompletableFuture.supplyAsync { fetchOverpass(lat, lon, radius) }
        val wiki = CompletableFuture.supplyAsync { fetchWikipedia(lat, lon, radius) }
        val news = CompletableFuture.supplyAsync { fetchGdelt(placeName, window) }

        val places = runCatching { overpass.get(28, TimeUnit.SECONDS) }.getOrDefault(emptyList())
        val wikiPlaces = runCatching { wiki.get(28, TimeUnit.SECONDS) }.getOrDefault(emptyList())
        val articles = runCatching { news.get(28, TimeUnit.SECONDS) }.getOrDefault(emptyList())

        val unusual = articles.filter { it.kind == "unusual-mention" }
        val incidents = articles.filter { it.kind == "incident-news" }
        val level = when {
            unusual.size >= 5 -> "high"
            unusual.size >= 2 -> "elevated"
            unusual.isNotEmpty() -> "low"
            else -> "none"
        }

        return DiscoverFeed(
            places = places,
            wikiPlaces = wikiPlaces,
            articles = articles,
            incidents = incidents.take(15),
            unusual = unusual.take(15),
            signal = Signal(
                level = level,
                mentions = unusual.size,
                window = if (window == 168) "7d" else "${window}h",
                label = "Unusual-media mention signal",
                disclaimer = "Keyword matches in recent coverage; not evidence of paranormal activity."
            ),
            generatedAt = java.time.Instant.now().toString()
        )
    }

    fun signIn(email: String, password: String): Session {
        requireFirebase()
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("returnSecureToken", true)
            .toString().toRequestBody(jsonType)
        val req = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$FIREBASE_API_KEY")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) throw IllegalArgumentException(firebaseError(text))
            return firebaseSession(JSONObject(text), email)
        }
    }

    fun signUp(email: String, password: String): Session? {
        requireFirebase()
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("returnSecureToken", true)
            .toString().toRequestBody(jsonType)
        val req = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$FIREBASE_API_KEY")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) throw IllegalArgumentException(firebaseError(text))
            return firebaseSession(JSONObject(text), email)
        }
    }

    fun savePlace(session: Session, place: MapPlace) {
        requireFirebase()
        val fields = JSONObject()
            .put("title", stringField(place.title))
            .put("subtitle", stringField(place.subtitle))
            .put("category", stringField(place.kind))
            .put("source", stringField(place.source))
            .put("latitude", doubleField(place.lat))
            .put("longitude", doubleField(place.lon))
            .put("savedAt", timestampField(java.time.Instant.now().toString()))
        place.image?.let { fields.put("image", stringField(it)) }

        val body = JSONObject().put("fields", fields).toString().toRequestBody(jsonType)
        val url = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/(default)/documents/users/${session.userId}/saved_places"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${session.accessToken}")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val text = res.body.string()
                throw IllegalStateException("Could not save place (${res.code}): ${firestoreError(text)}")
            }
        }
    }

    private fun fetchOverpass(lat: Double, lon: Double, radiusKm: Int): List<MapPlace> {
        val radius = radiusKm * 1000
        val q = """
            [out:json][timeout:16];(
              nwr["historic"="ruins"](around:$radius,$lat,$lon);
              nwr["ruins"="yes"](around:$radius,$lat,$lon);
              nwr["historic"="archaeological_site"](around:$radius,$lat,$lon);
              nwr["historic"="castle"](around:$radius,$lat,$lon);
            );out center tags 70;
        """.trimIndent()
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", "Sidequest/1.0")
            .post(FormBody.Builder().add("data", q).build())
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val arr = JSONObject(res.body.string()).optJSONArray("elements") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                val tags = e.optJSONObject("tags") ?: JSONObject()
                val center = e.optJSONObject("center")
                val pLat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
                val pLon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
                if (!pLat.isFinite() || !pLon.isFinite()) return@mapNotNull null
                val name = tags.optString("name")
                    .ifBlank { tags.optString("name:ro") }
                    .ifBlank { tags.optString("name:en") }
                    .ifBlank { "Documented historic place" }
                val kind = when {
                    tags.optString("historic") == "castle" -> "castle"
                    tags.optString("historic") == "archaeological_site" -> "archaeological"
                    else -> "ruins"
                }
                MapPlace(
                    id = "osm-${e.optString("type")}-${e.optLong("id")}",
                    title = name,
                    subtitle = "Mapped historic site. Respect access rules and do not enter closed structures.",
                    lat = pLat,
                    lon = pLon,
                    image = null,
                    source = "OpenStreetMap",
                    kind = kind
                )
            }.take(60)
        }
    }

    private fun fetchWikipedia(lat: Double, lon: Double, radiusKm: Int): List<MapPlace> {
        val radius = (radiusKm * 1000).coerceAtMost(10_000)
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=geosearch&prop=coordinates|pageimages|description|info&inprop=url&pithumbsize=900&ggscoord=${lat}%7C${lon}&ggsradius=$radius&ggslimit=30&format=json&origin=*"
        val req = Request.Builder().url(url).header("User-Agent", "Sidequest/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val pages = JSONObject(res.body.string()).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            return pages.keys().asSequence().mapNotNull { key ->
                val p = pages.optJSONObject(key) ?: return@mapNotNull null
                val c = p.optJSONArray("coordinates")?.optJSONObject(0) ?: return@mapNotNull null
                val pLat = c.optDouble("lat", Double.NaN)
                val pLon = c.optDouble("lon", Double.NaN)
                if (!pLat.isFinite() || !pLon.isFinite()) return@mapNotNull null
                MapPlace(
                    id = "wiki-${p.optLong("pageid")}",
                    title = p.optString("title", "Wikipedia place"),
                    subtitle = p.optString("description", "Nearby documented place"),
                    lat = pLat,
                    lon = pLon,
                    image = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("http") },
                    source = "Wikipedia",
                    kind = "wiki"
                )
            }.take(30).toList()
        }
    }

    private fun fetchGdelt(placeName: String, windowHours: Int): List<NewsItem> {
        if (placeName.isBlank() || placeName.equals("Current area", true)) return emptyList()
        val q = URLEncoder.encode("\"${placeName.replace("\"", "")}\"", "UTF-8")
        val span = if (windowHours >= 168) "7d" else "${windowHours}h"
        val url = "https://api.gdeltproject.org/api/v2/doc/doc?query=$q&mode=artlist&format=json&maxrecords=50&timespan=$span&sort=datedesc"
        val req = Request.Builder().url(url).header("User-Agent", "Sidequest/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val arr = runCatching { JSONObject(res.body.string()).optJSONArray("articles") }.getOrNull() ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                val kind = when {
                    hasAny(title, WEIRD) -> "unusual-mention"
                    hasAny(title, INCIDENT) -> "incident-news"
                    else -> "local-news"
                }
                NewsItem(
                    title = title,
                    url = o.optString("url").takeIf { it.startsWith("http") },
                    image = o.optString("socialimage").takeIf { it.startsWith("http") },
                    domain = o.optString("domain").takeIf { it.isNotBlank() },
                    seenDate = o.optString("seendate").takeIf { it.isNotBlank() },
                    kind = kind
                )
            }.take(50)
        }
    }

    private fun firebaseSession(o: JSONObject, fallbackEmail: String): Session = Session(
        accessToken = o.getString("idToken"),
        refreshToken = o.optString("refreshToken").takeIf { it.isNotBlank() },
        userId = o.getString("localId"),
        email = o.optString("email").ifBlank { fallbackEmail }
    )

    private fun requireFirebase() {
        if (!firebaseConfigured()) {
            throw IllegalStateException("Firebase is not configured yet. Add the Firebase project API key and project ID.")
        }
    }

    private fun firebaseError(text: String): String {
        val raw = runCatching {
            JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when {
            raw.startsWith("EMAIL_EXISTS") -> "An account already exists with this email."
            raw.startsWith("EMAIL_NOT_FOUND") || raw.startsWith("INVALID_LOGIN_CREDENTIALS") -> "Wrong email or password."
            raw.startsWith("INVALID_PASSWORD") -> "Wrong email or password."
            raw.startsWith("WEAK_PASSWORD") -> "Use a stronger password (at least 6 characters)."
            raw.startsWith("INVALID_EMAIL") -> "Enter a valid email address."
            raw.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") -> "Too many attempts. Try again later."
            raw.isNotBlank() -> raw.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            else -> "Firebase authentication failed."
        }
    }

    private fun firestoreError(text: String): String = runCatching {
        JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrDefault("").ifBlank { "Firestore request failed" }

    private fun stringField(v: String) = JSONObject().put("stringValue", v)
    private fun doubleField(v: Double) = JSONObject().put("doubleValue", v)
    private fun timestampField(v: String) = JSONObject().put("timestampValue", v)

    private fun hasAny(text: String, words: List<String>): Boolean {
        val t = text.lowercase()
        return words.any { t.contains(it) }
    }

    private val WEIRD = listOf(
        "paranormal", "unexplained", "strange", "mysterious", "mystery", "ufo", "ghost", "haunted",
        "apparition", "odd lights", "ciudat", "misterios", "inexplicabil", "fantom", "ozn", "lumini ciudate"
    )

    private val INCIDENT = listOf(
        "police", "crime", "arrest", "missing", "fire", "explosion", "accident", "investigation",
        "poliți", "crim", "dispăr", "incend", "exploz", "accident", "anchet"
    )
}

class SessionStore(context: android.content.Context) {
    private val p = context.getSharedPreferences("sidequest_session", android.content.Context.MODE_PRIVATE)

    fun load(): Session? {
        val token = p.getString("access", null) ?: return null
        return Session(
            token,
            p.getString("refresh", null),
            p.getString("uid", "") ?: "",
            p.getString("email", "") ?: ""
        )
    }

    fun save(s: Session) = p.edit()
        .putString("access", s.accessToken)
        .putString("refresh", s.refreshToken)
        .putString("uid", s.userId)
        .putString("email", s.email)
        .apply()

    fun clear() = p.edit().clear().apply()
}
