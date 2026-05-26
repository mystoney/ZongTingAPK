package com.zongting.zongting.ringtone

import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * 铃声裁剪与设置管理器
 * 导出格式：MP3，使用 Media3 Transformer 转码
 */
object AudioRingtoneHelper {

    private const val TAG = "AudioRingtoneHelper"
    private const val MAX_DURATION_MS = 60_000L
    private const val BUFFER_SIZE = 64 * 1024

    enum class RingtoneType {
        RINGTONE,
        NOTIFICATION,
        ALARM
    }

    sealed class TrimResult {
        data class Success(val filePath: String) : TrimResult()
        data class Error(val message: String) : TrimResult()
    }

    /**
     * 裁剪音频：下载URL到临时文件 → Media3 Transformer 转码为 MP3
     */
    suspend fun trimAudio(
        context: Context,
        songName: String,
        startMs: Long,
        endMs: Long,
        audioUrl: String?
    ): TrimResult = withContext(Dispatchers.IO) {
        try {
            val actualEndMs = minOf(endMs, startMs + MAX_DURATION_MS)

            val url = audioUrl ?: getCurrentPlayingUrl()
            Log.d(TAG, "trimAudio: url=${url ?: "NULL"}")

            if (url.isNullOrEmpty()) {
                Log.e(TAG, "trimAudio FAILED: audioUrl is null or empty")
                return@withContext TrimResult.Error("无法获取音频，请确保正在播放歌曲")
            }

            val sanitizedName = songName.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
            val downloadFile = File(context.cacheDir, "dl_${System.currentTimeMillis()}.tmp")
            val outputFile = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp3")

            Log.d(TAG, "下载音频: $url")
            val downloadOk = downloadFile(context, url, downloadFile)
            Log.d(TAG, "下载结果: ok=$downloadOk, size=${downloadFile.length()}")

            if (!downloadOk) {
                downloadFile.delete()
                Log.e(TAG, "trimAudio FAILED: downloadFile returned false")
                return@withContext TrimResult.Error("音频下载失败，请检查网络")
            }
            if (downloadFile.length() == 0L) {
                downloadFile.delete()
                Log.e(TAG, "trimAudio FAILED: downloaded file is empty")
                return@withContext TrimResult.Error("音频下载失败，文件为空")
            }

            Log.d(TAG, "裁剪音频: ${startMs}ms → ${actualEndMs}ms")
            val ok = trimToMp3(
                context,
                downloadFile.absolutePath,
                outputFile.absolutePath,
                startMs,
                actualEndMs
            )
            Log.d(TAG, "裁剪结果: ok=$ok, outputSize=${outputFile.length()}")

            downloadFile.delete()

            if (ok) {
                Log.d(TAG, "裁剪成功: ${outputFile.absolutePath}")
                TrimResult.Success(outputFile.absolutePath)
            } else {
                outputFile.delete()
                Log.e(TAG, "trimAudio FAILED: trimToMp3 returned false")
                TrimResult.Error("音频裁剪失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "裁剪异常", e)
            TrimResult.Error("裁剪异常: ${e.message}")
        }
    }

    /**
     * 使用 MediaCodec 解码 → 重新编码为 AAC，加 ADTS 头封装 → MediaMuxer 输出
     * 裁剪通过 startUs/endUs 参数控制 extractor 读取范围实现
     */
    private suspend fun trimToMp3(
        context: Context,
        input: String,
        output: String,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "trimToMp3: $input → $output (${startMs}ms → ${endMs}ms)")

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerTrackIndex = -1
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        val decOutInfo = MediaCodec.BufferInfo()
        val encOutInfo = MediaCodec.BufferInfo()
        val TIMEOUT_US = 10_000L

        try {
            // --- Setup Extractor ---
            extractor = MediaExtractor()
            extractor.setDataSource(input)
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { audioTrack = i; break }
            }
            if (audioTrack < 0) { Log.e(TAG, "No audio track"); return@withContext false }

            extractor.selectTrack(audioTrack)
            val inFmt = extractor.getTrackFormat(audioTrack)
            val inMime = inFmt.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val chCount = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            Log.d(TAG, "Input: $inMime, rate=$sampleRate, ch=$chCount")

            // --- Decoder ---
            decoder = MediaCodec.createDecoderByType(inMime)
            decoder.configure(inFmt, null, null, 0)
            decoder.start()

            // --- Encoder (AAC) ---
            val outFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, chCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // --- Muxer (MPEG-4, needs ADTS framing) ---
            File(output).delete()
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerTrackIndex = muxer.addTrack(outFmt)
            muxer.start()

            var pendingDecoderEOS = false
            var pendingEncoderEOS = false
            var pendingEncoderDrainEOS = false
            var firstPtsUs = Long.MAX_VALUE
            val pcmBuf = ByteArray(32768)

            // Encoder timestamp: convert decoder PTS to encoder timeline (relative to first frame)
            fun toEncoderPts(decoderPtsUs: Long): Long {
                if (firstPtsUs == Long.MAX_VALUE) firstPtsUs = decoderPtsUs
                return (decoderPtsUs - firstPtsUs).coerceAtLeast(0)
            }

            loop@ while (true) {
                // --- Feed decoder ---
                var decoderFed = false
                if (!pendingDecoderEOS) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        decoderFed = true
                        val inBuf = decoder.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(inBuf, 0)
                        val t = extractor.sampleTime
                        if (sz < 0 || t > endUs) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            pendingDecoderEOS = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sz, t, 0)
                            extractor.advance()
                        }
                    }
                }

                // --- Drain decoder → PCM → encoder ---
                val decIdx = decoder.dequeueOutputBuffer(decOutInfo, TIMEOUT_US)
                if (decIdx >= 0) {
                    if ((decOutInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        decoder.releaseOutputBuffer(decIdx, false)
                        // Queue EOS to encoder
                        val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (encInIdx >= 0) {
                            encoder.queueInputBuffer(encInIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            pendingEncoderDrainEOS = true
                        }
                    } else {
                        val pcmSize = decOutInfo.size
                        if (pcmSize > 0) {
                            val pcm = decoder.getOutputBuffer(decIdx)!!
                            pcm.position(decOutInfo.offset)
                            pcm.limit(decOutInfo.offset + pcmSize)
                            val copySize = minOf(pcmSize, pcmBuf.size)
                            pcm.get(pcmBuf, 0, copySize)
                            decoder.releaseOutputBuffer(decIdx, false)

                            // Try to feed encoder, drain first if needed
                            var encInIdx = encoder.dequeueInputBuffer(0)
                            while (encInIdx < 0 && !pendingEncoderDrainEOS) {
                                val dummyInfo = MediaCodec.BufferInfo()
                                val drainIdx = encoder.dequeueOutputBuffer(dummyInfo, 0)
                                if (drainIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                                if (drainIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                                if (drainIdx >= 0) {
                                    if (dummyInfo.size > 0) {
                                        val encOut = encoder.getOutputBuffer(drainIdx)!!
                                        muxer!!.writeSampleData(muxerTrackIndex, encOut, dummyInfo)
                                    }
                                    encoder.releaseOutputBuffer(drainIdx, false)
                                    if ((dummyInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        pendingEncoderEOS = true
                                    }
                                }
                                encInIdx = encoder.dequeueInputBuffer(0)
                            }
                            if (encInIdx >= 0) {
                                val encIn = encoder.getInputBuffer(encInIdx)!!
                                encIn.put(pcmBuf, 0, copySize)
                                encoder.queueInputBuffer(encInIdx, 0, copySize, toEncoderPts(decOutInfo.presentationTimeUs), 0)
                            }
                        } else {
                            decoder.releaseOutputBuffer(decIdx, false)
                        }
                    }
                }

                // --- Drain encoder → muxer ---
                val drainIdx = encoder.dequeueOutputBuffer(encOutInfo, 0)
                when {
                    drainIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    drainIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    drainIdx >= 0 -> {
                        if ((encOutInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoder.releaseOutputBuffer(drainIdx, false)
                            pendingEncoderEOS = true
                        } else if (encOutInfo.size > 0) {
                            val encOut = encoder.getOutputBuffer(drainIdx)!!
                            muxer!!.writeSampleData(muxerTrackIndex, encOut, encOutInfo)
                            encoder.releaseOutputBuffer(drainIdx, false)
                        } else {
                            encoder.releaseOutputBuffer(drainIdx, false)
                        }
                    }
                }

                // Check if we need to signal EOS to encoder because input buffer wasn't available
                if (pendingDecoderEOS && !pendingEncoderDrainEOS) {
                    val encInIdx = encoder.dequeueInputBuffer(0)
                    if (encInIdx >= 0) {
                        encoder.queueInputBuffer(encInIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        pendingEncoderDrainEOS = true
                    }
                }

                if (pendingEncoderEOS) break@loop
                // Safety: if we can't make progress, break
                if (!decoderFed && decIdx < 0 && drainIdx < 0) {
                    Log.w(TAG, "Decoder/encoder stall, breaking")
                    break@loop
                }
            }

            val result = File(output).length() > 1000
            Log.d(TAG, "trimToMp3 done: result=$result, size=${File(output).length()}")
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "trimToMp3 EXCEPTION", e)
            return@withContext false
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (e: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }

    private fun downloadFile(context: Context, urlStr: String, out: File): Boolean {
        return try {
            when {
                urlStr.startsWith("content://") -> {
                    val uri = Uri.parse(urlStr)
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(BUFFER_SIZE)
                            var n: Int
                            while (inp.read(buf).also { n = it } != -1) fos.write(buf, 0, n)
                        }
                    } ?: return false
                    true
                }
                urlStr.startsWith("file://") -> {
                    val file = File(urlStr.removePrefix("file://"))
                    if (!file.exists()) return false
                    file.inputStream().use { inp ->
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(BUFFER_SIZE)
                            var n: Int
                            while (inp.read(buf).also { n = it } != -1) fos.write(buf, 0, n)
                        }
                    }
                    true
                }
                else -> {
                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 60_000
                    conn.setRequestProperty("User-Agent", "ZongTing/1.0")
                    conn.connect()
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        conn.disconnect(); return false
                    }
                    conn.inputStream.use { inp ->
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(BUFFER_SIZE)
                            var n: Int
                            while (inp.read(buf).also { n = it } != -1) fos.write(buf, 0, n)
                        }
                    }
                    conn.disconnect()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "download failed: ${e.message}")
            false
        }
    }

    suspend fun saveToMediaStore(
        context: Context,
        sourceFilePath: String,
        songName: String,
        artist: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val ringtoneDir = context.getExternalFilesDir(Environment.DIRECTORY_RINGTONES)
                ?: File(context.filesDir, "Ringtones").also { it.mkdirs() }
            if (!ringtoneDir.exists()) ringtoneDir.mkdirs()

            val sanitized = songName.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
            val fileName = "${sanitized}_铃声_${System.currentTimeMillis()}.mp3"
            val destFile = File(ringtoneDir, fileName)
            File(sourceFilePath).copyTo(destFile, overwrite = true)
            File(sourceFilePath).delete()
            Log.d(TAG, "保存成功: ${destFile.absolutePath}")
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "saveToMediaStore failed", e)
            null
        }
    }

    fun setAsRingtone(context: Context, filePath: String, type: RingtoneType): Boolean {
        return try {
            if (!Settings.System.canWrite(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return false
            }
            val fileUri = if (filePath.isNotEmpty()) Uri.fromFile(File(filePath)) else return false
            val settingKey = when (type) {
                RingtoneType.RINGTONE -> Settings.System.RINGTONE
                RingtoneType.NOTIFICATION -> Settings.System.NOTIFICATION_SOUND
                RingtoneType.ALARM -> Settings.System.ALARM_ALERT
            }
            Settings.System.putString(context.contentResolver, settingKey, fileUri.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "setAsRingtone failed", e)
            false
        }
    }

    fun setMediaStoreAsRingtone(context: Context, mediaUri: Uri, type: RingtoneType): Boolean {
        return try {
            if (!Settings.System.canWrite(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return false
            }
            val settingKey = when (type) {
                RingtoneType.RINGTONE -> Settings.System.RINGTONE
                RingtoneType.NOTIFICATION -> Settings.System.NOTIFICATION_SOUND
                RingtoneType.ALARM -> Settings.System.ALARM_ALERT
            }
            Settings.System.putString(context.contentResolver, settingKey, mediaUri.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "setMediaStoreAsRingtone failed", e)
            false
        }
    }

    private fun getCurrentPlayingUrl(): String? {
        val player = com.zongting.zongting.player.PlayerManager.getPlayer() ?: return null
        return player.currentMediaItem?.localConfiguration?.uri?.toString()
    }

    fun hasWriteSettingsPermission(context: Context): Boolean = Settings.System.canWrite(context)
}
