package tv.jetflix.app.data

import android.media.MediaCodecInfo.CodecProfileLevel as CPL
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

/**
 * What this device can decode, in Jellyfin's vocabulary. Built from the platform decoder list so the server only
 * direct plays what the TV really handles and transcodes the rest. Levels use ffprobe numbering (H.264 4.1 = 41,
 * HEVC 5.1 = 153). A value of 0 or an empty map means "unknown", and no limit is sent for it.
 */
data class CodecSupport(
    val video: List<String>,
    val audio: List<String>,
    val hevcMain10: Boolean = false,
    val hevcMainLevel: Int = 0,
    val hevcMain10Level: Int = 0,
    val avcHigh10: Boolean = false,
    val avcLevel: Int = 0,
    val avcHigh10Level: Int = 0,
    val maxResolution: Map<String, Pair<Int, Int>> = emptyMap(),
    val hdr10: Boolean = false,
    val hdr10Plus: Boolean = false,
    val dolbyVision: Boolean = false,
    val dolbyVisionEl: Boolean = false,
    val hevcDoviHdr10PlusBug: Boolean = false,
) {
    companion object {
        val Conservative = CodecSupport(listOf("h264"), listOf("aac", "mp3"))

        private val FIRE_TV_DOVI_HDR10PLUS_BUG = setOf("AFTKRT", "AFTKA", "AFTKM", "AFTMM")

        private val AVC_LEVELS = listOf(
            CPL.AVCLevel1 to 10,
            CPL.AVCLevel11 to 11,
            CPL.AVCLevel12 to 12,
            CPL.AVCLevel13 to 13,
            CPL.AVCLevel2 to 20,
            CPL.AVCLevel21 to 21,
            CPL.AVCLevel22 to 22,
            CPL.AVCLevel3 to 30,
            CPL.AVCLevel31 to 31,
            CPL.AVCLevel32 to 32,
            CPL.AVCLevel4 to 40,
            CPL.AVCLevel41 to 41,
            CPL.AVCLevel42 to 42,
            CPL.AVCLevel5 to 50,
            CPL.AVCLevel51 to 51,
            CPL.AVCLevel52 to 52,
        )

        private val HEVC_LEVELS = listOf(
            CPL.HEVCMainTierLevel1 to 30,
            CPL.HEVCMainTierLevel2 to 60,
            CPL.HEVCMainTierLevel21 to 63,
            CPL.HEVCMainTierLevel3 to 90,
            CPL.HEVCMainTierLevel31 to 93,
            CPL.HEVCMainTierLevel4 to 120,
            CPL.HEVCMainTierLevel41 to 123,
            CPL.HEVCMainTierLevel5 to 150,
            CPL.HEVCMainTierLevel51 to 153,
            CPL.HEVCMainTierLevel52 to 156,
            CPL.HEVCMainTierLevel6 to 180,
            CPL.HEVCMainTierLevel61 to 183,
            CPL.HEVCMainTierLevel62 to 186,
        )

        /** Ask the platform decoders. Falls back to [Conservative] if the query fails. */
        fun detect(): CodecSupport = try {
            val all = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder }
            // Video: hardware decoders only. A software 4K decoder on a TV box stutters, so the server should transcode instead.
            val hw = all.filter { android.os.Build.VERSION.SDK_INT < 29 || !it.isSoftwareOnly }

            fun caps(info: android.media.MediaCodecInfo, mime: String) =
                try { info.getCapabilitiesForType(mime) } catch (_: Exception) { null }
            fun hasMime(mime: String) = hw.any { i -> i.supportedTypes.any { it.equals(mime, true) } }
            fun level(mime: String, profile: Int): Int = hw.mapNotNull { caps(it, mime) }
                .flatMap { it.profileLevels.asList() }.filter { it.profile == profile }.maxOfOrNull { it.level } ?: 0
            fun hasProfile(mime: String, profile: Int, minLevel: Int) = hw.mapNotNull { caps(it, mime) }
                .any { c -> c.profileLevels.any { it.profile == profile && it.level >= minLevel } }
            fun mapLevel(table: List<Pair<Int, Int>>, v: Int) = table.lastOrNull { v >= it.first }?.second ?: 0
            fun maxRes(mime: String): Pair<Int, Int>? {
                val r = hw.mapNotNull { caps(it, mime)?.videoCapabilities }
                    .mapNotNull { vc -> (vc.supportedWidths?.upper ?: return@mapNotNull null) to (vc.supportedHeights?.upper ?: return@mapNotNull null) }
                val w = r.maxOfOrNull { it.first } ?: 0
                val h = r.maxOfOrNull { it.second } ?: 0
                return if (w > 0 && h > 0) w to h else null
            }

            val video = buildList {
                if (hasMime("video/avc")) add("h264")
                if (hasMime("video/hevc")) add("hevc")
                if (hasMime("video/x-vnd.on2.vp9")) add("vp9")
                if (hasMime("video/av01")) add("av1")
                if (hasMime("video/x-vnd.on2.vp8")) add("vp8")
                if (hasMime("video/mpeg2")) { add("mpeg2video"); add("mpeg1video") }
                if (hasMime("video/wvc1")) add("vc1")
                if (hasMime("video/mp4v-es")) add("mpeg4")
            }.ifEmpty { Conservative.video }

            val audioMimes = all.flatMap { it.supportedTypes.toList() }.map { it.lowercase() }.toSet()
            val audio = buildList {
                add("aac"); add("mp3")
                if ("audio/ac3" in audioMimes) add("ac3")
                if ("audio/eac3" in audioMimes) add("eac3")
                if ("audio/opus" in audioMimes) add("opus")
                if ("audio/flac" in audioMimes) add("flac")
                if ("audio/vorbis" in audioMimes) add("vorbis")
                if ("audio/mpeg-l2" in audioMimes) add("mp2")
                if ("audio/alac" in audioMimes) add("alac")
                if ("audio/vnd.dts" in audioMimes || "audio/vnd.dts.hd" in audioMimes) { add("dts"); add("dca") }
                if ("audio/true-hd" in audioMimes) add("truehd")
            }

            val hevcMain = maxOf(level("video/hevc", CPL.HEVCProfileMain), level("video/hevc", CPL.HEVCProfileMain10))
            val hevc10 = level("video/hevc", CPL.HEVCProfileMain10)
            val avcMain = maxOf(level("video/avc", CPL.AVCProfileMain), level("video/avc", CPL.AVCProfileHigh))
            val avc10 = level("video/avc", CPL.AVCProfileHigh10)
            val dv = hasMime("video/dolby-vision")

            CodecSupport(
                video = video,
                audio = audio,
                hevcMain10 = hevc10 >= CPL.HEVCMainTierLevel4,
                hevcMainLevel = mapLevel(HEVC_LEVELS, hevcMain),
                hevcMain10Level = mapLevel(HEVC_LEVELS, hevc10),
                avcHigh10 = avc10 >= CPL.AVCLevel4,
                avcLevel = mapLevel(AVC_LEVELS, avcMain),
                avcHigh10Level = mapLevel(AVC_LEVELS, avc10),
                maxResolution = buildMap {
                    maxRes("video/avc")?.let { put("h264", it) }
                    maxRes("video/hevc")?.let { put("hevc", it) }
                    maxRes("video/av01")?.let { put("av1", it) }
                },
                hdr10 = hasProfile("video/hevc", CPL.HEVCProfileMain10HDR10, CPL.HEVCMainTierLevel4),
                hdr10Plus = android.os.Build.VERSION.SDK_INT >= 29 &&
                    hasProfile("video/hevc", CPL.HEVCProfileMain10HDR10Plus, CPL.HEVCMainTierLevel4),
                dolbyVision = dv,
                dolbyVisionEl = dv && hasProfile("video/dolby-vision", CPL.DolbyVisionProfileDvheDtb, CPL.DolbyVisionLevelHd24),
                hevcDoviHdr10PlusBug = android.os.Build.MODEL in FIRE_TV_DOVI_HDR10PLUS_BUG,
            )
        } catch (_: Throwable) {
            Conservative
        }
    }
}

object PlaybackNegotiator {
    private const val DIRECT_CONTAINERS = "mp4,m4v,mov,mkv,webm,ts,mpegts"

    /**
     * Highest bitrate offered to the server. Matches the official Jellyfin Android TV client default, so remuxes and
     * 4K discs direct play on a good network. A real limit still applies if the server or network has one.
     */
    const val MAX_STREAMING_BITRATE = 100_000_000
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

    /**
     * [forceTranscode] is the second attempt after a direct stream failed on the device: ask the server to re-encode
     * the video to H.264 instead of copying it, so a decoder the TV lacks can't fail the same way twice.
     */
    fun playbackInfoBody(
        userId: String,
        startTicks: Long,
        caps: CodecSupport = CodecSupport.Conservative,
        forceTranscode: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("UserId", userId)
        put("StartTimeTicks", startTicks)
        put("EnableDirectPlay", !forceTranscode)
        put("EnableDirectStream", !forceTranscode)
        put("EnableTranscoding", true)
        if (forceTranscode) {
            put("AllowVideoStreamCopy", false)
        }
        put("MaxStreamingBitrate", MAX_STREAMING_BITRATE)
        put("DeviceProfile", deviceProfile(caps, forceTranscode))
    }

    private fun cond(condition: String, property: String, value: String) = buildJsonObject {
        put("Condition", condition)
        put("Property", property)
        put("Value", value)
        put("IsRequired", true)
    }

    private fun codecProfile(codec: String, conditions: List<JsonObject>, apply: List<JsonObject> = emptyList()) = buildJsonObject {
        put("Type", "Video")
        put("Codec", codec)
        put("Conditions", JsonArray(conditions))
        put("ApplyConditions", JsonArray(apply))
    }

    /** HDR formats this TV can't show correctly. The server transcodes (tone maps) those instead of direct playing. */
    fun unsupportedHevcRanges(c: CodecSupport): Set<String> = buildSet {
        add("DOVIInvalid")
        if (!c.dolbyVisionEl) {
            add("DOVIWithEL")
            if (!c.hdr10Plus && !c.hevcDoviHdr10PlusBug) add("DOVIWithELHDR10Plus")
            if (!c.dolbyVision) {
                add("DOVI")
                if (!c.hdr10) add("DOVIWithHDR10")
                if (!c.hdr10Plus && !c.hevcDoviHdr10PlusBug) add("DOVIWithHDR10Plus")
            }
        }
        if (!c.hdr10Plus) {
            add("HDR10Plus")
            if (!c.hdr10) add("HDR10")
        }
        if (c.hevcDoviHdr10PlusBug) {
            add("DOVIWithHDR10Plus"); add("DOVIWithELHDR10Plus")
        }
    }

    fun codecProfiles(c: CodecSupport): List<JsonObject> = buildList {
        val hasAvc = "h264" in c.video
        val hasHevc = "hevc" in c.video
        fun profileIs(vararg names: String) = cond("EqualsAny", "VideoProfile", names.joinToString("|"))

        if (hasAvc) {
            val avcProfiles = listOfNotNull("high", "main", "baseline", "constrained baseline", if (c.avcHigh10) "high 10" else null)
            add(codecProfile("h264", listOf(profileIs(*avcProfiles.toTypedArray()))))
            if (c.avcLevel > 0) {
                add(codecProfile("h264", listOf(cond("LessThanEqual", "VideoLevel", c.avcLevel.toString())),
                    listOf(profileIs("high", "main", "baseline", "constrained baseline"))))
            }
            if (c.avcHigh10 && c.avcHigh10Level > 0) {
                add(codecProfile("h264", listOf(cond("LessThanEqual", "VideoLevel", c.avcHigh10Level.toString())),
                    listOf(cond("Equals", "VideoProfile", "high 10"))))
            }
            // Many TV decoders choke on high reference frame counts at large sizes.
            add(codecProfile("h264", listOf(cond("LessThanEqual", "RefFrames", "12")), listOf(cond("GreaterThanEqual", "Width", "1200"))))
            add(codecProfile("h264", listOf(cond("LessThanEqual", "RefFrames", "4")), listOf(cond("GreaterThanEqual", "Width", "1900"))))
        }
        if (hasHevc) {
            val hevcProfiles = listOfNotNull("main", if (c.hevcMain10) "main 10" else null)
            add(codecProfile("hevc", listOf(profileIs(*hevcProfiles.toTypedArray()))))
            if (c.hevcMainLevel > 0) {
                add(codecProfile("hevc", listOf(cond("LessThanEqual", "VideoLevel", c.hevcMainLevel.toString())),
                    listOf(cond("Equals", "VideoProfile", "main"))))
            }
            if (c.hevcMain10 && c.hevcMain10Level > 0) {
                add(codecProfile("hevc", listOf(cond("LessThanEqual", "VideoLevel", c.hevcMain10Level.toString())),
                    listOf(cond("Equals", "VideoProfile", "main 10"))))
            }
            val bad = unsupportedHevcRanges(c)
            if (bad.isNotEmpty()) {
                // The server treats a NotEquals that can't match as "transcode"; ApplyConditions limits it to those range types.
                add(codecProfile("hevc", listOf(cond("NotEquals", "VideoRangeType", bad.joinToString("|"))),
                    listOf(cond("EqualsAny", "VideoRangeType", bad.joinToString("|")))))
            }
        }
        // Largest picture each decoder reports, so an oversized file is transcoded instead of failing to start.
        c.maxResolution.forEach { (codec, wh) ->
            if (codec in c.video) {
                add(codecProfile(codec, listOf(cond("LessThanEqual", "Width", wh.first.toString()), cond("LessThanEqual", "Height", wh.second.toString()))))
            }
        }
    }

    fun deviceProfile(caps: CodecSupport = CodecSupport.Conservative, forceTranscode: Boolean = false): JsonObject = buildJsonObject {
        put("Name", "Driftfin Android TV")
        put("MaxStreamingBitrate", MAX_STREAMING_BITRATE)
        put("MaxStaticBitrate", MAX_STREAMING_BITRATE)
        put("DirectPlayProfiles", buildJsonArray {
            if (!forceTranscode) add(buildJsonObject {
                put("Type", "Video")
                put("Container", DIRECT_CONTAINERS)
                put("VideoCodec", caps.video.joinToString(","))
                put("AudioCodec", caps.audio.joinToString(","))
            })
        })
        put("TranscodingProfiles", buildJsonArray {
            // Video the TV can decode is copied into HLS when only the audio is a problem; otherwise the server re-encodes.
            val hlsVideo = if ("hevc" in caps.video && !forceTranscode) "hevc,h264" else "h264"
            val hlsAudio = listOf("aac", "ac3", "eac3", "mp3").filter { it in caps.audio || it == "aac" }
            add(buildJsonObject {
                put("Type", "Video")
                put("Container", "ts")
                put("Protocol", "hls")
                put("VideoCodec", hlsVideo)
                put("AudioCodec", hlsAudio.joinToString(","))
                put("Context", "Streaming")
                put("MaxAudioChannels", "6")
                put("BreakOnNonKeyFrames", true)
            })
        })
        put("CodecProfiles", JsonArray(codecProfiles(caps)))
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
