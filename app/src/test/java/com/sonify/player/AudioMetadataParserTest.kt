package com.sonify.player

import androidx.media3.common.C
import com.sonify.player.audio.UacAudioSink
import com.sonify.player.data.TrackEntity
import com.sonify.player.library.AudioMetadataParser
import com.sonify.player.usb.UsbAudioNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class AudioMetadataParserTest {

    @Test
    fun parseFlacStream_extractsHiResStreamInfoAndVorbisTags() {
        val sampleRate = 96000
        val channels = 2
        val bitDepth = 24
        val totalSamples = 96000L * 180L // 180s

        // 构造合法的 FLAC 头部：fLaC + STREAMINFO(type=0) + VORBIS_COMMENT(type=4, last=1)
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

        // Block 0: STREAMINFO (34 bytes)
        out.write(0x00) // isLast=0, type=0
        out.write(0x00); out.write(0x00); out.write(34)
        val si = ByteArray(34)
        // bytes 10..17: 20b sr | 3b (ch-1) | 5b (bps-1) | 36b totalSamples
        val chBits = channels - 1
        val bpsBits = bitDepth - 1
        si[10] = ((sampleRate ushr 12) and 0xFF).toByte()
        si[11] = ((sampleRate ushr 4) and 0xFF).toByte()
        si[12] = (((sampleRate and 0x0F) shl 4) or ((chBits and 0x07) shl 1) or ((bpsBits ushr 4) and 0x01)).toByte()
        si[13] = (((bpsBits and 0x0F) shl 4) or ((totalSamples ushr 32).toInt() and 0x0F)).toByte()
        si[14] = ((totalSamples ushr 24) and 0xFF).toByte()
        si[15] = ((totalSamples ushr 16) and 0xFF).toByte()
        si[16] = ((totalSamples ushr 8) and 0xFF).toByte()
        si[17] = (totalSamples and 0xFF).toByte()
        out.write(si)

        // Block 1: VORBIS_COMMENT (isLast=1, type=4)
        val vc = ByteArrayOutputStream()
        fun writeLe32(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF)
            buf.write((v ushr 8) and 0xFF)
            buf.write((v ushr 16) and 0xFF)
            buf.write((v ushr 24) and 0xFF)
        }
        val vendor = "reference libFLAC".toByteArray(StandardCharsets.UTF_8)
        writeLe32(vc, vendor.size)
        vc.write(vendor)
        val comments = listOf(
            "TITLE=晴天",
            "ARTIST=周杰伦",
            "ALBUM=叶惠美",
            "TRACKNUMBER=03/11",
        )
        writeLe32(vc, comments.size)
        for (c in comments) {
            val cb = c.toByteArray(StandardCharsets.UTF_8)
            writeLe32(vc, cb.size)
            vc.write(cb)
        }
        val vcBytes = vc.toByteArray()
        out.write(0x84) // isLast=1, type=4
        out.write((vcBytes.size ushr 16) and 0xFF)
        out.write((vcBytes.size ushr 8) and 0xFF)
        out.write(vcBytes.size and 0xFF)
        out.write(vcBytes)

        val meta = AudioMetadataParser.parseFlacStream(
            ByteArrayInputStream(out.toByteArray()),
            fileSize = 60_000_000L,
        )
        assertNotNull(meta)
        assertEquals("FLAC", meta!!.format)
        assertEquals(96000, meta.sampleRate)
        assertEquals(24, meta.bitDepth)
        assertEquals(2, meta.channels)
        assertEquals(180_000L, meta.durationMs)
        assertEquals("晴天", meta.title)
        assertEquals("周杰伦", meta.artist)
        assertEquals("叶惠美", meta.album)
        assertEquals(3, meta.trackNumber)
    }

    @Test
    fun parseWavStream_extractsSampleRateBitDepthAndDuration() {
        val sampleRate = 44100
        val channels = 2
        val bitDepth = 16
        val byteRate = sampleRate * channels * (bitDepth / 8)
        val dataSize = byteRate * 10 // 10 seconds

        val out = ByteArrayOutputStream()
        fun le16(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
        }
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        out.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        le32(36 + dataSize)
        out.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
        out.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
        le32(16)
        le16(1) // PCM
        le16(channels)
        le32(sampleRate)
        le32(byteRate)
        le16(channels * (bitDepth / 8))
        le16(bitDepth)
        out.write("data".toByteArray(StandardCharsets.US_ASCII))
        le32(dataSize)

        val meta = AudioMetadataParser.parseWavStream(
            ByteArrayInputStream(out.toByteArray()),
            fileSize = (44 + dataSize).toLong(),
        )
        assertNotNull(meta)
        assertEquals("WAV", meta!!.format)
        assertEquals(44100, meta.sampleRate)
        assertEquals(16, meta.bitDepth)
        assertEquals(2, meta.channels)
        assertEquals(10_000L, meta.durationMs)
    }

    @Test
    fun parseFilenameFallback_handlesChineseBracketsAndTrackNumbers() {
        val (title, artist, num) = AudioMetadataParser.parseFilenameFallback(
            File("/sdcard/Music/(06) [周杰倫] 黑色幽默.flac")
        )
        assertEquals("黑色幽默", title)
        assertEquals("周杰倫", artist)
        assertEquals(6, num)
    }

    @Test
    fun detectMagicFormat_rejectsFakeExtensions() {
        val bogus = "NOT_AUDIO_DATA".toByteArray(StandardCharsets.US_ASCII)
        assertNull(AudioMetadataParser.detectMagicFormat(bogus, bogus.size, "flac"))
        val flac = "fLaC12345678".toByteArray(StandardCharsets.US_ASCII)
        assertEquals("FLAC", AudioMetadataParser.detectMagicFormat(flac, flac.size, "flac"))
    }

    @Test
    fun pcmEncodingMappingAndTrackBadges() {
        assertEquals(UsbAudioNative.PCM_ENC_16BIT, UacAudioSink.mapPcmEncoding(C.ENCODING_PCM_16BIT))
        assertEquals(UsbAudioNative.PCM_ENC_24BIT, UacAudioSink.mapPcmEncoding(C.ENCODING_PCM_24BIT))
        assertEquals(UsbAudioNative.PCM_ENC_32BIT, UacAudioSink.mapPcmEncoding(C.ENCODING_PCM_32BIT))
        assertEquals(UsbAudioNative.PCM_ENC_FLOAT, UacAudioSink.mapPcmEncoding(C.ENCODING_PCM_FLOAT))

        val hiRes = TrackEntity(
            path = "/a.flac",
            title = "Test",
            artist = "A",
            album = "B",
            sampleRate = 96000,
            bitDepth = 24,
            bitrateKbps = 2950,
            format = "FLAC",
        )
        assertTrue(hiRes.isHiRes)
        assertEquals("FLAC 96k/24b", hiRes.formatBadgeShort())
        assertEquals("FLAC · 96kHz / 24bit · 2950 kbps", hiRes.formatBadgeFull())
    }
}
