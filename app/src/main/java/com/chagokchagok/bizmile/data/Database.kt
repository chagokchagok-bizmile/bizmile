package com.chagokchagok.bizmile.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One recorded trip. Enum-like columns are stored as their names. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val vin: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val startOdometerKm: Float,
    val endOdometerKm: Float,
    val startPlace: String?,
    val endPlace: String?,
    val startLat: Double?,
    val startLng: Double?,
    val endLat: Double?,
    val endLng: Double?,
    val stopovers: Int,
    /** Purpose name, or null while undecided. */
    val purpose: String?,
    /** ReviewState name. */
    val reviewState: String,
    val reason: String,
    val memo: String,
    /** SignalSource name: which data produced this record. */
    val source: String,
) {
    val distanceKm: Float get() = (endOdometerKm - startOdometerKm).coerceAtLeast(0f)
}

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float,
    val kind: String,
    val defaultPurpose: String,
    val defaultMemo: String,
)

@Entity(tableName = "handovers")
data class HandoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val vin: String?,
    val odometerKm: Float?,
    val verdict: String,
    /** One item per line: key \t label \t value \t level */
    val items: String,
    val source: String,
)

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun byId(id: String): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startTimeMillis DESC")
    fun all(): Flow<List<TripEntity>>

    @Query("UPDATE trips SET purpose = :purpose, reviewState = :state, memo = :memo WHERE id = :id")
    suspend fun setPurpose(id: String, purpose: String, state: String, memo: String)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM trips WHERE source = :source")
    suspend fun deleteBySource(source: String)
}

@Dao
interface PlaceDao {
    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Query("SELECT * FROM places ORDER BY id")
    fun all(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY id")
    suspend fun list(): List<PlaceEntity>

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface HandoverDao {
    @Insert
    suspend fun insert(report: HandoverEntity): Long

    @Query("SELECT * FROM handovers ORDER BY createdAtMillis DESC LIMIT 20")
    fun recent(): Flow<List<HandoverEntity>>
}

@Database(
    entities = [TripEntity::class, PlaceEntity::class, HandoverEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BizmileDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun places(): PlaceDao
    abstract fun handovers(): HandoverDao

    companion object {
        fun create(context: Context): BizmileDatabase =
            Room.databaseBuilder(context, BizmileDatabase::class.java, "bizmile.db").build()
    }
}
