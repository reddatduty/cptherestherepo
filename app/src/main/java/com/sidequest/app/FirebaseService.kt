package com.sidequest.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseService {
    private const val APP_NAME = "sidequest"
    private var app: FirebaseApp? = null

    fun initialize(context: Context): Boolean {
        app?.let { return true }
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.let {
            app = it
            return true
        }
        if (BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_APP_ID.isBlank() ||
            BuildConfig.FIREBASE_PROJECT_ID.isBlank()) return false

        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
        app = FirebaseApp.initializeApp(context, options, APP_NAME)
        return app != null
    }

    fun isConfigured(): Boolean = app != null
    fun currentUser(): FirebaseUser? = app?.let { FirebaseAuth.getInstance(it).currentUser }

    suspend fun signUp(email:String,password:String): FirebaseUser {
        val a = auth()
        val result = a.createUserWithEmailAndPassword(email.trim(),password).await()
        return result.user ?: error("Account created but no user was returned")
    }

    suspend fun signIn(email:String,password:String): FirebaseUser {
        val result = auth().signInWithEmailAndPassword(email.trim(),password).await()
        return result.user ?: error("Sign in failed")
    }

    fun signOut() {
        if (app != null) auth().signOut()
    }

    suspend fun savePlace(place:MapPlace) {
        val user = currentUser() ?: error("Sign in first")
        val data = hashMapOf<String,Any?>(
            "id" to place.id,
            "title" to place.title,
            "subtitle" to place.subtitle,
            "latitude" to place.lat,
            "longitude" to place.lon,
            "image" to place.image,
            "source" to place.source,
            "kind" to place.kind,
            "savedAt" to FieldValue.serverTimestamp()
        )
        db().collection("users").document(user.uid)
            .collection("savedPlaces").document(place.id).set(data).await()
    }

    suspend fun submitReport(report:CommunityReport) {
        val user = currentUser() ?: error("Sign in first")
        val doc = db().collection("reports").document()
        val data = hashMapOf<String,Any?>(
            "id" to doc.id,
            "category" to report.category,
            "title" to report.title.take(80),
            "description" to report.description.take(600),
            "latitude" to report.lat,
            "longitude" to report.lon,
            "areaName" to report.areaName.take(100),
            "authorId" to user.uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to "pending"
        )
        doc.set(data).await()
    }

    suspend fun loadApprovedReports(limit:Int=100):List<CommunityReport> {
        if(app == null) return emptyList()
        val snap = db().collection("reports")
            .whereEqualTo("status","approved")
            .limit(limit.toLong())
            .get().await()
        return snap.documents.mapNotNull { d ->
            val lat=d.getDouble("latitude") ?: return@mapNotNull null
            val lon=d.getDouble("longitude") ?: return@mapNotNull null
            CommunityReport(
                id=d.id,
                category=d.getString("category") ?: "other",
                title=d.getString("title") ?: "Community report",
                description=d.getString("description") ?: "",
                lat=lat,
                lon=lon,
                areaName=d.getString("areaName") ?: "",
                authorId=d.getString("authorId") ?: "",
                createdAt=d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            )
        }
    }

    private fun auth():FirebaseAuth = FirebaseAuth.getInstance(app ?: error("Firebase is not configured"))
    private fun db():FirebaseFirestore = FirebaseFirestore.getInstance(app ?: error("Firebase is not configured"))
}
