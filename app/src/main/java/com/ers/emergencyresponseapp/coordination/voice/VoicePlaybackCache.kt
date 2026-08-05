package com.ers.emergencyresponseapp.coordination.voice

import android.content.Context
import android.net.Uri
import com.ers.emergencyresponseapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Downloads a remote coordination voice note into an app-private playback cache.
 *
 * Android's platform [android.media.MediaPlayer] is inconsistent when it streams
 * WAV/AAC files from servers that redirect or return an imprecise Content-Type.
 * Playing a validated local cache file avoids that device/server combination and
 * also turns HTTP/HTML errors into a visible message instead of apparent silence.
 */
class VoicePlaybackCache(context: Context) {
    private val playbackDirectory = File(
        context.applicationContext.cacheDir,
        "coordination_voice_playback"
    ).apply {
        if (!exists() && !mkdirs()) {
            throw IOException("Unable to initialize the voice playback cache")
        }
    }

    suspend fun resolve(
        remoteUrl: String,
        expectedMimeType: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = remoteUrl.trim()
            if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
                throw IOException("Voice messages require a secure HTTPS URL")
            }

            pruneOldFiles()

            val extension = preferredExtension(cleanUrl, expectedMimeType)
            val cacheFile = File(
                playbackDirectory,
                "voice_${sha256(cleanUrl)}.$extension"
            )
            if (isUsableCachedAudio(cacheFile)) {
                cacheFile.setLastModified(System.currentTimeMillis())
                return@runCatching cacheFile
            }
            cacheFile.delete()

            val temporary = File(
                playbackDirectory,
                "${cacheFile.name}.download-${System.nanoTime()}"
            )

            try {
                val request = Request.Builder()
                    .url(cleanUrl)
                    .get()
                    .header("Accept", "audio/*, application/octet-stream;q=0.8")
                    .header("User-Agent", "ERS-Responder/${BuildConfig.VERSION_NAME}")
                    .build()

                HTTP_CLIENT.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Voice file server returned HTTP ${response.code}")
                    }

                    val body = response.body
                        ?: throw IOException("Voice file server returned an empty response")
                    val responseMime = body.contentType()?.toString().orEmpty().lowercase(Locale.US)
                    if (
                        responseMime.startsWith("text/") ||
                        responseMime.contains("json") ||
                        responseMime.contains("html")
                    ) {
                        throw IOException("Voice file URL returned $responseMime instead of audio")
                    }

                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_AUDIO_BYTES) {
                        throw IOException("Voice file is larger than the playback limit")
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(temporary, false).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_AUDIO_BYTES) {
                                    throw IOException("Voice file is larger than the playback limit")
                                }
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                }

                validateDownloadedAudio(
                    file = temporary,
                    extension = extension,
                    expectedMimeType = expectedMimeType
                )

                if (!temporary.renameTo(cacheFile)) {
                    temporary.copyTo(cacheFile, overwrite = true)
                    temporary.delete()
                }
                if (!isUsableCachedAudio(cacheFile)) {
                    cacheFile.delete()
                    throw IOException("Voice file could not be saved for playback")
                }
                cacheFile
            } finally {
                temporary.delete()
            }
        }
    }

    private fun validateDownloadedAudio(
        file: File,
        extension: String,
        expectedMimeType: String?
    ) {
        if (!isUsableCachedAudio(file)) {
            throw IOException("Voice file is empty or incomplete")
        }

        val header = ByteArray(16)
        val headerLength = file.inputStream().use { it.read(header) }
        if (headerLength <= 0) {
            throw IOException("Voice file is empty")
        }

        val firstNonWhitespace = header
            .take(headerLength)
            .firstOrNull { byte -> !byte.toInt().toChar().isWhitespace() }
            ?.toInt()
            ?.and(0xFF)
        if (firstNonWhitespace == '<'.code || firstNonWhitespace == '{'.code) {
            throw IOException("Voice file URL returned a web page instead of audio")
        }

        val knownSignature = when {
            headerLength >= 12 && ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 4) == "WAVE" -> true
            headerLength >= 8 && ascii(header, 4, 4) == "ftyp" -> true
            headerLength >= 4 && ascii(header, 0, 4) == "OggS" -> true
            headerLength >= 3 && ascii(header, 0, 3) == "ID3" -> true
            headerLength >= 2 && (header[0].toInt() and 0xFF) == 0xFF &&
                    ((header[1].toInt() and 0xE0) == 0xE0) -> true
            else -> false
        }

        val declaredAudio = expectedMimeType
            ?.trim()
            ?.startsWith("audio/", ignoreCase = true) == true
        val knownExtension = extension in SUPPORTED_EXTENSIONS
        if (!knownSignature && !declaredAudio && !knownExtension) {
            throw IOException("Downloaded file is not a supported voice-message format")
        }
    }

    private fun isUsableCachedAudio(file: File): Boolean =
        file.isFile && file.length() >= MIN_AUDIO_BYTES

    private fun preferredExtension(url: String, mimeType: String?): String {
        val pathExtension = Uri.parse(url)
            .lastPathSegment
            ?.substringBefore('?')
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?.takeIf { it in SUPPORTED_EXTENSIONS }
        if (pathExtension != null) return pathExtension

        return when (mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)) {
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/aac", "audio/x-aac" -> "aac"
            "audio/3gpp" -> "3gp"
            "audio/ogg", "audio/opus" -> "ogg"
            "audio/mpeg", "audio/mp3" -> "mp3"
            else -> "audio"
        }
    }

    private fun pruneOldFiles() {
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
        playbackDirectory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff || file.name.contains(".download-")) {
                file.delete()
            }
        }

        val retained = playbackDirectory.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        var retainedBytes = 0L
        retained.forEach { file ->
            retainedBytes += file.length()
            if (retainedBytes > CACHE_MAX_BYTES) file.delete()
        }
    }

    private fun ascii(buffer: ByteArray, offset: Int, length: Int): String =
        String(buffer, offset, length, Charsets.US_ASCII)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val MIN_AUDIO_BYTES = 128L
        private const val MAX_AUDIO_BYTES = 25L * 1024L * 1024L
        private const val CACHE_MAX_BYTES = 75L * 1024L * 1024L
        private const val CACHE_MAX_AGE_MS = 3L * 24L * 60L * 60L * 1000L

        private val SUPPORTED_EXTENSIONS = setOf(
            "wav", "m4a", "mp4", "aac", "3gp", "ogg", "opus", "mp3"
        )

        private val HTTP_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
