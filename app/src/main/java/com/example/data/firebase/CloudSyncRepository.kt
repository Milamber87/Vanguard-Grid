package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.CommanderProfile
import com.example.data.model.MissionEventLog
import com.example.data.model.PlanetColonyDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Commander must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<CommanderProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile observation." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        CommanderProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePlanets(userId: String): Flow<List<PlanetColonyDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user planet observation." }
        val path = "users/$uid/planets"
        emitAll(
            db.collection("users").document(uid).collection("planets")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        parsePlanetDoc(doc)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeEventLogs(userId: String): Flow<List<MissionEventLog>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user event log observation." }
        val path = "users/$uid/eventLogs"
        emitAll(
            db.collection("users").document(uid).collection("eventLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MissionEventLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveCommanderProfile(profile: CommanderProfile, existsOnServer: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val sanitized = profile.copy(userId = uid)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            if (existsOnServer) {
                docRef.update(sanitized.toUpdateMap()).await()
            } else {
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    docRef.update(sanitized.toUpdateMap()).await()
                } else {
                    docRef.set(sanitized.toCreateMap()).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun savePlanetColony(colony: PlanetColonyDoc): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/planets/${colony.planetId}"
        return try {
            val uid = requireUserId()
            require(colony.planetId in ALLOWED_PLANETS) { "Invalid planetId: ${colony.planetId}" }
            val sanitized = colony.copy(userId = uid)
            val docRef = db.collection("users").document(uid)
                .collection("planets").document(colony.planetId)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(sanitized.toUpdateMap()).await()
            } else {
                docRef.set(sanitized.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendEventLog(log: MissionEventLog): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/eventLogs"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("eventLogs").document()
            docPath = docRef.path
            val sanitized = log.copy(userId = uid)
            docRef.set(sanitized.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private fun parsePlanetDoc(doc: DocumentSnapshot): PlanetColonyDoc? {
        val data = doc.data ?: return null
        val rawBuildings = (data["buildings"] as? List<*>)?.mapNotNull { entry ->
            @Suppress("UNCHECKED_CAST")
            entry as? Map<String, Any>
        } ?: emptyList()
        val rawInventoryMap = (data["inventory"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val amount = (v as? Number)?.toLong() ?: 0L
            key to amount
        }?.toMap() ?: emptyMap()
        return PlanetColonyDoc(
            id = doc.id,
            userId = data["userId"] as? String ?: "",
            planetId = data["planetId"] as? String ?: doc.id,
            biomeName = data["biomeName"] as? String ?: "Unknown Sector",
            colonizationPercent = (data["colonizationPercent"] as? Number)?.toLong() ?: 0L,
            powerGeneratedMw = (data["powerGeneratedMw"] as? Number)?.toLong() ?: 0L,
            powerConsumedMw = (data["powerConsumedMw"] as? Number)?.toLong() ?: 0L,
            buildings = rawBuildings,
            inventory = rawInventoryMap,
            activeHazardId = data["activeHazardId"] as? String ?: "none",
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    companion object {
        private val ALLOWED_PLANETS = setOf("temperate", "volcanic", "cryo", "arid")
    }
}
