package com.ers.emergencyresponseapp.coordination.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records coordination voice notes as uncompressed mono PCM WAV.
 *
 * WAV is intentionally used instead of a device-specific AAC encoder. Some
 * Android devices can produce a valid-looking but silent MPEG-4/AAC file when
 * their selected encoder/sample-rate combination is not fully supported. PCM
 * capture also lets the app reject digitally silent recordings before upload.
 */
class VoiceRecorder(context: Context) {
    private val appContext = context.applicationContext

    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var writerThread: Thread? = null
    private var selectedSampleRate: Int = DEFAULT_SAMPLE_RATE
    private var startedAtElapsedMs: Long = 0L

    private val recording = AtomicBoolean(false)
    private val writerFailure = AtomicReference<Throwable?>(null)
    private val capturedPcmBytes = AtomicLong(0L)
    private val peakAmplitude = AtomicInteger(0)
    private val squaredAmplitudeSum = AtomicLong(0L)
    private val capturedSampleCount = AtomicLong(0L)

    val isRecording: Boolean
        @Synchronized get() = audioRecord != null && recording.get()

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Result<Unit> = runCatching {
        cancelInternal(deleteOutput = true)

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.isMicrophoneMute == true) {
            throw IOException("The microphone is muted. Turn it on and try again.")
        }

        val directory = File(appContext.cacheDir, "coordination_voice").apply {
            if (!exists() && !mkdirs()) {
                throw IOException("Unable to create the voice-message cache")
            }
        }
        val file = File(
            directory,
            String.format(
                Locale.US,
                "voice_%d_%d.wav",
                System.currentTimeMillis(),
                (1000..9999).random()
            )
        )

        val configured = createAudioRecord()
        val recorder = configured.recorder

        try {
            // Reserve the standard WAV header. It is finalized after recording.
            FileOutputStream(file, false).use { output ->
                output.write(ByteArray(WAV_HEADER_SIZE))
                output.flush()
            }

            writerFailure.set(null)
            capturedPcmBytes.set(0L)
            peakAmplitude.set(0)
            squaredAmplitudeSum.set(0L)
            capturedSampleCount.set(0L)
            selectedSampleRate = configured.sampleRate
            outputFile = file
            audioRecord = recorder

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("The microphone did not start recording")
            }

            recording.set(true)
            startedAtElapsedMs = SystemClock.elapsedRealtime()

            val thread = Thread(
                {
                    writePcmLoop(
                        recorder = recorder,
                        file = file,
                        bufferSize = configured.bufferSize
                    )
                },
                "ERS-CoordinationVoiceRecorder"
            ).apply {
                isDaemon = true
                start()
            }
            writerThread = thread
        } catch (error: Throwable) {
            recording.set(false)
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            audioRecord = null
            outputFile = null
            writerThread = null
            startedAtElapsedMs = 0L
            file.delete()
            throw error
        }
    }

    /** Stops recording and returns a validated WAV cache file. */
    @Synchronized
    fun stop(): Result<VoiceRecording> = runCatching {
        val recorder = audioRecord
            ?: throw IllegalStateException("No voice recording is active")
        val file = outputFile
            ?: throw IllegalStateException("Voice recording output is unavailable")
        val thread = writerThread
        val sampleRate = selectedSampleRate
        val elapsedMs = (SystemClock.elapsedRealtime() - startedAtElapsedMs)
            .coerceAtLeast(0L)

        audioRecord = null
        outputFile = null
        writerThread = null
        startedAtElapsedMs = 0L
        recording.set(false)

        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: Throwable) {
            // Releasing AudioRecord below also unblocks a pending read.
        }

        thread?.join(WRITER_JOIN_TIMEOUT_MS)
        if (thread?.isAlive == true) {
            thread.interrupt()
            thread.join(WRITER_INTERRUPT_JOIN_MS)
        }
        runCatching { recorder.release() }

        if (thread?.isAlive == true) {
            file.delete()
            resetCaptureMetrics()
            throw IOException("The voice recording could not be finalized")
        }

        writerFailure.getAndSet(null)?.let { failure ->
            file.delete()
            resetCaptureMetrics()
            throw IOException("The microphone recording failed", failure)
        }

        val dataBytes = (file.length() - WAV_HEADER_SIZE).coerceAtLeast(0L)
        val pcmDurationMs = if (dataBytes > 0L) {
            dataBytes * 1000L / (sampleRate.toLong() * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        } else {
            0L
        }
        val durationMs = max(pcmDurationMs, elapsedMs.coerceAtMost(pcmDurationMs + 350L))
            .coerceAtMost(MAX_RECORDING_DURATION_MS)
        val peak = peakAmplitude.get()
        val samples = capturedSampleCount.get()
        val rmsAmplitude = if (samples > 0L) {
            sqrt(squaredAmplitudeSum.get().toDouble() / samples.toDouble()).toInt()
        } else {
            0
        }

        if (durationMs < MIN_RECORDING_DURATION_MS || dataBytes <= 0L) {
            file.delete()
            resetCaptureMetrics()
            throw IOException("Record at least one second before sending")
        }
        if (peak < MIN_AUDIBLE_PEAK || rmsAmplitude < MIN_AUDIBLE_RMS) {
            file.delete()
            resetCaptureMetrics()
            throw IOException(
                "No clear microphone sound was detected. Check microphone permission, privacy/mute settings, and speak closer to the device."
            )
        }

        finalizeWavHeader(file, sampleRate, dataBytes)
        resetCaptureMetrics()

        VoiceRecording(
            file = file,
            durationMs = durationMs,
            mimeType = "audio/wav",
            peakAmplitude = peak,
            rmsAmplitude = rmsAmplitude
        )
    }

    /** Stops (best effort), releases the microphone, and deletes the partial file. */
    @Synchronized
    fun cancel() {
        cancelInternal(deleteOutput = true)
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): RecorderConfig {
        val sampleRates = intArrayOf(DEFAULT_SAMPLE_RATE, 44_100, 48_000)
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
        var lastError: Throwable? = null

        for (sampleRate in sampleRates) {
            val minimum = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minimum <= 0) continue

            val bufferSize = max(minimum * 2, MIN_BUFFER_BYTES)
            for (source in audioSources) {
                try {
                    @Suppress("DEPRECATION")
                    val recorder = AudioRecord(
                        source,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                        return RecorderConfig(recorder, sampleRate, bufferSize)
                    }
                    recorder.release()
                } catch (error: Throwable) {
                    lastError = error
                }
            }
        }

        throw IOException("This device could not initialize microphone recording", lastError)
    }

    private fun writePcmLoop(
        recorder: AudioRecord,
        file: File,
        bufferSize: Int
    ) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ByteArray(bufferSize)

            FileOutputStream(file, true).buffered(bufferSize).use { output ->
                while (recording.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    when {
                        read > 0 -> {
                            output.write(buffer, 0, read)
                            capturedPcmBytes.addAndGet(read.toLong())
                            updatePeakAmplitude(buffer, read)
                        }

                        read == AudioRecord.ERROR_INVALID_OPERATION ||
                            read == AudioRecord.ERROR_BAD_VALUE ||
                            read == AudioRecord.ERROR_DEAD_OBJECT -> {
                            if (recording.get()) {
                                throw IOException("Microphone read failed with code $read")
                            }
                        }
                    }
                }
                output.flush()
            }
        } catch (error: Throwable) {
            if (recording.get()) {
                writerFailure.compareAndSet(null, error)
            }
        }
    }

    private fun updatePeakAmplitude(buffer: ByteArray, length: Int) {
        var localPeak = peakAmplitude.get()
        var localSquaredSum = 0L
        var localSamples = 0L
        var index = 0
        val safeLength = length - (length % 2)
        while (index < safeLength) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val amplitude = abs(sample)
            if (amplitude > localPeak) localPeak = amplitude
            localSquaredSum += sample.toLong() * sample.toLong()
            localSamples++
            index += 2
        }

        squaredAmplitudeSum.addAndGet(localSquaredSum)
        capturedSampleCount.addAndGet(localSamples)

        var current = peakAmplitude.get()
        while (localPeak > current && !peakAmplitude.compareAndSet(current, localPeak)) {
            current = peakAmplitude.get()
        }
    }

    private fun finalizeWavHeader(file: File, sampleRate: Int, dataBytes: Long) {
        if (dataBytes > Int.MAX_VALUE.toLong()) {
            throw IOException("The voice recording is too large")
        }

        val byteRate = sampleRate * CHANNEL_COUNT * BYTES_PER_SAMPLE
        val blockAlign = CHANNEL_COUNT * BYTES_PER_SAMPLE
        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0L)
            wav.writeBytes("RIFF")
            wav.writeLittleEndianInt((36L + dataBytes).toInt())
            wav.writeBytes("WAVE")
            wav.writeBytes("fmt ")
            wav.writeLittleEndianInt(16)
            wav.writeLittleEndianShort(1) // PCM
            wav.writeLittleEndianShort(CHANNEL_COUNT)
            wav.writeLittleEndianInt(sampleRate)
            wav.writeLittleEndianInt(byteRate)
            wav.writeLittleEndianShort(blockAlign)
            wav.writeLittleEndianShort(BITS_PER_SAMPLE)
            wav.writeBytes("data")
            wav.writeLittleEndianInt(dataBytes.toInt())
        }
    }

    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun cancelInternal(deleteOutput: Boolean) {
        val recorder = audioRecord
        val file = outputFile
        val thread = writerThread

        audioRecord = null
        outputFile = null
        writerThread = null
        startedAtElapsedMs = 0L
        recording.set(false)

        if (recorder != null) {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
        }
        runCatching { thread?.join(WRITER_INTERRUPT_JOIN_MS) }
        runCatching { recorder?.release() }

        if (deleteOutput) file?.delete()
        resetCaptureMetrics()
    }

    private fun resetCaptureMetrics() {
        writerFailure.set(null)
        capturedPcmBytes.set(0L)
        peakAmplitude.set(0)
        squaredAmplitudeSum.set(0L)
        capturedSampleCount.set(0L)
    }

    private data class RecorderConfig(
        val recorder: AudioRecord,
        val sampleRate: Int,
        val bufferSize: Int
    )

    companion object {
        const val MAX_RECORDING_DURATION_MS: Long = 120_000L

        private const val DEFAULT_SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        private const val WAV_HEADER_SIZE = 44
        private const val MIN_BUFFER_BYTES = 4096
        private const val MIN_RECORDING_DURATION_MS = 800L
        private const val MIN_AUDIBLE_PEAK = 64
        private const val MIN_AUDIBLE_RMS = 8
        private const val WRITER_JOIN_TIMEOUT_MS = 3_000L
        private const val WRITER_INTERRUPT_JOIN_MS = 500L
    }
}

data class VoiceRecording(
    val file: File,
    val durationMs: Long,
    val mimeType: String,
    val peakAmplitude: Int,
    val rmsAmplitude: Int
)
