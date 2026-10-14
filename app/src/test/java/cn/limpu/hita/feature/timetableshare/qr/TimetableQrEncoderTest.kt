package cn.limpu.hita.feature.timetableshare.qr

import cn.limpu.hita.feature.timetableshare.protocol.TimetableShareCodec
import cn.limpu.hita.feature.timetableshare.protocol.SharedOccurrence
import cn.limpu.hita.feature.timetableshare.protocol.fullFixture
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableQrEncoderTest {
    @Test fun selectsConservativeByteModeCapacityBoundaries() {
        assertEquals(ErrorCorrectionLevel.Q, ready(tokenOfLength(1663)).level)
        assertEquals(ErrorCorrectionLevel.M, ready(tokenOfLength(1664)).level)
        assertEquals(ErrorCorrectionLevel.M, ready(tokenOfLength(2331)).level)
        assertEquals(TimetableQrResult.TooLarge, encodeTimetableQr(tokenOfLength(2332)))
    }

    @Test fun completeHita2TokenRoundTripsThroughQrPixels() {
        val token = TimetableShareCodec().encode(fullFixture())
        val matrix = ready(token).matrix
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        assertEquals(token, QRCodeReader().decode(image, mapOf(DecodeHintType.PURE_BARCODE to true)).text)
    }

    @Test fun regularFullTermUsesOneQLevelSymbol() {
        val base = fullFixture()
        val events = (0 until 16).flatMap { week ->
            (0 until 16).map { course ->
                val start = base.occurrences.single().startMillis +
                    (week * 7L + course % 5) * 86_400_000L + (course / 5) * 7_200_000L
                SharedOccurrence(start, start + 6_300_000L, course, "教学楼${course % 3 + 1}-${100 + course}")
            }
        }
        val snapshot = base.copy(courses = (0 until 16).map { "合成课程${it + 1}" }, occurrences = events)
        val token = TimetableShareCodec().encode(snapshot)
        assertEquals(ErrorCorrectionLevel.Q, ready(token).level)
    }

    @Test fun rejectsInvalidPayloadBeforeRendering() {
        assertThrows(IllegalArgumentException::class.java) { encodeTimetableQr("HITA1:AAAA") }
        assertThrows(IllegalArgumentException::class.java) { encodeTimetableQr("HITA2:中文") }
        assertThrows(IllegalArgumentException::class.java) { encodeTimetableQr("HITA2:") }
    }

    private fun ready(token: String): TimetableQrResult.Ready {
        val result = encodeTimetableQr(token)
        assertTrue("Expected QR for ${token.length} characters, got $result", result is TimetableQrResult.Ready)
        return result as TimetableQrResult.Ready
    }

    private fun tokenOfLength(length: Int) = "HITA2:" + "a".repeat(length - 6)
}
