package tv.jetflix.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How the player should open a stream after asking the server what it can do. */
sealed interface PlaybackChoice {
    data class Direct(val url: String, val mediaSourceId: String, val subtitles: List<SubtitleTrack> = emptyList()) : PlaybackChoice
    data class Hls(val url: String, val mediaSourceId: String, val subtitles: List<SubtitleTrack> = emptyList()) : PlaybackChoice
    data class Unplayable(val reason: String) : PlaybackChoice
}

/** A text subtitle the server offers as a separate file; the player loads it next to the video. */
data class SubtitleTrack(val url: String, val mimeType: String, val language: String?, val label: String?)

/** Codec names in Jellyfin's vocabulary that this device can decode. */
data class CodecSupport(val video: List<String>, val audio: List<String>) {
    companion object {
        val Conservative = CodecSupport(listOf("h264"), listOf("aac", "mp3"))

        /** Ask the platform decoders. Falls back to [Conservative] if the query fails. */
        fun detect(): CodecSupport = try {
            val mimes = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos
                .filter { !it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .map { it.lowercase() }
                .toSet()
            val video = buildList {
                if ("video/avc" in mimes) add("h264")
                if ("video/hevc" in mimes) add("hevc")
                if ("video/x-vnd.on2.vp9" in mimes) add("vp9")
                if ("video/av01" in mimes) add("av1")
            }.ifEmpty { Conservative.video }
            val audio = buildList {
                add("aac"); add("mp3")
                if ("audio/ac3" in mimes) add("ac3")
                if ("audio/eac3" in mimes) add("eac3")
                if ("audio/opus" in mimes) add("opus")
                if ("audio/flac" in mimes) add("flac")
            }
            CodecSupport(video, audio)
        } catch (_: Exception) {
            Conservative
        }
    }
}

object PlaybackNegotiator {
    private const val DIRECT_CONTAINERS = "mp4,m4v,mkv,webm"
    val TEXT_SUBTITLE_FORMATS = listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa")

    private fun mimeFor(codec: String?): String? = when (codec?.lowercase()) {
        "srt", "subrip" -> "application/x-subrip"
        "vtt", "webvtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> null
    }

    /**
     * Subtitle files the server will serve separately. For direct play only real sidecar files are added
     * (tracks inside the container are read by the player itself, so adding them again would duplicate rows);
     * for a transcode every server-delivered text track is added because the transcode drops embedded ones.
     */
    fun externalSubtitles(source: MediaSourceInfo, base: String, directPlay: Boolean): List<SubtitleTrack> =
        source.MediaStreams.mapNotNull { s ->
            if (s.Type != "Subtitle" || s.DeliveryMethod != "External" || s.DeliveryUrl.isNullOrBlank()) return@mapNotNull null
            if (directPlay && s.IsExternal != true) return@mapNotNull null
            val mime = mimeFor(s.Codec) ?: return@mapNotNull null
            val rel = s.DeliveryUrl
            SubtitleTrack(if (rel.startsWith("http")) rel else base + (if (rel.startsWith("/")) rel else "/$rel"), mime, s.Language, s.DisplayTitle)
        }

    fun playbackInfoBody(userId: String, startTicks: Long, caps: CodecSupport = CodecSupport.Conservative): JsonObject = buildJsonObject {
        put("UserId", userId)
        put("StartTimeTicks", startTicks)
        put("EnableDirectPlay", true)
        put("EnableDirectStream", true)
        put("EnableTranscoding", true)
        put("MaxStreamingBitrate", 40_000_000)
        put("DeviceProfile", deviceProfile(caps))
    }

    fun deviceProfile(caps: CodecSupport = CodecSupport.Conservative): JsonObject = buildJsonObject {
        put("Name", "Jetflix Android TV")
        put("MaxStreamingBitrate", 40_000_000)
        put("DirectPlayProfiles", buildJsonArray {
            add(buildJsonObject {
                put("Type", "Video")
                put("Container", DIRECT_CONTAINERS)
                put("VideoCodec", caps.video.joinToString(","))
                put("AudioCodec", caps.audio.joinToString(","))
            })
        })
        put("TranscodingProfiles", buildJsonArray {
            add(buildJsonObject {
                put("Type", "Video")
                put("Container", "ts")
                put("Protocol", "hls")
                put("VideoCodec", "h264")
                put("AudioCodec", "aac,ac3")
                put("Context", "Streaming")
                put("MaxAudioChannels", "6")
                put("BreakOnNonKeyFrames", true)
            })
        })
        // Text formats only: ExoPlayer reads SubRip, WebVTT and SSA/ASS. Picture subtitles (PGS, VobSub) are not offered.
        put("SubtitleProfiles", buildJsonArray {
            TEXT_SUBTITLE_FORMATS.forEach { f ->
                add(buildJsonObject { put("Format", f); put("Method", "External") })
            }
        })
    }

    /** Pick the first media source the server says is playable; null-safe on bad responses. */
    fun choose(info: PlaybackInfoResponse?, base: String, itemId: String): PlaybackChoice {
        val sources = info?.MediaSources.orEmpty()
        if (sources.isEmpty()) return PlaybackChoice.Unplayable("The server returned no playable media source.")
        sources.firstOrNull { it.SupportsDirectPlay == true || it.SupportsDirectStream == true }?.let {
            val id = it.Id ?: itemId
            return PlaybackChoice.Direct("$base/Videos/$itemId/stream?static=true&mediaSourceId=$id", id, externalSubtitles(it, base, directPlay = true))
        }
        sources.firstOrNull { it.SupportsTranscoding == true && !it.TranscodingUrl.isNullOrBlank() }?.let {
            val rel = it.TranscodingUrl!!
            val url = if (rel.startsWith("http")) rel else base + (if (rel.startsWith("/")) rel else "/$rel")
            return PlaybackChoice.Hls(url, it.Id ?: itemId, externalSubtitles(it, base, directPlay = false))
        }
        return PlaybackChoice.Unplayable("This file can't be played or converted for this TV.")
    }
}
