package com.audoneout.app.handoff

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.audoneout.app.data.TrackEntity
import com.audoneout.app.playlist.PlaylistExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalPlayerLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistExporter: PlaylistExporter
) {
    fun openTrack(track: TrackEntity): Result<Unit> = launch(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(track.contentUri), track.mimeType.ifBlank { "audio/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        "Open ${track.title} in a music player"
    )

    fun openPlaylist(name: String, tracks: List<TrackEntity>): Result<Unit> = runCatching {
        require(tracks.isNotEmpty()) { "This mix has no local tracks" }
        if (tracks.size == 1) {
            openTrack(tracks.first()).getOrThrow()
            return@runCatching
        }
        val directory = File(context.cacheDir, "handoffs").apply { mkdirs() }
        val file = File(directory, "${name.safeFileName()}-${System.currentTimeMillis()}.m3u8")
        file.writeText(playlistExporter.toM3u8(name, tracks))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.apple.mpegurl")
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, name)
        }
        launch(intent, "Open $name in a music player").getOrThrow()
    }

    fun openStream(name: String, streamUrl: String): Result<Unit> = launch(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(streamUrl), "audio/*")
            putExtra(Intent.EXTRA_TITLE, name)
        },
        "Open $name in a music player"
    )

    fun openWeb(url: String, label: String): Result<Unit> = launch(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
        label
    )

    private fun launch(intent: Intent, chooserTitle: String): Result<Unit> = runCatching {
        val chooser = Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "AudOneOut mix" }
