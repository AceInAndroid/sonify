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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "subsonic_servers")
data class SubsonicServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val username: String,
    val encryptedPassword: String,
    val useLegacyAuth: Boolean = false,
    val allowSelfSignedTls: Boolean = false,
    val musicFolderId: String? = null,
    val serverType: String = "",
    val serverVersion: String = "",
    val isActive: Boolean = false,
    val lastConnectedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "subsonic_tracks",
    indices = [
        Index(value = ["serverId"]),
    ]
)
data class SubsonicTrackCacheEntity(
    @PrimaryKey val path: String,
    val serverId: Long,
    val remoteId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val albumId: String = "",
    val artistId: String = "",
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val durationMs: Long = 0L,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val bitrateKbps: Int = 0,
    val format: String = "AUDIO",
    val fileSize: Long = 0L,
    val coverPath: String? = null,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = 0,
        path = path,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationMs = durationMs,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        channels = channels,
        bitrateKbps = bitrateKbps,
        format = format,
        fileSize = fileSize,
        lastModified = updatedAt,
        coverPath = coverPath,
        lrcPath = null,
        folderPath = "subsonic://$serverId",
        addedAt = updatedAt,
        isFavorite = isFavorite,
    )
}

@Dao
interface SubsonicServerDao {

    @Query("SELECT * FROM subsonic_servers ORDER BY isActive DESC, lastConnectedAt DESC, id ASC")
    fun observeAll(): Flow<List<SubsonicServerEntity>>

    @Query("SELECT * FROM subsonic_servers ORDER BY isActive DESC, lastConnectedAt DESC, id ASC")
    suspend fun getAll(): List<SubsonicServerEntity>

    @Query("SELECT * FROM subsonic_servers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SubsonicServerEntity?

    @Query("SELECT * FROM subsonic_servers WHERE isActive = 1 LIMIT 1")
    suspend fun findActive(): SubsonicServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: SubsonicServerEntity): Long

    @Query("UPDATE subsonic_servers SET isActive = CASE WHEN id = :serverId THEN 1 ELSE 0 END")
    suspend fun setActive(serverId: Long)

    @Query("UPDATE subsonic_servers SET musicFolderId = :folderId WHERE id = :serverId")
    suspend fun setMusicFolder(serverId: Long, folderId: String?)

    @Query("UPDATE subsonic_servers SET lastConnectedAt = :ts, serverType = :type, serverVersion = :version WHERE id = :serverId")
    suspend fun updateConnectionInfo(serverId: Long, ts: Long, type: String, version: String)

    @Query("DELETE FROM subsonic_servers WHERE id = :serverId")
    suspend fun deleteById(serverId: Long)
}

@Dao
interface SubsonicTrackCacheDao {

    @Query("SELECT * FROM subsonic_tracks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SubsonicTrackCacheEntity>>

    @Query("SELECT * FROM subsonic_tracks WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): SubsonicTrackCacheEntity?

    @Query("SELECT * FROM subsonic_tracks WHERE serverId = :serverId AND albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    suspend fun findByAlbum(serverId: Long, albumId: String): List<SubsonicTrackCacheEntity>

    @Query("SELECT * FROM subsonic_tracks WHERE serverId = :serverId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun findRecentByServer(serverId: Long, limit: Int = 50): List<SubsonicTrackCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<SubsonicTrackCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: SubsonicTrackCacheEntity)

    @Query("UPDATE subsonic_tracks SET isFavorite = :favorite WHERE path = :path")
    suspend fun setFavorite(path: String, favorite: Boolean)

    @Query("DELETE FROM subsonic_tracks WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: Long)
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val customCoverPath: String? = null,
    val sortField: String = "CUSTOM",
    val sortAscending: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_items",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "trackPath"], unique = true),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackPath: String,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "downloaded_tracks",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["localFilePath"]),
    ],
)
data class DownloadedTrackEntity(
    @PrimaryKey val remotePath: String,
    val serverId: Long,
    val remoteId: String,
    val localFilePath: String,
    val localCoverPath: String? = null,
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
    val quality: String = "RAW",
    val downloadedAt: Long = System.currentTimeMillis(),
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = 0,
        path = remotePath,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationMs = durationMs,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        channels = channels,
        bitrateKbps = bitrateKbps,
        format = format,
        fileSize = fileSize,
        lastModified = downloadedAt,
        coverPath = localCoverPath,
        lrcPath = null,
        folderPath = localFilePath.substringBeforeLast('/', ""),
        addedAt = downloadedAt,
        isFavorite = false,
    )
}

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC, id DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun findPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description, customCoverPath = :coverPath, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistMeta(
        playlistId: Long,
        name: String,
        description: String,
        coverPath: String?,
        updatedAt: Long,
    )

    @Query("UPDATE playlists SET sortField = :sortField, sortAscending = :ascending, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistSort(
        playlistId: Long,
        sortField: String,
        ascending: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT * FROM playlist_items ORDER BY playlistId ASC, position ASC, addedAt ASC")
    fun observeAllItems(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    suspend fun getItems(playlistId: Long): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemsIgnore(items: List<PlaylistItemEntity>): List<Long>

    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId")
    suspend fun updateItemPosition(itemId: Long, position: Int)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackPath IN (:trackPaths)")
    suspend fun deleteItemsByPaths(playlistId: Long, trackPaths: List<String>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteAllItemsForPlaylist(playlistId: Long)
}

@Dao
interface DownloadedTrackDao {

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE remotePath = :remotePath LIMIT 1")
    suspend fun findByRemotePath(remotePath: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE remotePath = :remotePath")
    suspend fun deleteByRemotePath(remotePath: String)

    @Query("DELETE FROM downloaded_tracks WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: Long)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun deleteAll()
}

@Database(
    entities = [
        TrackEntity::class,
        PlaybackPositionEntity::class,
        SubsonicServerEntity::class,
        SubsonicTrackCacheEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        DownloadedTrackEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun subsonicServerDao(): SubsonicServerDao
    abstract fun subsonicTrackCacheDao(): SubsonicTrackCacheDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadedTrackDao(): DownloadedTrackDao

    companion object {
        @Volatile
        private var instance: LibraryDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subsonic_servers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `encryptedPassword` TEXT NOT NULL,
                        `useLegacyAuth` INTEGER NOT NULL,
                        `allowSelfSignedTls` INTEGER NOT NULL,
                        `musicFolderId` TEXT,
                        `serverType` TEXT NOT NULL,
                        `serverVersion` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `lastConnectedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subsonic_tracks` (
                        `path` TEXT NOT NULL,
                        `serverId` INTEGER NOT NULL,
                        `remoteId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumArtist` TEXT NOT NULL,
                        `albumId` TEXT NOT NULL,
                        `artistId` TEXT NOT NULL,
                        `trackNumber` INTEGER NOT NULL,
                        `discNumber` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `sampleRate` INTEGER NOT NULL,
                        `bitDepth` INTEGER NOT NULL,
                        `channels` INTEGER NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `format` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `coverPath` TEXT,
                        `isFavorite` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`path`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subsonic_tracks_serverId` ON `subsonic_tracks` (`serverId`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `customCoverPath` TEXT,
                        `sortField` TEXT NOT NULL,
                        `sortAscending` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `trackPath` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_items_playlistId_trackPath` ON `playlist_items` (`playlistId`, `trackPath`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloaded_tracks` (
                        `remotePath` TEXT NOT NULL,
                        `serverId` INTEGER NOT NULL,
                        `remoteId` TEXT NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `localCoverPath` TEXT,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumArtist` TEXT NOT NULL,
                        `trackNumber` INTEGER NOT NULL,
                        `discNumber` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `sampleRate` INTEGER NOT NULL,
                        `bitDepth` INTEGER NOT NULL,
                        `channels` INTEGER NOT NULL,
                        `bitrateKbps` INTEGER NOT NULL,
                        `format` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `quality` TEXT NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`remotePath`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_serverId` ON `downloaded_tracks` (`serverId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_localFilePath` ON `downloaded_tracks` (`localFilePath`)"
                )
            }
        }

        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "sonify_library.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
