package com.audoneout.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT COUNT(*) FROM tracks WHERE availability = 'Available'")
    fun observeTrackCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT album) FROM tracks WHERE availability = 'Available' AND album != ''")
    fun observeAlbumCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT artist) FROM tracks WHERE availability = 'Available' AND artist != ''")
    fun observeArtistCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT folder) FROM tracks WHERE availability = 'Available' AND folder != ''")
    fun observeFolderCount(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE availability = 'Available' ORDER BY title COLLATE NOCASE LIMIT :limit")
    fun observeTracks(limit: Int = 250): Flow<List<TrackEntity>>

    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE LIMIT :limit")
    fun observeAlbums(limit: Int = 250): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE LIMIT :limit")
    fun observeArtists(limit: Int = 250): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE LIMIT :limit")
    fun observeFolders(limit: Int = 250): Flow<List<FolderEntity>>

    @Query("SELECT * FROM tracks WHERE enhancementStatus != 'Ready' ORDER BY dateAddedSeconds DESC LIMIT :limit")
    fun observeInbox(limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM folder_blacklist_rules ORDER BY defaultSuggestion DESC, label COLLATE NOCASE")
    fun observeBlacklistRules(): Flow<List<FolderBlacklistRuleEntity>>

    @Query("SELECT * FROM music_roots ORDER BY displayName COLLATE NOCASE")
    fun observeMusicRoots(): Flow<List<MusicRootEntity>>

    @Query("SELECT * FROM tracks WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun findTrackByMediaStoreId(mediaStoreId: Long): TrackEntity?

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM folder_blacklist_rules WHERE enabled = 1")
    suspend fun getEnabledBlacklistRules(): List<FolderBlacklistRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMusicRoot(root: MusicRootEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlacklistRule(rule: FolderBlacklistRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlacklistRules(rules: List<FolderBlacklistRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanJob(job: ScanJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanChanges(changes: List<ScanChangeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisResults(results: List<AnalysisResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(refs: List<PlaylistTrackCrossRef>)

    @Query("UPDATE tracks SET availability = :availability, enhancementStatus = :enhancementStatus WHERE id = :trackId")
    suspend fun updateTrackState(trackId: Long, availability: String, enhancementStatus: String)

    @Query("UPDATE scan_jobs SET status = :status, finishedAtMillis = :finishedAtMillis, tracksFound = :tracksFound, newTracks = :newTracks, updatedTracks = :updatedTracks, excludedTracks = :excludedTracks, failedTracks = :failedTracks WHERE id = :scanJobId")
    suspend fun finishScanJob(
        scanJobId: Long,
        status: String,
        finishedAtMillis: Long,
        tracksFound: Int,
        newTracks: Int,
        updatedTracks: Int,
        excludedTracks: Int,
        failedTracks: Int
    )

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: Long, refs: List<PlaylistTrackCrossRef>) {
        deletePlaylistTracks(playlistId)
        insertPlaylistTracks(refs)
    }

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: Long)

    @Transaction
    suspend fun replaceLibraryFacets(
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        folders: List<FolderEntity>
    ) {
        deleteAlbums()
        deleteArtists()
        deleteFolders()
        upsertAlbums(albums)
        upsertArtists(artists)
        upsertFolders(folders)
    }

    @Query("DELETE FROM albums")
    suspend fun deleteAlbums()

    @Query("DELETE FROM artists")
    suspend fun deleteArtists()

    @Query("DELETE FROM folders")
    suspend fun deleteFolders()
}
