package com.audoneout.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT * FROM tracks WHERE availability = 'Available' ORDER BY title COLLATE NOCASE")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT tracks.* FROM tracks INNER JOIN favorite_tracks ON tracks.id = favorite_tracks.trackId WHERE tracks.availability = 'Available' ORDER BY favorite_tracks.position, favorite_tracks.addedAtMillis DESC LIMIT :limit")
    fun observeFavoriteTracks(limit: Int = 12): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlists ORDER BY updatedAtMillis DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM radio_stations ORDER BY lastPlayedAtMillis DESC, name COLLATE NOCASE")
    fun observeRadioStations(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM scan_jobs WHERE status = 'Complete' ORDER BY finishedAtMillis DESC LIMIT 1")
    fun observeLatestCompletedScan(): Flow<ScanJobEntity?>

    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT DISTINCT tracks.* FROM tracks
        LEFT JOIN metadata_writebacks ON metadata_writebacks.trackId = tracks.id
        LEFT JOIN metadata_suggestions ON metadata_suggestions.trackId = tracks.id
            AND metadata_suggestions.status = 'Pending'
        WHERE tracks.enhancementStatus != 'Ready'
            OR metadata_suggestions.id IS NOT NULL
            OR metadata_writebacks.status IN ('Pending', 'Partial', 'Failed')
        ORDER BY tracks.dateAddedSeconds DESC
        """
    )
    fun observeInbox(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM analysis_results WHERE ignored = 0 ORDER BY severity DESC, createdAtMillis DESC")
    fun observeAnalysisResults(): Flow<List<AnalysisResultEntity>>

    @Query("SELECT * FROM metadata_suggestions WHERE status = 'Pending' ORDER BY createdAtMillis DESC")
    fun observeMetadataSuggestions(): Flow<List<MetadataSuggestionEntity>>

    @Query("SELECT * FROM user_confirmed_metadata")
    fun observeUserConfirmedMetadata(): Flow<List<UserConfirmedMetadataEntity>>

    @Query("SELECT * FROM metadata_writebacks")
    fun observeMetadataWritebacks(): Flow<List<MetadataWritebackEntity>>

    @Query("SELECT * FROM folder_blacklist_rules ORDER BY defaultSuggestion DESC, label COLLATE NOCASE")
    fun observeBlacklistRules(): Flow<List<FolderBlacklistRuleEntity>>

    @Query("SELECT * FROM music_roots ORDER BY displayName COLLATE NOCASE")
    fun observeMusicRoots(): Flow<List<MusicRootEntity>>

    @Query("SELECT * FROM music_roots ORDER BY displayName COLLATE NOCASE")
    suspend fun observeMusicRootsOnce(): List<MusicRootEntity>

    @Query("SELECT * FROM music_roots WHERE id = :rootId LIMIT 1")
    suspend fun findMusicRoot(rootId: Long): MusicRootEntity?

    @Query("SELECT * FROM tracks WHERE volumeName = :volumeName AND mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun findTrackByMediaStoreId(volumeName: String, mediaStoreId: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun findTrack(trackId: Long): TrackEntity?

    @Query("SELECT * FROM metadata_suggestions WHERE id = :suggestionId LIMIT 1")
    suspend fun findMetadataSuggestion(suggestionId: Long): MetadataSuggestionEntity?

    @Query("SELECT * FROM metadata_suggestions WHERE status = 'Pending' AND confidence >= :minimumConfidence AND field IN ('title', 'artist', 'album') ORDER BY trackId, confidence DESC")
    suspend fun getSafePendingMetadataSuggestions(minimumConfidence: Float = 0.8f): List<MetadataSuggestionEntity>

    @Query("SELECT * FROM user_confirmed_metadata WHERE trackId = :trackId LIMIT 1")
    suspend fun findUserConfirmedMetadata(trackId: Long): UserConfirmedMetadataEntity?

    @Query("SELECT * FROM enriched_metadata")
    suspend fun getAllEnrichedMetadataOnce(): List<EnrichedMetadataEntity>

    @Query("SELECT * FROM enriched_metadata WHERE trackId = :trackId")
    suspend fun getEnrichedMetadataForTrack(trackId: Long): List<EnrichedMetadataEntity>

    @Query("SELECT * FROM original_metadata WHERE trackId = :trackId LIMIT 1")
    suspend fun findOriginalMetadata(trackId: Long): OriginalMetadataEntity?

    @Query("SELECT * FROM track_availability WHERE trackId = :trackId LIMIT 1")
    suspend fun findTrackAvailability(trackId: Long): TrackAvailabilityEntity?

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT tracks.* FROM tracks INNER JOIN favorite_tracks ON tracks.id = favorite_tracks.trackId WHERE tracks.availability = 'Available' ORDER BY favorite_tracks.position, favorite_tracks.addedAtMillis DESC")
    suspend fun getFavoriteTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM listening_events ORDER BY occurredAtMillis DESC LIMIT :limit")
    suspend fun getListeningEventsOnce(limit: Int = 1000): List<ListeningEventEntity>

    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_track_cross_ref ON tracks.id = playlist_track_cross_ref.trackId WHERE playlist_track_cross_ref.playlistId = :playlistId AND tracks.availability = 'Available' AND tracks.enhancementStatus != 'Excluded' ORDER BY playlist_track_cross_ref.position")
    suspend fun getPlaylistTracksOnce(playlistId: Long): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks INNER JOIN playlist_track_cross_ref ON tracks.id = playlist_track_cross_ref.trackId WHERE playlist_track_cross_ref.playlistId = :playlistId AND (tracks.availability != 'Available' OR tracks.enhancementStatus = 'Excluded')")
    suspend fun countUnavailablePlaylistTracks(playlistId: Long): Int

    @Query("SELECT * FROM playlist_track_cross_ref WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getPlaylistRefsOnce(playlistId: Long): List<PlaylistTrackCrossRef>

    @Query("SELECT * FROM folder_blacklist_rules WHERE enabled = 1")
    suspend fun getEnabledBlacklistRules(): List<FolderBlacklistRuleEntity>

    @Query("SELECT * FROM folder_blacklist_rules")
    suspend fun getAllBlacklistRulesOnce(): List<FolderBlacklistRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun updateTracks(tracks: List<TrackEntity>)

    @Transaction
    suspend fun upsertTracks(tracks: List<TrackEntity>): List<Long> {
        val insertedIds = insertTracks(tracks)
        val updates = tracks.filterIndexed { index, _ -> insertedIds[index] == -1L }
        if (updates.isNotEmpty()) updateTracks(updates)
        return insertedIds.mapIndexed { index, insertedId ->
            if (insertedId == -1L) tracks[index].id else insertedId
        }
    }

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

    @Query("DELETE FROM analysis_results")
    suspend fun deleteAnalysisResults()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(refs: List<PlaylistTrackCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistRule(rule: PlaylistRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningEvent(event: ListeningEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRadioStation(station: RadioStationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrichedMetadata(values: List<EnrichedMetadataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataSuggestions(values: List<MetadataSuggestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOriginalMetadata(value: OriginalMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserConfirmedMetadata(value: UserConfirmedMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadataWriteback(value: MetadataWritebackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackAvailability(value: TrackAvailabilityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExternalMatch(value: ExternalMatchEntity)

    @Query("UPDATE tracks SET availability = :availability, enhancementStatus = :enhancementStatus WHERE id = :trackId")
    suspend fun updateTrackState(trackId: Long, availability: String, enhancementStatus: String)

    @Query("UPDATE folder_blacklist_rules SET enabled = :enabled WHERE id = :ruleId")
    suspend fun updateBlacklistRuleEnabled(ruleId: Long, enabled: Boolean)

    @Query("UPDATE folder_blacklist_rules SET excludedPreviewCount = :count WHERE id = :ruleId")
    suspend fun updateBlacklistPreviewCount(ruleId: Long, count: Int)

    @Query("DELETE FROM folder_blacklist_rules WHERE id = :ruleId")
    suspend fun deleteBlacklistRule(ruleId: Long)

    @Query("UPDATE music_roots SET included = :included WHERE id = :rootId")
    suspend fun updateMusicRootIncluded(rootId: Long, included: Boolean)

    @Query("UPDATE music_roots SET scanStatus = :scanStatus, lastScanTimeMillis = :lastScanTimeMillis, indexedTrackCount = :indexedTrackCount, storageBytes = :storageBytes WHERE id = :rootId")
    suspend fun updateMusicRootScanSummary(
        rootId: Long,
        scanStatus: String,
        lastScanTimeMillis: Long,
        indexedTrackCount: Int,
        storageBytes: Long
    )

    @Query("UPDATE music_roots SET scanStatus = :scanStatus WHERE id = :rootId")
    suspend fun updateMusicRootScanStatus(rootId: Long, scanStatus: String)

    @Query("DELETE FROM music_roots WHERE id = :rootId")
    suspend fun deleteMusicRoot(rootId: Long)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: Long): Boolean

    @Query("DELETE FROM radio_stations WHERE id = :stationId")
    suspend fun deleteRadioStation(stationId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAtMillis = :updatedAtMillis WHERE id = :playlistId")
    suspend fun updatePlaylist(playlistId: Long, name: String, description: String, updatedAtMillis: Long)

    @Query("UPDATE tracks SET availability = 'Missing', enhancementStatus = 'NeedsReview' WHERE rootId IS NULL AND contentUri NOT IN (:seenContentUris)")
    suspend fun markUnseenGlobalTracksMissing(seenContentUris: List<String>)

    @Query("UPDATE tracks SET availability = 'Missing', enhancementStatus = 'NeedsReview' WHERE rootId IS NULL")
    suspend fun markAllGlobalTracksMissing()

    @Query("UPDATE tracks SET availability = 'Missing', enhancementStatus = 'NeedsReview' WHERE rootId = :rootId AND contentUri NOT IN (:seenContentUris)")
    suspend fun markUnseenRootTracksMissing(rootId: Long, seenContentUris: List<String>)

    @Query("UPDATE tracks SET availability = 'Missing', enhancementStatus = 'NeedsReview' WHERE rootId = :rootId")
    suspend fun markAllRootTracksMissing(rootId: Long)

    @Query("UPDATE tracks SET availability = 'Missing', enhancementStatus = 'NeedsReview', rootId = NULL WHERE rootId = :rootId")
    suspend fun markTracksForRemovedRootMissing(rootId: Long)

    @Query("UPDATE radio_stations SET lastPlayedAtMillis = :playedAtMillis WHERE id = :stationId")
    suspend fun markRadioStationPlayed(stationId: Long, playedAtMillis: Long)

    @Query("UPDATE analysis_results SET ignored = 1 WHERE id = :resultId")
    suspend fun ignoreAnalysisResult(resultId: Long)

    @Query("UPDATE metadata_suggestions SET status = :status WHERE id = :suggestionId")
    suspend fun updateMetadataSuggestionStatus(suggestionId: Long, status: String)

    @Query("DELETE FROM metadata_suggestions WHERE trackId = :trackId AND source = :source AND status = 'Pending'")
    suspend fun deletePendingMetadataSuggestions(trackId: Long, source: String)

    @Query("DELETE FROM enriched_metadata WHERE trackId = :trackId AND source = :source")
    suspend fun deleteEnrichedMetadata(trackId: Long, source: String)

    @Query("UPDATE enriched_metadata SET confirmed = 1 WHERE trackId = :trackId AND LOWER(key) = LOWER(:key)")
    suspend fun markEnrichedMetadataFieldConfirmed(trackId: Long, key: String)

    @Query("UPDATE enriched_metadata SET confirmed = 1 WHERE trackId = :trackId")
    suspend fun markAllEnrichedMetadataConfirmed(trackId: Long)

    @Query("UPDATE tracks SET title = :value, enhancementStatus = 'Ready' WHERE id = :trackId")
    suspend fun acceptSuggestedTitle(trackId: Long, value: String)

    @Query("UPDATE tracks SET artist = :value, enhancementStatus = 'Ready' WHERE id = :trackId")
    suspend fun acceptSuggestedArtist(trackId: Long, value: String)

    @Query("UPDATE tracks SET album = :value, enhancementStatus = 'Ready' WHERE id = :trackId")
    suspend fun acceptSuggestedAlbum(trackId: Long, value: String)

    @Query("UPDATE tracks SET genre = :value, enhancementStatus = 'Ready' WHERE id = :trackId")
    suspend fun acceptSuggestedGenre(trackId: Long, value: String)

    @Query(
        """
        UPDATE tracks SET
            title = :title,
            artist = :artist,
            album = :album,
            albumArtist = :albumArtist,
            genre = :genre,
            year = :year,
            trackNumber = :trackNumber,
            discNumber = :discNumber,
            language = :language,
            mood = :mood,
            energy = :energy,
            discoveryTags = :discoveryTags,
            enhancementStatus = 'Ready'
        WHERE id = :trackId
        """
    )
    suspend fun updateConfirmedTrackMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        genre: String,
        year: Int?,
        trackNumber: Int?,
        discNumber: Int?,
        language: String,
        mood: String,
        energy: Int?,
        discoveryTags: String
    )

    @Query(
        """
        UPDATE tracks SET
            language = :language,
            mood = :mood,
            energy = :energy,
            discoveryTags = :discoveryTags
        WHERE id = :trackId
        """
    )
    suspend fun updateEffectiveDiscoveryMetadata(
        trackId: Long,
        language: String,
        mood: String,
        energy: Int?,
        discoveryTags: String
    )

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

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrack(playlistId: Long, trackId: Long)

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
