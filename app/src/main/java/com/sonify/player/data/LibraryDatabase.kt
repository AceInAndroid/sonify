package com.sonify.player.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["folderPath"]),
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val durationMs: Long = 0L,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val bitrateKbps: Int = 0,
    val format: String = "AUDIO",
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val coverPath: String? = null,
    val lrcPath: String? = null,
    val folderPath: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
) {
    val isHiRes: Boolean
        get() = sampleRate > 48000 || bitDepth > 16

    fun sampleRateLabel(): String = when {
        sampleRate <= 0 -> ""
        sampleRate % 1000 == 0 -> "${sampleRate / 1000}k"
        else -> "%.1fk".format(sampleRate / 1000.0)
    }

    fun formatBadgeShort(): String {
        val sr = sampleRateLabel()
        val bits = if (bitDepth > 0) "${bitDepth}b" else ""
        val detail = listOf(sr, bits).filter { it.isNotEmpty() }.joinToString("/")
        return if (detail.isNotEmpty()) "$format $detail" else format
    }

    fun formatBadgeFull(): String {
        val parts = mutableListOf(format)
        val sr = if (sampleRate > 0) {
            if (sampleRate % 1000 == 0) "${sampleRate / 1000}kHz" else "%.1fkHz".format(sampleRate / 1000.0)
        } else ""
        val bits = if (bitDepth > 0) "${bitDepth}bit" else ""
        val tech = listOf(sr, bits).filter { it.isNotEmpty() }.joinToString(" / ")
        if (tech.isNotEmpty()) parts += tech
        if (bitrateKbps > 0) parts += "${bitrateKbps} kbps"
        return parts.joinToString(" · ")
    }
}

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val trackPath: String,
    val positionMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class TrackFingerprint(
    val id: Long,
    val path: String,
    val fileSize: Long,
    val lastModified: Long,
    val isFavorite: Boolean,
    val addedAt: Long,
)

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY album COLLATE NOCASE ASC, discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): TrackEntity?

    @Query("SELECT id, path, fileSize, lastModified, isFavorite, addedAt FROM tracks")
    suspend fun getAllFingerprints(): List<TrackFingerprint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE path = :path")
    suspend fun setFavorite(path: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}

@Dao
interface PlaybackPositionDao {

    @Query("SELECT * FROM playback_positions WHERE trackPath = :path LIMIT 1")
    suspend fun get(path: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE trackPath = :path")
    suspend fun clear(path: String)
}

@Database(
    entities = [TrackEntity::class, PlaybackPositionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playbackPositionDao(): PlaybackPositionDao

    companion object {
        @Volatile
        private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "sonify_library.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
