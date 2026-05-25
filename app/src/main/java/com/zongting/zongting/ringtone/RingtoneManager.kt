package com.zongting.zongting.ringtone

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 铃声裁剪与设置管理器（使用 Android 原生 MediaMuxer，无外部依赖）
 * 注意：类名避免与 android.media.RingtoneManager 重名
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
     * 裁剪音频：下载URL到临时文件 → MediaCodec转码(MP3→AAC) → MediaMuxer封装
     * 设备自带 MP3 decoder 和 AAC encoder，无需外部依赖
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
            val outputFile = File(context.cacheDir, "trim_${System.currentTimeMillis()}.m4a")

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
            val ok = trimWithMuxer(
                downloadFile.absolutePath,
                outputFile.absolutePath,
                startMs * 1000L,
                actualEndMs * 1000L
            )
            Log.d(TAG, "裁剪结果: ok=$ok, outputSize=${outputFile.length()}")

            downloadFile.delete()

            if (ok) {
                Log.d(TAG, "裁剪成功: ${outputFile.absolutePath}")
                TrimResult.Success(outputFile.absolutePath)
            } else {
                outputFile.delete()
                Log.e(TAG, "trimAudio FAILED: trimWithMuxer returned false")
                TrimResult.Error("音频裁剪失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "裁剪异常", e)
            TrimResult.Error("裁剪异常: ${e.message}")
        }
    }

    private fun trimWithMuxer(input: String, output: String, startUs: Long, endUs: Long): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(input)

            val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false

            val srcFormat = extractor.getTrackFormat(trackIdx)
            val mime = srcFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = srcFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = srcFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.selectTrack(trackIdx)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            Log.d(TAG, "音频格式: mime=$mime, rate=$sampleRate, ch=$channelCount")

            // 解码器（MP3 → PCM）
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(srcFormat, null, null, 0)

            // 编码器（PCM → AAC）
            val dstFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(dstFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIdx = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()

            // Decoder callback：将PCM数据直接传给encoder
            decoder.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    val buf = codec.getInputBuffer(index) ?: return
                    buf.clear()
                    val sampleSize = extractor?.readSampleData(buf, 0) ?: -1
                    if (sampleSize < 0 || (extractor?.sampleTime ?: 0) > endUs + 500_000) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(index, 0, sampleSize, extractor!!.sampleTime, 0)
                        extractor.advance()
                    }
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo
                ) {
                    val encoded = codec.getOutputBuffer(index)
                    if (info.size > 0 && encoded != null) {
                        // 给encoder喂PCM
                        val encInIdx = encoder?.dequeueInputBuffer(5000) ?: -1
                        if (encInIdx >= 0) {
                            val encBuf = encoder!!.getInputBuffer(encInIdx)!!
                            val copyBytes = minOf(info.size, encBuf.remaining())
                            val copy = ByteArray(copyBytes)
                            encoded.get(copy, 0, copyBytes)
                            encBuf.clear()
                            encBuf.put(copy)
                            encoder.queueInputBuffer(encInIdx, 0, copyBytes, info.presentationTimeUs, 0)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        val encInIdx = encoder?.dequeueInputBuffer(5000) ?: -1
                        if (encInIdx >= 0) {
                            encoder!!.queueInputBuffer(encInIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Log.e(TAG, "Decoder error", e)
                }
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
            })

            // Encoder callback：将AAC数据写入muxer
            encoder.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

                override fun onOutputBufferAvailable(
                    codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo
                ) {
                    if (!muxerStarted) {
                        muxerTrackIdx = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    val data = codec.getOutputBuffer(index)
                    if (info.size > 0 && data != null && info.presentationTimeUs <= endUs) {
                        muxer.writeSampleData(muxerTrackIdx, data, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        Log.d(TAG, "Encoder EOS received")
                    }
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Log.e(TAG, "Encoder error", e)
                }
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
            })

            decoder.start()
            encoder.start()

            // 等待处理完成（最多30秒）
            val timeout = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < timeout) {
                if (muxerStarted && File(output).length() > 0) {
                    val hasMore = extractor?.sampleTime ?: 0 <= endUs + 500_000
                    if (!hasMore) {
                        // 数据读完，等encoder输出完毕
                        Thread.sleep(500)
                        break
                    }
                }
                Thread.sleep(100)
            }

            val result = muxerStarted && File(output).length() > 0
            Log.d(TAG, "trimWithMuxer完成: success=$result, size=${File(output).length()}")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "trimWithMuxer异常", e)
            return false
        } finally {
            listOf(decoder, encoder).forEach {
                try { it?.stop(); it?.release() } catch (_: Exception) {}
            }
            try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
            extractor?.release()
        }
    }

    private fun downloadFile(context: Context, urlStr: String, out: File): Boolean {
        return try {
            when {
                urlStr.startsWith("content://") -> {
                    // 从 ContentProvider 读取（如缓存的音频）
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
                    // 直接读取本地文件
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
                    // HTTP/HTTPS 流
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
            val fileName = "${songName}_铃声_${System.currentTimeMillis()}.m4a"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.TITLE, "${songName}_铃声")
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                put(MediaStore.Audio.Media.IS_ALARM, true)
                put(MediaStore.Audio.Media.IS_MUSIC, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { os ->
                File(sourceFilePath).inputStream().use { it.copyTo(os) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            File(sourceFilePath).delete()
            uri
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
