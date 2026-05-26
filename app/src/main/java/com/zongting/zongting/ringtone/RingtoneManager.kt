package com.zongting.zongting.ringtone

import android.content.Context
import android.content.Intent
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
     * 音频裁剪：MP3 直接 copy 原始帧，AAC 用 MediaMuxer remux
     * MP3 自包含不需要 MPEG-4 容器封装
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
        var muxer: MediaMuxer? = null

        val startUs = startMs * 1000L
        val endUs = endMs * 1000L

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(input)

            var audioTrack = -1
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val m = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("audio/")) { audioTrack = i; mime = m; break }
            }
            if (audioTrack < 0) {
                Log.e(TAG, "No audio track found")
                return@withContext false
            }

            extractor.selectTrack(audioTrack)
            val trackFmt = extractor.getTrackFormat(audioTrack)
            Log.d(TAG, "Audio track format: $trackFmt, mime=$mime")

            // MP3: 直接 copy 原始帧，不走 MediaMuxer
            if (mime == "audio/mpeg") {
                return@withContext trimMp3Raw(input, output, startMs, endMs)
            }

            // AAC/M4A: MediaMuxer remux
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            File(output).delete()
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(trackFmt)
            muxer.start()

            val buf = java.nio.ByteBuffer.allocate(32768)
            val info = android.media.MediaCodec.BufferInfo()
            var totalBytes = 0L

            while (true) {
                val sampleSize = extractor.readSampleData(buf, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break

                if (sampleTime >= startUs) {
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = sampleTime - startUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrack, buf, info)
                    totalBytes += sampleSize
                }

                if (!extractor.advance()) break
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val result = File(output).length() > 1000
            Log.d(TAG, "trimToMp3 done: result=$result, bytes=$totalBytes, fileSize=${File(output).length()}")
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "trimToMp3 EXCEPTION", e)
            return@withContext false
        } finally {
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }

    /**
     * MP3 原始帧拷贝：解析 MP3 帧头找到时间范围内的帧，直接写入输出文件
     * MP3 是自包含格式，不需要容器封装
     */
    private fun trimMp3Raw(input: String, output: String, startMs: Long, endMs: Long): Boolean {
        return try {
            val bytes = File(input).readBytes()
            Log.d(TAG, "trimMp3Raw: reading ${bytes.size} bytes, start=$startMs, end=$endMs")

            val frames = mutableListOf<Pair<Int, ByteArray>>() // offset, frame bytes
            var i = 0
            while (i < bytes.size - 4) {
                // 找 MP3 帧同步字: 0xFF 0xE? (MPEG-1 Audio Layer III)
                if (bytes[i].toInt() and 0xFF == 0xFF && (bytes[i + 1].toInt() and 0xE0) == 0xE0) {
                    val header = (bytes[i].toInt() and 0xFF shl 24) or (bytes[i + 1].toInt() and 0xFF shl 16) or
                                 (bytes[i + 2].toInt() and 0xFF shl 8) or (bytes[i + 3].toInt() and 0xFF)

                    // 采样率: bits 11-12 (0=MPEG1, 1=MPEG2, 2=MPEG2.5)
                    val version = (header shr 19) and 0x3
                    val sampleRateIdx = (header shr 10) and 0x3
                    val sampleRates = if (version == 3) intArrayOf(44100, 48000, 32000) // MPEG-1
                                      else intArrayOf(22050, 24000, 16000) // MPEG-2/2.5
                    val sampleRate = if (sampleRateIdx < sampleRates.size) sampleRates[sampleRateIdx] else 44100

                    // 比特率索引 bits 12-15
                    val bitrateIdx = (header shr 12) and 0xF
                    val bitrates = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
                    val bitrate = if (bitrateIdx < bitrates.size) bitrates[bitrateIdx] * 1000 else 128000

                    // 填充位
                    val padding = if ((header shr 9) and 0x1 == 1) 1 else 0
                    val channelMode = (header shr 6) and 0x3

                    // 帧长 = (144 * bitrate / sampleRate) + padding
                    val frameSize = (144 * bitrate / sampleRate) + padding
                    if (frameSize <= 0 || i + frameSize > bytes.size) {
                        i++; continue
                    }

                    val frameBytes = bytes.copyOfRange(i, i + frameSize)
                    frames.add(i to frameBytes)
                    i += frameSize
                } else {
                    i++
                }
            }

            Log.d(TAG, "trimMp3Raw: found ${frames.size} frames")

            // 用 MediaExtractor 定位起始帧
            val extractor = MediaExtractor()
            extractor.setDataSource(input)
            var audioTrack = -1
            for (t in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(t)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioTrack = t; break
                }
            }
            extractor.selectTrack(audioTrack)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val startPts = extractor.sampleTime
            extractor.seekTo(endMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val endPts = extractor.sampleTime
            extractor.release()

            Log.d(TAG, "trimMp3Raw: PTS range $startPts - $endPts")

            // 分配足够大的 buffer
            val totalSize = frames.sumOf { it.second.size }
            val outBytes = ByteArray(totalSize)
            var outPos = 0
            var framesWritten = 0

            for ((offset, frame) in frames) {
                val pts = extractorPtsForByteOffset(input, audioTrack, offset)
                if (pts < 0) { outPos += frame.size; continue }
                if (pts >= startPts && pts <= endPts) {
                    System.arraycopy(frame, 0, outBytes, outPos, frame.size)
                    outPos += frame.size
                    framesWritten++
                }
            }

            Log.d(TAG, "trimMp3Raw: framesWritten=$framesWritten, outPos=$outPos")
            if (framesWritten == 0) {
                Log.e(TAG, "trimMp3Raw: no frames in range!")
                return false
            }

            File(output).writeBytes(outBytes.copyOf(outPos))
            val result = File(output).length() > 1000
            Log.d(TAG, "trimMp3Raw done: result=$result, fileSize=${File(output).length()}")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "trimMp3Raw EXCEPTION", e)
            return false
        }
    }

    // 用 extractor 查询某 byte offset 对应的 PTS
    private fun extractorPtsForByteOffset(input: String, track: Int, byteOffset: Int): Long {
        return try {
            val ext = MediaExtractor()
            ext.setDataSource(input)
            ext.selectTrack(track)
            var offset = 0
            while (true) {
                val pts = ext.sampleTime
                val size = ext.readSampleData(java.nio.ByteBuffer.allocate(32768), 0)
                if (size < 0) break
                if (offset <= byteOffset && byteOffset < offset + kotlin.math.max(size, 1)) {
                    ext.release()
                    return pts
                }
                offset += kotlin.math.max(size, 1)
                ext.advance()
            }
            ext.release()
            -1L
        } catch (e: Exception) { -1L }
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
