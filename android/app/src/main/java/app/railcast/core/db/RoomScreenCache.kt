package app.railcast.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "screen_cache")
data class ScreenCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val serverFetchedAt: String,
    val cachedAtEpochMs: Long,
    val ttlSeconds: Int,
)

@Dao
interface ScreenCacheDao {
    @Query("SELECT * FROM screen_cache WHERE `key` = :key")
    suspend fun get(key: String): ScreenCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ScreenCacheEntity)

    @Query("DELETE FROM screen_cache")
    suspend fun clear()
}

@Database(entities = [ScreenCacheEntity::class], version = 1, exportSchema = false)
abstract class RailcastDatabase : RoomDatabase() {
    abstract fun screenCacheDao(): ScreenCacheDao
}

/** Room-backed ScreenCache (offline cache, FR-9.1/9.2). */
class RoomScreenCache(private val dao: ScreenCacheDao) : ScreenCache {
    override suspend fun get(key: String): CachedScreen? =
        dao.get(key)?.let { CachedScreen(it.key, it.json, it.serverFetchedAt, it.cachedAtEpochMs, it.ttlSeconds) }

    override suspend fun put(entry: CachedScreen) =
        dao.put(ScreenCacheEntity(entry.key, entry.json, entry.serverFetchedAt, entry.cachedAtEpochMs, entry.ttlSeconds))

    override suspend fun clear() = dao.clear()
}
