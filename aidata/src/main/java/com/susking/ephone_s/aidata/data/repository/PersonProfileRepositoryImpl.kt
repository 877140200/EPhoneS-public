package com.susking.ephone_s.aidata.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.susking.ephone_s.aidata.data.local.AiDataDatabase
import com.susking.ephone_s.aidata.data.local.entity.toDomainModel
import com.susking.ephone_s.aidata.data.local.entity.toEntity
import com.susking.ephone_s.aidata.domain.model.PersonProfile
import com.susking.ephone_s.aidata.domain.model.UserProfile
import com.susking.ephone_s.aidata.domain.repository.PersonProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 角色设定和用户设定 Repository 实现
 * 使用 Room 数据库存储 PersonProfile，使用 SharedPreferences 存储 UserProfile
 */
class PersonProfileRepositoryImpl(
    private val context: Context,
    private val database: AiDataDatabase
) : PersonProfileRepository {

    private companion object {
        private const val PREFS_NAME = "qq_prefs"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val USER_PROFILE_ID = "user_self"
        private const val TAG = "PersonProfileRepo"
    }

    private val personProfileDao = database.personProfileDao()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson: Gson by lazy { Gson() }

    // ===== PersonProfile 方法（使用 Room） =====

    override fun getPersonProfilesFlow(): Flow<List<PersonProfile>> {
        return personProfileDao.getAllPersonProfiles().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getPersonProfileByIdFlow(id: String): Flow<PersonProfile?> {
        return personProfileDao.getPersonProfileByIdFlow(id).map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun getPersonProfiles(): List<PersonProfile> = withContext(Dispatchers.IO) {
        personProfileDao.getAllPersonProfilesNonFlow().map { it.toDomainModel() }
    }

    override suspend fun getPersonProfileById(id: String): PersonProfile? = withContext(Dispatchers.IO) {
        personProfileDao.getPersonProfileById(id)?.toDomainModel()
    }

    override suspend fun savePersonProfiles(personProfiles: List<PersonProfile>) {
        withContext(Dispatchers.IO) {
            val entities = personProfiles.map { it.toEntity() }
            personProfileDao.insertAllPersonProfiles(entities)
            Log.d(TAG, "Saved ${personProfiles.size} person profiles to database.")
        }
    }

    override suspend fun updatePersonProfile(personProfile: PersonProfile) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "updatePersonProfile: 收到更新请求: $personProfile")
            personProfileDao.updatePersonProfile(personProfile.toEntity())
            Log.d(TAG, "Updated person profile with ID: ${personProfile.id}")
        }
    }

    override suspend fun updatePersonProfiles(profiles: List<PersonProfile>) {
        withContext(Dispatchers.IO) {
            val entities = profiles.map { it.toEntity() }
            personProfileDao.updatePersonProfiles(entities)
            Log.d(TAG, "Updated ${profiles.size} person profiles.")
        }
    }

    override suspend fun deletePersonProfile(id: String) {
        withContext(Dispatchers.IO) {
            personProfileDao.deletePersonProfileById(id)
            Log.d(TAG, "Deleted person profile for ID: $id")
        }
    }

    override suspend fun updateUnreadMessageCount(id: String, count: Int) {
        withContext(Dispatchers.IO) {
            personProfileDao.updateUnreadMessageCount(id, count)
        }
    }


    override suspend fun updatePinnedStatus(id: String, isPinned: Boolean) {
        withContext(Dispatchers.IO) {
            personProfileDao.updatePinnedStatus(id, isPinned)
        }
    }

    // ===== UserProfile 方法（使用 SharedPreferences） =====

    override suspend fun getUserProfile(): UserProfile = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_USER_PROFILE, null)
        if (json != null) {
            try {
                val profileMap = gson.fromJson<Map<String, Any?>>(json, object : TypeToken<Map<String, Any?>>() {}.type)

                if (profileMap.containsKey("id") && profileMap["id"] != null) {
                    return@withContext gson.fromJson(json, UserProfile::class.java)
                } else {
                    Log.i(TAG, "Old user profile format detected, migrating...")
                    val migratedProfile = UserProfile(
                        id = USER_PROFILE_ID,
                        nickname = profileMap["nickname"] as? String ?: "人类",
                        signature = profileMap["signature"] as? String ?: "Does that make me insane? (^^)",
                        avatarUri = profileMap["avatarUri"] as? String,
                        backgroundUri = profileMap["backgroundUri"] as? String,
                        persona = profileMap["persona"] as? String ?: ""
                    )
                    saveUserProfile(migratedProfile)
                    return@withContext migratedProfile
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse or migrate user profile, creating new default.", e)
            }
        }

        return@withContext createDefaultUserProfile().also { saveUserProfile(it) }
    }

    override suspend fun saveUserProfile(userProfile: UserProfile) {
        withContext(Dispatchers.IO) {
            val json = gson.toJson(userProfile)
            prefs.edit().putString(KEY_USER_PROFILE, json).apply()
            Log.d(TAG, "Saved user profile to SharedPreferences.")
        }
    }

    private fun createDefaultUserProfile(): UserProfile {
        return UserProfile(
            id = USER_PROFILE_ID,
            nickname = "人类",
            signature = "Does that make me insane? (^^)",
            avatarUri = null,
            backgroundUri = null,
            persona = ""
        )
    }
}