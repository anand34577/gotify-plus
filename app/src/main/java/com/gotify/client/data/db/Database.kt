package com.gotify.client.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow



@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val clientToken: String,
    val clientId: Int = 0,
    val isActive: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    primaryKeys = ["serverId", "id"],
    indices = [
        Index("serverId"),
        Index("appId"),
        Index("date"),
        Index("priority")
    ]
)
data class MessageEntity(
    val id: Long,
    val serverId: Long,
    val appId: Int,
    val title: String,
    val message: String,
    val priority: Int,
    val date: String,
    val extrasJson: String? = null,
    val isRead: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "applications",
    primaryKeys = ["serverId", "id"],
    indices = [Index("serverId")]
)
data class ApplicationEntity(
    val id: Int,
    val serverId: Long,
    val token: String? = null,
    val name: String,
    val description: String,
    val internal: Boolean,
    val image: String,
    val cachedAt: Long = System.currentTimeMillis()
)



@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY addedAt ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): ServerEntity?

    @Query("SELECT * FROM servers WHERE baseUrl = :baseUrl COLLATE NOCASE LIMIT 1")
    suspend fun getServerByBaseUrl(baseUrl: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Query("UPDATE servers SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE servers SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Transaction
    suspend fun switchActiveServer(id: Long) {
        deactivateAll()
        setActive(id)
    }

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServer(id: Long)

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun count(): Int
}

@Dao
interface MessageDao {



    @Query("""
        SELECT * FROM messages 
        WHERE serverId = :serverId 
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getMessagesPaged(serverId: Long, limit: Int = 50, offset: Int = 0): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE serverId = :serverId AND appId = :appId
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getMessagesByAppPaged(serverId: Long, appId: Int, limit: Int = 50, offset: Int = 0): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE serverId = :serverId AND id = :id LIMIT 1")
    suspend fun getMessageById(serverId: Long, id: Long): MessageEntity?

    @Query("""
        SELECT * FROM messages 
        WHERE serverId = :serverId 
          AND (title LIKE '%' || :query || '%' ESCAPE '\\' OR message LIKE '%' || :query || '%' ESCAPE '\\')
        ORDER BY id DESC
        LIMIT 100
    """)
    fun searchMessages(serverId: Long, query: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE serverId = :serverId AND appId = :appId")
    fun getMessageCountForApp(serverId: Long, appId: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE serverId = :serverId AND isRead = 0")
    fun getUnreadCount(serverId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE serverId = :serverId")
    fun getMessageCount(serverId: Long): Flow<Int>



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isRead = 1 WHERE serverId = :serverId AND id = :id")
    suspend fun markAsRead(serverId: Long, id: Long)

    @Query("UPDATE messages SET isRead = 1 WHERE serverId = :serverId")
    suspend fun markAllAsRead(serverId: Long)



    @Query("DELETE FROM messages WHERE serverId = :serverId AND id = :id")
    suspend fun deleteMessage(serverId: Long, id: Long)

    @Query("DELETE FROM messages WHERE serverId = :serverId AND appId = :appId")
    suspend fun deleteMessagesByApp(serverId: Long, appId: Int)

    @Query("DELETE FROM messages WHERE serverId = :serverId AND appId = :appId AND id NOT IN (:remoteIds)")
    suspend fun deleteMissingForApp(serverId: Long, appId: Int, remoteIds: List<Long>)

    @Query("SELECT id FROM messages WHERE serverId = :serverId")
    suspend fun getMessageIds(serverId: Long): List<Long>

    @Query("SELECT id FROM messages WHERE serverId = :serverId AND isRead = 1")
    suspend fun getReadMessageIds(serverId: Long): List<Long>

    @Query("SELECT id FROM messages WHERE serverId = :serverId AND appId = :appId")
    suspend fun getMessageIdsForApp(serverId: Long, appId: Int): List<Long>

    @Query("SELECT id FROM messages WHERE serverId = :serverId AND appId = :appId AND isRead = 1")
    suspend fun getReadMessageIdsForApp(serverId: Long, appId: Int): List<Long>

    @Query("DELETE FROM messages WHERE serverId = :serverId AND id IN (:messageIds)")
    suspend fun deleteMessagesByIds(serverId: Long, messageIds: List<Long>)

    @Query("DELETE FROM messages WHERE serverId = :serverId AND appId = :appId AND id IN (:messageIds)")
    suspend fun deleteMessagesByIdsForApp(serverId: Long, appId: Int, messageIds: List<Long>)

    @Query("DELETE FROM messages WHERE serverId = :serverId")
    suspend fun deleteAllMessages(serverId: Long)

    @Query("DELETE FROM messages WHERE serverId = :serverId AND cachedAt < :olderThan")
    suspend fun evictOldMessages(serverId: Long, olderThan: Long)

    @Query("DELETE FROM messages WHERE serverId = :serverId AND id NOT IN (:remoteIds)")
    suspend fun deleteMissing(serverId: Long, remoteIds: List<Long>)
}

@Dao
interface ApplicationDao {

    @Query("SELECT * FROM applications WHERE serverId = :serverId ORDER BY name ASC")
    fun getApplications(serverId: Long): Flow<List<ApplicationEntity>>

    @Query("SELECT * FROM applications WHERE serverId = :serverId AND id = :id LIMIT 1")
    suspend fun getApplicationById(serverId: Long, id: Int): ApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplications(apps: List<ApplicationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(app: ApplicationEntity)

    @Query("DELETE FROM applications WHERE serverId = :serverId AND id = :id")
    suspend fun deleteApplication(serverId: Long, id: Int)

    @Query("DELETE FROM applications WHERE serverId = :serverId AND id NOT IN (:remoteIds)")
    suspend fun deleteMissing(serverId: Long, remoteIds: List<Int>)

    @Query("SELECT id FROM applications WHERE serverId = :serverId")
    suspend fun getApplicationIds(serverId: Long): List<Int>

    @Query("DELETE FROM applications WHERE serverId = :serverId AND id IN (:applicationIds)")
    suspend fun deleteApplicationsByIds(serverId: Long, applicationIds: List<Int>)

    @Query("DELETE FROM applications WHERE serverId = :serverId")
    suspend fun deleteAllForServer(serverId: Long)
}



@Database(
    entities = [
        ServerEntity::class,
        MessageEntity::class,
        ApplicationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GotifyDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun messageDao(): MessageDao
    abstract fun applicationDao(): ApplicationDao

    companion object {
        const val DATABASE_NAME = "gotify.db"
    }
}
