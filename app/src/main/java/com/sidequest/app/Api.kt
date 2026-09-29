package com.sidequest.app

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .build()

    private val weirdWords = listOf(
        "paranormal","unexplained","strange","mysterious","mystery","ufo","ghost","haunted",
        "apparition","odd lights","ciudat","misterios","misterioasa","misterioasă","inexplicabil","fantom","ozn"
    )
    private val incidentWords = listOf(
        "police","crime","arrest","missing","fire","explosion","accident","investigation",
        "politi","poliți","crim","dispar","dispăr","incend","exploz","accident","anchet"
    )

    fun searchPlaces(query:String):List<SearchPlace> {
        if(query.isBlank()) return emptyList()
        val url = "https://photon.komoot.io/api/?limit=8&q=" + URLEncoder.encode(query.trim(),"UTF-8")
        val req = Request.Builder().url(url).header("User-Agent","Sidequest/0.10 Android").build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                if(!res.isSuccessful) return@use emptyList()
                val root = JSONObject(res.body.string())
                val arr = root.optJSONArray("features") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val f = arr.optJSONObject(i) ?: return@mapNotNull null
                    val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                    val p = f.optJSONObject("properties") ?: JSONObject()
                    val name = p.optString("name").ifBlank { p.optString("city").ifBlank { p.optString("country") } }
                    if(name.isBlank()) return@mapNotNull null
                    val subtitle = listOf(p.optString("city"),p.optString("state"),p.optString("country"))
                        .filter { it.isNotBlank() && it != name }.distinct().joinToString(" · ")
                    SearchPlace(name,subtitle,coords.optDouble(1),coords.optDouble(0))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun discover(lat:Double,lon:Double,placeName:String,radiusKm:Int,windowHours:Int):DiscoverFeed {
        val places = overpassPlaces(lat,lon,radiusKm)
        val wiki = wikipediaPlaces(lat,lon,radiusKm)
        val articles = gdelt(placeName,windowHours)
        val unusual = articles.filter { it.kind == "unusual-mention" }
        val incidents = articles.filter { it.kind == "incident-news" }
        val level = when {
            unusual.size >= 5 -> "high"
            unusual.size >= 2 -> "elevated"
            unusual.isNotEmpty() -> "low"
            else -> "none"
        }
        return DiscoverFeed(
            places=places,
            wikiPlaces=wiki,
            articles=articles,
            incidents=incidents.take(20),
            unusual=unusual.take(20),
            signal=Signal(
                level=level,
                mentions=unusual.size,
                window=if(windowHours >= 168) "7d" else "${windowHours}h",
                disclaimer="Keyword matches in recent news coverage; not evidence of paranormal activity."
            ),
            generatedAt=System.currentTimeMillis().toString()
        )
    }

    private fun overpassPlaces(lat:Double,lon:Double,radiusKm:Int):List<MapPlace> {
        val radius = radiusKm.coerceIn(1,25) * 1000
        val query = """
            [out:json][timeout:18];(
              nwr["historic"="ruins"]["access"~"^(yes|permissive|public)$"](around:$radius,$lat,$lon);
              nwr["historic"="ruins"]["tourism"="attraction"](around:$radius,$lat,$lon);
              nwr["ruins"="yes"]["access"~"^(yes|permissive|public)$"](around:$radius,$lat,$lon);
            );out center tags 60;
        """.trimIndent()
        val body = FormBody.Builder().add("data",query).build()
        val req = Request.Builder().url("https://overpass-api.de/api/interpreter")
            .header("User-Agent","Sidequest/0.10 Android")
            .post(body).build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                if(!res.isSuccessful) return@use emptyList()
                val arr = JSONObject(res.body.string()).optJSONArray("elements") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val tags = o.optJSONObject("tags") ?: JSONObject()
                    val center = o.optJSONObject("center")
                    val la = if(o.has("lat")) o.optDouble("lat") else center?.optDouble("lat",Double.NaN) ?: Double.NaN
                    val lo = if(o.has("lon")) o.optDouble("lon") else center?.optDouble("lon",Double.NaN) ?: Double.NaN
                    if(!la.isFinite() || !lo.isFinite()) return@mapNotNull null
                    val title = tags.optString("name").ifBlank { tags.optString("name:ro").ifBlank { "Documented ruin" } }
                    MapPlace(
                        id="osm-${o.optString("type")}-${o.optLong("id")}",
                        title=title,
                        subtitle="Documented map location · verify access and posted rules",
                        lat=la,lon=lo,image=null,source="OpenStreetMap",kind="ruins"
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun wikipediaPlaces(lat:Double,lon:Double,radiusKm:Int):List<MapPlace> {
        val radius = (radiusKm.coerceIn(1,10) * 1000)
        val params = linkedMapOf(
            "action" to "query",
            "generator" to "geosearch",
            "prop" to "coordinates|pageimages|description|info",
            "inprop" to "url",
            "pithumbsize" to "900",
            "ggscoord" to "$lat|$lon",
            "ggsradius" to radius.toString(),
            "ggslimit" to "25",
            "format" to "json",
            "origin" to "*"
        )
        val url = "https://en.wikipedia.org/w/api.php?" + params.entries.joinToString("&") { (k,v) ->
            "$k=${URLEncoder.encode(v,"UTF-8")}" }
        val req = Request.Builder().url(url).header("User-Agent","Sidequest/0.10 Android").build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                if(!res.isSuccessful) return@use emptyList()
                val pages = JSONObject(res.body.string()).optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
                pages.keys().asSequence().mapNotNull { key ->
                    val p = pages.optJSONObject(key) ?: return@mapNotNull null
                    val c = p.optJSONArray("coordinates")?.optJSONObject(0) ?: return@mapNotNull null
                    val la=c.optDouble("lat",Double.NaN); val lo=c.optDouble("lon",Double.NaN)
                    if(!la.isFinite() || !lo.isFinite()) return@mapNotNull null
                    MapPlace(
                        id="wiki-${p.optLong("pageid")}",
                        title=p.optString("title","Wikipedia place"),
                        subtitle=p.optString("description","Nearby documented place"),
                        lat=la,lon=lo,
                        image=p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("http") },
                        source="Wikipedia",kind="wiki"
                    )
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun gdelt(placeName:String,windowHours:Int):List<NewsItem> {
        if(placeName.isBlank()) return emptyList()
        val span = if(windowHours >= 168) "7d" else "${windowHours.coerceIn(1,167)}h"
        val params = linkedMapOf(
            "query" to "\"${placeName.replace("\"","")}\"",
            "mode" to "artlist",
            "format" to "json",
            "maxrecords" to "50",
            "timespan" to span,
            "sort" to "datedesc"
        )
        val url = "https://api.gdeltproject.org/api/v2/doc/doc?" + params.entries.joinToString("&") { (k,v) ->
            "$k=${URLEncoder.encode(v,"UTF-8")}" }
        val req = Request.Builder().url(url).header("User-Agent","Sidequest/0.10 Android").build()
        return runCatching {
            client.newCall(req).execute().use { res ->
                if(!res.isSuccessful) return@use emptyList()
                val arr = JSONObject(res.body.string()).optJSONArray("articles") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val a=arr.optJSONObject(i) ?: return@mapNotNull null
                    val title=a.optString("title").trim(); if(title.isBlank()) return@mapNotNull null
                    val low=title.lowercase()
                    val kind=when {
                        weirdWords.any(low::contains) -> "unusual-mention"
                        incidentWords.any(low::contains) -> "incident-news"
                        else -> "local-news"
                    }
                    NewsItem(
                        title=title,
                        url=a.optString("url").takeIf { it.startsWith("http") },
                        image=a.optString("socialimage").takeIf { it.startsWith("http") },
                        domain=a.optString("domain").takeIf { it.isNotBlank() },
                        seenDate=a.optString("seendate").takeIf { it.isNotBlank() },
                        kind=kind
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
