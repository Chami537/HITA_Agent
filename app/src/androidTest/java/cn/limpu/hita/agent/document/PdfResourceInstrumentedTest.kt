package cn.limpu.hita.agent.document

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/** Exercise real PDF parsing with predefined CMaps, without a ToUnicode shortcut. */
@RunWith(AndroidJUnit4::class)
class PdfResourceInstrumentedTest {
    @Test fun onlyDependencyCmapsArePackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.assets.list("com/tom_roush/pdfbox/resources/cmap").orEmpty().isEmpty())
        assertNull(context.classLoader.getResource("com/tom_roush/pdfbox/resources/cmap/UniGB-UCS2-H"))
        for (name in listOf("UniGB-UCS2-H", "UniCNS-UCS2-H", "Adobe-GB1-UCS2", "Adobe-CNS1-UCS2")) {
            context.assets.open("com/tom_roush/fontbox/resources/cmap/$name").use {
                assertTrue("Missing dependency CMap $name", it.read() >= 0)
            }
        }
    }

    @Test fun simplifiedChineseCourseText() = checkText(
        "课程表 高等数学 线性代数 2026", "UniGB-UCS2-H", "GB1", "STSong-Light"
    )

    @Test fun traditionalChineseCourseText() = checkText(
        "課程表 高等數學 線性代數 2026", "UniCNS-UCS2-H", "CNS1", "MSung-Light"
    )

    private fun checkText(text: String, cmap: String, ordering: String, font: String) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("pdf-cmap-source-", ".pdf", context.cacheDir)
        val targetName = "parsed-${source.name}"
        try {
            source.writeBytes(makePdf(text, cmap, ordering, font))
            val result = PdfFileParser().parse(context, Uri.fromFile(source), targetName)
            assertTrue("Expected readable Chinese text, got $result", result is ParseResult.Success)
            result as ParseResult.Success
            assertTrue("Expected '$text', got '${result.text}'", result.text.contains(text))
            assertEquals(1, result.metadata.pageCount)
            assertTrue("Parser temporary file must be removed", !File(context.cacheDir, targetName).exists())
        } finally {
            source.delete()
            File(context.cacheDir, targetName).delete()
        }
    }

    // A one-page original fixture with a predefined Adobe CJK mapping. No embedded
    // font or ToUnicode map: removing required CMap resources must break this test.
    private fun makePdf(text: String, cmap: String, ordering: String, font: String): ByteArray {
        val hex = text.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it.toInt() and 255) }
        val stream = "BT /F1 12 Tf 30 780 Td <$hex> Tj ET\n"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
            "<< /Length ${stream.length} >>\nstream\n${stream}endstream",
            "<< /Type /Font /Subtype /Type0 /BaseFont /$font /Encoding /$cmap /DescendantFonts [6 0 R] >>",
            "<< /Type /Font /Subtype /CIDFontType0 /BaseFont /$font /CIDSystemInfo << /Registry (Adobe) /Ordering ($ordering) /Supplement 4 >> /FontDescriptor 7 0 R /DW 1000 >>",
            "<< /Type /FontDescriptor /FontName /$font /Flags 6 /FontBBox [0 -200 1000 900] /ItalicAngle 0 /Ascent 900 /Descent -200 /CapHeight 700 /StemV 80 >>"
        )
        val output = ByteArrayOutputStream()
        fun write(value: String) { output.write(value.toByteArray(Charsets.US_ASCII)) }
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, obj ->
            val offset = output.size()
            write("${i + 1} 0 obj\n$obj\nendobj\n")
            offset
        }
        val xref = output.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.toByteArray()
    }
}
