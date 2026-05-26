package com.zongting.zongting.ringtone

import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
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
        var extractorForFmt: MediaExtractor? = null
        try {
            // 获取 track format（用于 sample rate）
            extractorForFmt = MediaExtractor()
            extractorForFmt.setDataSource(input)
            var audioTrackIdx = -1
            var trackFmt: MediaFormat? = null
            for (t in 0 until extractorForFmt.trackCount) {
                val fmt = extractorForFmt.getTrackFormat(t)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioTrackIdx = t; trackFmt = fmt; break
                }
            }
            if (audioTrackIdx < 0 || trackFmt == null) {
                Log.e(TAG, "trimMp3Raw: no audio track"); return false
            }
            val sampleRate = trackFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            Log.d(TAG, "trimMp3Raw: sampleRate=$sampleRate")
            extractorForFmt.release()
            extractorForFmt = null

            // ── 步骤1: 读取文件字节，扫描所有 MP3 帧 ──────────────────────
            val bytes = File(input).readBytes()
            Log.d(TAG, "trimMp3Raw: reading ${bytes.size} bytes, start=$startMs, end=$endMs")

            val frames = mutableListOf<ByteArray>() // 帧数据列表（按文件顺序）
            var i = 0
            while (i < bytes.size - 4) {
                // MPEG Audio Frame Sync: 0xFF 0xE? (MPEG-1/2 Audio Layer III)
                if (bytes[i].toInt() and 0xFF == 0xFF && (bytes[i + 1].toInt() and 0xE0) == 0xE0) {
                    val h = (bytes[i].toInt() and 0xFF shl 24) or (bytes[i + 1].toInt() and 0xFF shl 16) or
                            (bytes[i + 2].toInt() and 0xFF shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    val version = (h shr 19) and 0x3
                    val srIdx = (h shr 10) and 0x3
                    val srArr = if (version == 3) intArrayOf(44100, 48000, 32000) else intArrayOf(22050, 24000, 16000)
                    val sr = if (srIdx < srArr.size) srArr[srIdx] else 44100
                    val brIdx = (h shr 12) and 0xF
                    val brArr = intArrayOf(0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0)
                    val br = if (brIdx < brArr.size) brArr[brIdx] * 1000 else 128000
                    val padding = if ((h shr 9) and 0x1 == 1) 1 else 0
                    val frameSz = (144 * br / sr) + padding
                    if (frameSz <= 0 || i + frameSz > bytes.size) { i++; continue }
                    frames.add(bytes.copyOfRange(i, i + frameSz))
                    i += frameSz
                } else { i++ }
            }
            Log.d(TAG, "trimMp3Raw: found ${frames.size} MP3 frames (file-level scan)")

            // ── 步骤2: 用 extractor 获取帧级 PTS ──────────────────────────
            val ext2 = MediaExtractor()
            ext2.setDataSource(input)
            ext2.selectTrack(audioTrackIdx)
            ext2.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val startPts = ext2.sampleTime
            val ptsList = mutableListOf<Long>()
            while (true) {
                val pts = ext2.sampleTime
                if (pts < 0) break
                ptsList.add(pts)
                ext2.advance()
                if (pts > endMs * 1000L + 5_000_000L) break
            }
            ext2.release()
            Log.d(TAG, "trimMp3Raw: extractor pts count=${ptsList.size}, startPts=$startPts")

            // MPEG-1 Layer 3: 1152 samples/frame, frame duration = 1152/sr * 1e6 us
            val frameDurUs = (1152L * 1_000_000L) / sampleRate

            // ── 步骤3: 按 PTS 范围筛选帧 ──────────────────────────────────
            val outBuf = ByteArray(bytes.size) // 足够大
            var outPos = 0
            var written = 0
            val endPts = startMs * 1000L + (endMs - startMs) * 1000L

            for ((fi, frame) in frames.withIndex()) {
                val pts: Long = if (fi < ptsList.size) {
                    ptsList[fi]
                } else if (ptsList.isNotEmpty()) {
                    ptsList.last() + (fi - ptsList.size + 1) * frameDurUs
                } else {
                    startMs * 1000L + fi * frameDurUs
                }
                if (pts >= startPts && pts <= endPts) {
                    System.arraycopy(frame, 0, outBuf, outPos, frame.size)
                    outPos += frame.size
                    written++
                }
            }

            Log.d(TAG, "trimMp3Raw: written=$written frames, bytes=$outPos")
            if (written == 0) { Log.e(TAG, "trimMp3Raw: no frames in range!"); return false }

            File(output).writeBytes(outBuf.copyOf(outPos))
            val ok = File(output).length() > 1000
            Log.d(TAG, "trimMp3Raw done: result=$ok, fileSize=${File(output).length()}")
            return ok

        } catch (e: Exception) {
            Log.e(TAG, "trimMp3Raw EXCEPTION", e)
            return false
        } finally {
            try { extractorForFmt?.release() } catch (e: Exception) {}
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
            val ringtoneDir = File("/storage/emulated/0/Download").also { it.mkdirs() }
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
