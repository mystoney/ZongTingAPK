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
     * 使用 MediaCodec 解码 → 重新编码为 AAC/M4A（音频转码，裁剪）
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
        var trackIndex = -1
        var muxerTrackIndex = -1
        var sawInputEOS = false
        var sawOutputEOS = false
        var encoderOutputFinished = false
        var decoderOutputFinished = false
        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        val inputBufferInfo = MediaCodec.BufferInfo()
        val outputBufferInfo = MediaCodec.BufferInfo()
        val TIMEOUT_US = 10_000L

        try {
            // --- Setup Extractor ---
            extractor = MediaExtractor()
            extractor.setDataSource(input)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) {
                Log.e(TAG, "No audio track found"); return@withContext false
            }
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = inputFormat.getLong(MediaFormat.KEY_DURATION)

            // Seek to start
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            Log.d(TAG, "Input format: $inputMime, rate=$sampleRate, ch=$channelCount, dur=$duration")

            // --- Setup Decoder ---
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // --- Setup Encoder (AAC) ---
            val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // --- Setup Muxer ---
            File(output).delete()
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerTrackIndex = muxer.addTrack(outputFormat)
            muxer.start()
            Log.d(TAG, "Muxer started, trackIndex=$muxerTrackIndex")

            // --- Transcode Loop ---
            val bufferInfo = MediaCodec.BufferInfo()
            var frameCount = 0

            while (!sawOutputEOS) {
                // Feed input to decoder
                if (!sawInputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val sampleTime = extractor.sampleTime

                        if (sampleSize < 0 || sampleTime > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                            Log.d(TAG, "Input EOS queued at ${extractor.sampleTime}")
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime - startUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain decoder output → encoder input
                if (!decoderOutputFinished) {
                    val outIndex = decoder.dequeueOutputBuffer(inputBufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                        outIndex >= 0 -> {
                            val outBuf = decoder.getOutputBuffer(outIndex)!!
                            val info = inputBufferInfo
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoder.queueInputBuffer(outIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                decoderOutputFinished = true
                            } else {
                                encoder.queueInputBuffer(outIndex, 0, info.size, info.presentationTimeUs, 0)
                            }
                        }
                    }
                }

                // Drain encoder output → muxer
                if (!encoderOutputFinished) {
                    val outIndex = encoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            Log.d(TAG, "Encoder output format changed: ${encoder.outputFormat}")
                        }
                        outIndex >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outIndex)!!
                            val info = outputBufferInfo
                            if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                // skip codec config
                                encoder.releaseOutputBuffer(outIndex, false)
                            } else {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                muxer.writeSampleData(muxerTrackIndex, outBuf, info)
                                frameCount++
                            }
                            encoder.releaseOutputBuffer(outIndex, false)

                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderOutputFinished = true
                                sawOutputEOS = true
                                Log.d(TAG, "Encoder EOS, wrote $frameCount frames")
                            }
                        }
                    }
                }

                // Timeout safety: if we've processed beyond endUs, signal EOS
                if (sawInputEOS && decoderOutputFinished && encoderOutputFinished) {
                    sawOutputEOS = true
                }
            }

            val result = File(output).length() > 0
            Log.d(TAG, "trimToMp3 done: result=$result, size=${File(output).length()}, frames=$frameCount")
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
