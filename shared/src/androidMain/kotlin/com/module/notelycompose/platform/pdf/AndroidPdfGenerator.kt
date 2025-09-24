package com.module.notelycompose.platform.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.net.toUri

class AndroidPdfGenerator(private val context: Context) {

    companion object Companion {
        private const val PAGE_WIDTH = 612 // 8.5 inches * 72 points per inch
        private const val PAGE_HEIGHT = 792 // 11 inches * 72 points per inch
        private const val MARGIN = 72 // 1 inch margin
        private const val LINE_SPACING = 1.2f
    }

    private val contentWidth = PAGE_WIDTH - (2 * MARGIN)
    private val contentHeight = PAGE_HEIGHT - (2 * MARGIN)

    /**
     * Create PDF document from text and save to target URI
     */
    fun createPdfDocument(text: String, targetUri: String, textSize: Float): Boolean {
        return try {
            val pdfDocument = PdfDocument()
            val textPaint = createTextPaint(textSize)
            val pages = splitTextIntoPages(text, textPaint, contentWidth, contentHeight)

            pages.forEachIndexed { pageIndex, pageText ->
                addPageToPdf(pdfDocument, pageText, textPaint, pageIndex + 1)
            }

            // Write PDF to target URI
            val uri = targetUri.toUri()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createTextPaint(size: Float): TextPaint {
        return TextPaint().apply {
            color = android.graphics.Color.BLACK
            textSize = size
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
    }

    private fun addPageToPdf(
        pdfDocument: PdfDocument,
        pageText: String,
        textPaint: TextPaint,
        pageNumber: Int
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Create text layout
        val staticLayout = StaticLayout.Builder
            .obtain(pageText, 0, pageText.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()

        // Draw text on canvas
        canvas.save()
        canvas.translate(MARGIN.toFloat(), MARGIN.toFloat())
        staticLayout.draw(canvas)
        canvas.restore()

        pdfDocument.finishPage(page)
    }

    private fun splitTextIntoPages(
        text: String,
        textPaint: TextPaint,
        pageWidth: Int,
        pageHeight: Int
    ): List<String> {
        val pages = mutableListOf<String>()
        val lineHeight = textPaint.fontSpacing
        val maxLinesPerPage = (pageHeight / lineHeight).toInt()

        val words = text.split(" ")
        var currentPage = StringBuilder()
        var currentLineCount = 0
        var currentLineWidth = 0f

        for (word in words) {
            val wordWidth = textPaint.measureText("$word ")

            // Check if word fits on current line
            if (currentLineWidth + wordWidth <= pageWidth) {
                currentPage.append("$word ")
                currentLineWidth += wordWidth
            } else {
                // New line needed
                currentPage.append("\n$word ")
                currentLineCount++
                currentLineWidth = wordWidth

                // Check if page is full
                if (currentLineCount >= maxLinesPerPage) {
                    pages.add(currentPage.toString().trim())
                    currentPage = StringBuilder()
                    currentLineCount = 0
                }
            }
        }

        // Add remaining text as last page
        if (currentPage.isNotEmpty()) {
            pages.add(currentPage.toString().trim())
        }

        return pages.ifEmpty { listOf("") }
    }
}
