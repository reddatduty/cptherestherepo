package com.sidequest.app

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Api {
    private const val BASE = "https://hhylxjuykwqsvdoigwni.supabase.co"
    private const val PUBLISHABLE = "sb_publishable_0TuIsqpuJ07vVqRBRc_C-w_oKteuq-Q"
    private const val ANON_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhoeWx4anV5a3dxc3Zkb2lnd25pIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA2Nzk0MjAsImV4cCI6MjEwNjI1NTQyMH0.HUAnMnouuVb1gFvHe4zyf4t4iE0ijtZVXz7i_sElEzk"
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(24, TimeUnit.SECONDS).build()

    fun searchPlaces(query:String):List<SearchPlace> {
        val url = "https://photon.komoot.io/api/?limit=8&q=" + URLEncoder.encode(query,"UTF-8")
        val req = Request.Builder().url(url).header("User-Agent","Sidequest/0.9").build()
        client.newCall(req).execute().use { res ->
            if(!res.isSuccessful) return emptyList()
            val root = JSONObject(res.body.string())
            val arr = root.optJSONArray("features") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: JSONObject()
                val name = p.optString("name").ifBlank { p.optString("city").ifBlank { p.optString("country") } }
                if(name.isBlank()) return@mapNotNull null
                val subtitle = listOf(p.optString("city"),p.optString("state"),p.optString("country")).filter { it.isNotBlank() && it != name }.distinct().joinToString(" · ")
                SearchPlace(name,subtitle,coords.optDouble(1),coords.optDouble(0))
            }
        }
    }

    fun discover(lat:Double,lon:Double,placeName:String,radiusKm:Int,windowHours:Int,session:Session?):DiscoverFeed {
        val payload = JSONObject().put("lat",lat).put("lon",lon).put("placeName",placeName).put("radiusKm",radiusKm).put("windowHours",windowHours)
        val req = Request.Builder().url("$BASE/functions/v1/discover-feed")
            .header("apikey",PUBLISHABLE)
            .header("Authorization","Bearer ${session?.accessToken ?: ANON_JWT}")
            .post(payload.toString().toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { res ->
            if(!res.isSuccessful) throw IllegalStateException("Discover feed ${res.code}")
            return parseFeed(JSONObject(res.body.string()))
        }
    }

    fun signIn(email:String,password:String):Session {
        val body = JSONObject().put("email",email).put("password",password).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$BASE/auth/v1/token?grant_type=password").header("apikey",PUBLISHABLE).post(body).build()
        client.newCall(req).execute().use { res ->
            val text = res.body.string()
            if(!res.isSuccessful) throw IllegalArgumentException(JSONObject(text).optString("msg","Sign in failed"))
            return parseSession(JSONObject(text),email)
        }
    }

    fun signUp(email:String,password:String):Session? {
        val body = JSONObject().put("email",email).put("password",password).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$BASE/auth/v1/signup").header("apikey",PUBLISHABLE).post(body).build()
        client.newCall(req).execute().use { res ->
            val text = res.body.string()
            if(!res.isSuccessful) throw IllegalArgumentException(JSONObject(text).optString("msg","Account creation failed"))
            val obj = JSONObject(text)
            if(obj.optString("access_token").isBlank()) return null
            return parseSession(obj,email)
        }
    }

    fun savePlace(session:Session,place:MapPlace) {
        val body = JSONObject().put("user_id",session.userId).put("title",place.title).put("latitude",place.lat).put("longitude",place.lon).put("category",place.kind).put("notes",place.subtitle).toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$BASE/rest/v1/saved_places")
            .header("apikey",PUBLISHABLE).header("Authorization","Bearer ${session.accessToken}").header("Prefer","return=minimal").post(body).build()
        client.newCall(req).execute().use { if(!it.isSuccessful) throw IllegalStateException("Save failed ${it.code}") }
    }

    private fun parseSession(o:JSONObject,fallbackEmail:String):Session {
        val user = o.optJSONObject("user") ?: JSONObject()
        return Session(o.getString("access_token"),o.optString("refresh_token").takeIf { it.isNotBlank() },user.optString("id"),user.optString("email").ifBlank { fallbackEmail })
    }

    private fun parseFeed(root:JSONObject):DiscoverFeed {
        fun mapPlaces(arr:JSONArray?,wiki:Boolean):List<MapPlace> = if(arr==null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o=arr.optJSONObject(i) ?: return@mapNotNull null
            val lat=o.optDouble("lat",Double.NaN); val lon=o.optDouble("lon",Double.NaN)
            if(!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            MapPlace(
                o.optString("id","p$i"),
                o.optString("title","Place"),
                if(wiki) o.optString("description","Wikipedia place") else o.optString("note","Documented public place"),
                lat,lon,
                o.optString("image").takeIf { it.startsWith("http") },
                o.optString("source",if(wiki) "Wikipedia" else "OpenStreetMap"),
                if(wiki) "wiki" else o.optString("kind","place")
            )
        }
        fun news(arr:JSONArray?):List<NewsItem> = if(arr==null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o=arr.optJSONObject(i) ?: return@mapNotNull null
            val title=o.optString("title"); if(title.isBlank()) return@mapNotNull null
            NewsItem(title,o.optString("url").takeIf { it.startsWith("http") },o.optString("image").takeIf { it.startsWith("http") },o.optString("domain").takeIf { it.isNotBlank() },o.optString("seenDate").takeIf { it.isNotBlank() },o.optString("kind","local-news"))
        }
        val s=root.optJSONObject("signal") ?: JSONObject()
        return DiscoverFeed(
            mapPlaces(root.optJSONArray("places"),false),
            mapPlaces(root.optJSONArray("wikiPlaces"),true),
            news(root.optJSONArray("articles")),
            news(root.optJSONArray("incidentArticles")),
            news(root.optJSONArray("unusualArticles")),
            Signal(s.optString("level","none"),s.optInt("mentions",0),s.optString("window","24h"),s.optString("label","Unusual-media mention signal"),s.optString("disclaimer","Keyword matches only.")),
            root.optString("generatedAt")
        )
    }
}

class SessionStore(context:Context) {
    private val p=context.getSharedPreferences("sidequest_session",Context.MODE_PRIVATE)
    fun load():Session? {
        val token=p.getString("access",null) ?: return null
        return Session(token,p.getString("refresh",null),p.getString("uid","") ?: "",p.getString("email","") ?: "")
    }
    fun save(s:Session)=p.edit().putString("access",s.accessToken).putString("refresh",s.refreshToken).putString("uid",s.userId).putString("email",s.email).apply()
    fun clear()=p.edit().clear().apply()
}
