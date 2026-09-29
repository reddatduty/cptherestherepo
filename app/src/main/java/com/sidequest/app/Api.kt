package com.sidequest.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object FirebaseBootstrap {
    @Volatile private var app: FirebaseApp? = null

    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun ensure(context: Context): Boolean {
        app?.let { return true }
        synchronized(this) {
            app?.let { return true }

            val existing = FirebaseApp.getApps(context).firstOrNull()
            if (existing != null) {
                app = existing
                return true
            }

            if (!isConfigured) return false

            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()

            app = FirebaseApp.initializeApp(context.applicationContext, options, "sidequest")
            return app != null
        }
    }

    fun ready(): Boolean = app != null

    fun auth(): FirebaseAuth {
        val current = app ?: error("Firebase is not configured for this build.")
        return FirebaseAuth.getInstance(current)
    }

    fun db(): FirebaseFirestore {
        val current = app ?: error("Firebase is not configured for this build.")
        return FirebaseFirestore.getInstance(current)
    }

    fun currentSession(): Session? {
        val current = app ?: return null
        val user = FirebaseAuth.getInstance(current).currentUser ?: return null
        return Session(
            accessToken = "firebase",
            refreshToken = null,
            userId = user.uid,
            email = user.email.orEmpty()
        )
    }

    fun signOut() {
        runCatching { auth().signOut() }
    }
}

object Api {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .build()

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
        val safeRadiusKm = radiusKm.coerceIn(1, 25)
        val radiusMeters = safeRadiusKm * 1000
        val timeSpan = when {
            windowHours <= 6 -> "6h"
            windowHours <= 24 -> "24h"
            else -> "7d"
        }

        val overpassQuery = """
            [out:json][timeout:16];
            (
              nwr["historic"="ruins"](around:$radiusMeters,$lat,$lon);
              nwr["ruins"="yes"](around:$radiusMeters,$lat,$lon);
              nwr["historic"="archaeological_site"](around:$radiusMeters,$lat,$lon);
            );
            out center tags 80;
        """.trimIndent()

        val overpassReq = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", "Sidequest/1.0")
            .post(("data=" + URLEncoder.encode(overpassQuery, "UTF-8")).toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val wikiUrl = buildString {
            append("https://en.wikipedia.org/w/api.php?action=query&generator=geosearch")
            append("&prop=coordinates%7Cpageimages%7Cdescription%7Cinfo&inprop=url&pithumbsize=900")
            append("&ggscoord=").append(URLEncoder.encode("$lat|$lon", "UTF-8"))
            append("&ggsradius=").append(radiusMeters.coerceAtMost(10_000))
            append("&ggslimit=30&format=json&origin=*")
        }

        val gdeltUrl = if (placeName.isBlank()) null else buildString {
            append("https://api.gdeltproject.org/api/v2/doc/doc?query=")
            append(URLEncoder.encode("\"${placeName.replace("\"", "")}\"", "UTF-8"))
            append("&mode=artlist&format=json&maxrecords=50&sort=datedesc&timespan=")
            append(timeSpan)
        }

        val overpass = runCatching {
            client.newCall(overpassReq).execute().use { if (it.isSuccessful) JSONObject(it.body.string()) else null }
        }.getOrNull()

        val wiki = fetchJson(wikiUrl)
        val gdelt = gdeltUrl?.let(::fetchJson)

        val places = parseOverpass(overpass)
        val wikiPlaces = parseWiki(wiki)
        val articles = parseNews(gdelt)
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
                window = timeSpan,
                label = "Unusual-media mention signal",
                disclaimer = "Keyword matches in recent coverage; not evidence of paranormal activity."
            ),
            generatedAt = System.currentTimeMillis().toString()
        )
    }

    fun signIn(email: String, password: String): Session {
        if (!FirebaseBootstrap.ready()) {
            throw IllegalStateException("Firebase is not configured yet. Add the project configuration and rebuild.")
        }
        val result = Tasks.await(FirebaseBootstrap.auth().signInWithEmailAndPassword(email, password))
        val user = result.user ?: throw IllegalStateException("Firebase sign-in returned no user.")
        return Session("firebase", null, user.uid, user.email ?: email)
    }

    fun signUp(email: String, password: String): Session? {
        if (!FirebaseBootstrap.ready()) {
            throw IllegalStateException("Firebase is not configured yet. Add the project configuration and rebuild.")
        }
        val result = Tasks.await(FirebaseBootstrap.auth().createUserWithEmailAndPassword(email, password))
        val user = result.user ?: throw IllegalStateException("Firebase account creation returned no user.")
        return Session("firebase", null, user.uid, user.email ?: email)
    }

    fun savePlace(session: Session, place: MapPlace) {
        if (session.userId == "guest") return
        if (!FirebaseBootstrap.ready()) throw IllegalStateException("Firebase is not configured.")

        val payload = hashMapOf<String, Any?>(
            "title" to place.title,
            "latitude" to place.lat,
            "longitude" to place.lon,
            "category" to place.kind,
            "subtitle" to place.subtitle,
            "source" to place.source,
            "image" to place.image,
            "savedAt" to System.currentTimeMillis()
        )

        Tasks.await(
            FirebaseBootstrap.db()
                .collection("users")
                .document(session.userId)
                .collection("saved_places")
                .document(place.id.replace("/", "_"))
                .set(payload)
        )
    }

    private fun fetchJson(url: String): JSONObject? {
        val req = Request.Builder().url(url).header("User-Agent", "Sidequest/1.0").build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) null else JSONObject(res.body.string())
            }
        }.getOrNull()
    }

    private fun parseOverpass(root: JSONObject?): List<MapPlace> {
        val arr = root?.optJSONArray("elements") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val access = tags.optString("access")
            if (access == "private" || access == "no") return@mapNotNull null

            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat", Double.NaN) else center?.optDouble("lat", Double.NaN) ?: Double.NaN
            val lon = if (e.has("lon")) e.optDouble("lon", Double.NaN) else center?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null

            val title = tags.optString("name")
                .ifBlank { tags.optString("name:en") }
                .ifBlank { tags.optString("name:ro") }
                .ifBlank { "Historic site" }

            MapPlace(
                id = "osm-${e.optString("type")}-${e.optLong("id")}",
                title = title,
                subtitle = "Documented historic/ruin site. Follow posted access rules.",
                lat = lat,
                lon = lon,
                image = null,
                source = "OpenStreetMap",
                kind = if (tags.optString("historic") == "archaeological_site") "archaeological" else "ruins"
            )
        }.take(60)
    }

    private fun parseWiki(root: JSONObject?): List<MapPlace> {
        val pages = root?.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        return pages.keys().asSequence().mapNotNull { key ->
            val p = pages.optJSONObject(key) ?: return@mapNotNull null
            val c = p.optJSONArray("coordinates")?.optJSONObject(0) ?: return@mapNotNull null
            val lat = c.optDouble("lat", Double.NaN)
            val lon = c.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            MapPlace(
                id = "wiki-${p.optLong("pageid")}",
                title = p.optString("title", "Wikipedia place"),
                subtitle = p.optString("description", "Nearby documented place"),
                lat = lat,
                lon = lon,
                image = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("http") },
                source = "Wikipedia",
                kind = "wiki"
            )
        }.take(30).toList()
    }

    private fun parseNews(root: JSONObject?): List<NewsItem> {
        val arr = root?.optJSONArray("articles") ?: return emptyList()
        val unusualTerms = listOf("paranormal", "unexplained", "strange", "mysterious", "mystery", "ufo", "ghost", "haunted", "apparition", "odd lights", "ciudat", "misterios", "inexplicabil", "fantom", "ozn", "lumini ciudate")
        val incidentTerms = listOf("police", "crime", "arrest", "missing", "fire", "explosion", "accident", "investigation", "poliți", "crim", "dispăr", "incend", "exploz", "accident", "anchet")

        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val lower = title.lowercase()
            val kind = when {
                unusualTerms.any(lower::contains) -> "unusual-mention"
                incidentTerms.any(lower::contains) -> "incident-news"
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

class SessionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("sidequest_session", Context.MODE_PRIVATE)

    init {
        FirebaseBootstrap.ensure(context)
    }

    fun load(): Session? {
        FirebaseBootstrap.currentSession()?.let { return it }
        return if (prefs.getBoolean("guest", false)) {
            Session("guest", null, "guest", "")
        } else null
    }

    fun save(session: Session) {
        prefs.edit().putBoolean("guest", false).apply()
    }

    fun setGuest() {
        prefs.edit().putBoolean("guest", true).apply()
    }

    fun clear() {
        FirebaseBootstrap.signOut()
        prefs.edit().clear().apply()
        if (context is Activity) {
            context.startActivity(Intent(context, EntryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            context.finish()
        }
    }
}
