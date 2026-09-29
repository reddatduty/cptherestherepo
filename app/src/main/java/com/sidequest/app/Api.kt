package com.sidequest.app

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .build()

    private val unusualWords = listOf(
        "paranormal", "unexplained", "strange", "mysterious", "mystery", "ufo", "ghost", "haunted",
        "apparition", "odd lights", "ciudat", "misterios", "inexplicabil", "fantom", "ozn", "lumini ciudate"
    )
    private val incidentWords = listOf(
        "police", "crime", "arrest", "missing", "fire", "explosion", "accident", "investigation",
        "poliți", "crim", "dispăr", "incend", "exploz", "accident", "anchet"
    )

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
                val name = p.optString("name").ifBlank { p.optString("city").ifBlank { p.optString("country") } }
                if (name.isBlank()) return@mapNotNull null
                val subtitle = listOf(p.optString("city"), p.optString("state"), p.optString("country"))
                    .filter { it.isNotBlank() && it != name }.distinct().joinToString(" · ")
                SearchPlace(name, subtitle, coords.optDouble(1), coords.optDouble(0))
            }
        }
    }

    fun discover(lat: Double, lon: Double, placeName: String, radiusKm: Int, windowHours: Int): DiscoverFeed {
        val places = runCatching { loadOsmPlaces(lat, lon, radiusKm) }.getOrDefault(emptyList())
        val wikiPlaces = runCatching { loadWikipedia(lat, lon, radiusKm) }.getOrDefault(emptyList())
        val articles = runCatching { loadNews(placeName, windowHours) }.getOrDefault(emptyList())
        val unusual = articles.filter { it.kind == "unusual-mention" }
        val incidents = articles.filter { it.kind == "incident-news" }
        val signal = Signal(
            level = when {
                unusual.size >= 5 -> "high"
                unusual.size >= 2 -> "elevated"
                unusual.isNotEmpty() -> "low"
                else -> "none"
            },
            mentions = unusual.size,
            window = if (windowHours < 24) "${windowHours}h" else "${windowHours / 24}d",
            disclaimer = "Keyword matches in recent coverage; not evidence of paranormal activity."
        )
        return DiscoverFeed(
            places = places,
            wikiPlaces = wikiPlaces,
            articles = articles,
            incidents = incidents.take(20),
            unusual = unusual.take(20),
            signal = signal,
            generatedAt = Instant.now().toString()
        )
    }

    private fun loadOsmPlaces(lat: Double, lon: Double, radiusKm: Int): List<MapPlace> {
        val radius = radiusKm.coerceIn(1, 25) * 1000
        val q = """
            [out:json][timeout:16];
            (
              nwr["historic"="ruins"](around:$radius,$lat,$lon);
              nwr["ruins"="yes"](around:$radius,$lat,$lon);
              nwr["historic"]["tourism"="attraction"](around:$radius,$lat,$lon);
            );
            out center tags 80;
        """.trimIndent()
        val body = FormBody.Builder().add("data", q).build()
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", "Sidequest/1.0")
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val arr = JSONObject(res.body.string()).optJSONArray("elements") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                val tags = e.optJSONObject("tags") ?: JSONObject()
                val center = e.optJSONObject("center")
                val pLat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
                val pLon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
                if (!pLat.isFinite() || !pLon.isFinite()) return@mapNotNull null
                val title = tags.optString("name").ifBlank { tags.optString("name:ro") }.ifBlank { "Historic place" }
                MapPlace(
                    id = "osm-${e.optString("type")}-${e.optLong("id")}",
                    title = title,
                    subtitle = "Documented on OpenStreetMap",
                    lat = pLat,
                    lon = pLon,
                    source = "OpenStreetMap",
                    kind = if (tags.optString("historic") == "ruins" || tags.optString("ruins") == "yes") "ruins" else "historic"
                )
            }.take(60)
        }
    }

    private fun loadWikipedia(lat: Double, lon: Double, radiusKm: Int): List<MapPlace> {
        val radius = (radiusKm.coerceIn(1, 10) * 1000).coerceAtMost(10_000)
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=geosearch&prop=coordinates%7Cpageimages%7Cdescription%7Cinfo&inprop=url&pithumbsize=900&ggscoord=$lat%7C$lon&ggsradius=$radius&ggslimit=30&format=json&origin=*"
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
                    subtitle = p.optString("description", "Nearby place"),
                    lat = pLat,
                    lon = pLon,
                    image = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("http") },
                    source = "Wikipedia",
                    kind = "wiki"
                )
            }.take(30).toList()
        }
    }

    private fun loadNews(placeName: String, windowHours: Int): List<NewsItem> {
        if (placeName.isBlank()) return emptyList()
        val timespan = if (windowHours < 24) "${windowHours.coerceAtLeast(1)}h" else "${(windowHours / 24).coerceAtLeast(1)}d"
        val query = URLEncoder.encode("\"${placeName.replace("\"", "")}\"", "UTF-8")
        val url = "https://api.gdeltproject.org/api/v2/doc/doc?query=$query&mode=artlist&format=json&maxrecords=50&timespan=$timespan&sort=datedesc"
        val req = Request.Builder().url(url).header("User-Agent", "Sidequest/1.0").build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val arr = JSONObject(res.body.string()).optJSONArray("articles") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = a.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                val lower = title.lowercase()
                val kind = when {
                    unusualWords.any(lower::contains) -> "unusual-mention"
                    incidentWords.any(lower::contains) -> "incident-news"
                    else -> "local-news"
                }
                NewsItem(
                    title = title,
                    url = a.optString("url").takeIf { it.startsWith("http") },
                    image = a.optString("socialimage").takeIf { it.startsWith("http") },
                    domain = a.optString("domain").takeIf { it.isNotBlank() },
                    seenDate = a.optString("seendate").takeIf { it.isNotBlank() },
                    kind = kind
                )
            }
        }
    }
}
