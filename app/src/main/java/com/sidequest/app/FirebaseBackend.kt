package com.sidequest.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlin.math.round

object FirebaseBackend {
    fun isConfigured(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        return FirebaseApp.initializeApp(context) != null
    }

    fun currentAccount(context: Context): Account? {
        if (!isConfigured(context)) return null
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return Account(user.uid, user.email.orEmpty())
    }

    suspend fun signIn(context: Context, email: String, password: String): Account {
        requireConfigured(context)
        val result = FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .await()
        val user = result.user ?: error("Firebase did not return a user")
        return Account(user.uid, user.email.orEmpty())
    }

    suspend fun signUp(context: Context, email: String, password: String): Account {
        requireConfigured(context)
        val result = FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .await()
        val user = result.user ?: error("Firebase did not return a user")
        return Account(user.uid, user.email.orEmpty())
    }

    fun signOut(context: Context) {
        if (isConfigured(context)) FirebaseAuth.getInstance().signOut()
    }

    suspend fun savePlace(context: Context, place: MapPlace) {
        requireConfigured(context)
        val user = FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("saved_places")
            .document(place.id)
            .set(
                mapOf(
                    "title" to place.title,
                    "subtitle" to place.subtitle,
                    "latitude" to place.lat,
                    "longitude" to place.lon,
                    "source" to place.source,
                    "kind" to place.kind,
                    "savedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    suspend fun createReport(
        context: Context,
        category: String,
        title: String,
        details: String,
        lat: Double,
        lon: Double
    ) {
        requireConfigured(context)
        val user = FirebaseAuth.getInstance().currentUser ?: error("Sign in first")
        val coarseLat = round(lat * 1000.0) / 1000.0
        val coarseLon = round(lon * 1000.0) / 1000.0
        FirebaseFirestore.getInstance().collection("reports").add(
            mapOf(
                "uid" to user.uid,
                "category" to category,
                "title" to title.take(80),
                "details" to details.take(500),
                "latitude" to coarseLat,
                "longitude" to coarseLon,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun loadReports(context: Context): List<CommunityReport> {
        if (!isConfigured(context)) return emptyList()
        val docs = FirebaseFirestore.getInstance()
            .collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()

        return docs.documents.mapNotNull { d ->
            val lat = d.getDouble("latitude") ?: return@mapNotNull null
            val lon = d.getDouble("longitude") ?: return@mapNotNull null
            CommunityReport(
                id = d.id,
                uid = d.getString("uid").orEmpty(),
                category = d.getString("category") ?: "other",
                title = d.getString("title").orEmpty(),
                details = d.getString("details").orEmpty(),
                lat = lat,
                lon = lon,
                createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            )
        }
    }

    private fun requireConfigured(context: Context) {
        if (!isConfigured(context)) {
            error("Firebase is not connected yet. Add app/google-services.json.")
        }
    }
}
