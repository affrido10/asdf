package com.paperscan.app

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var scansContainer: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var scanButton: Button
    private var pendingExportFile: File? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            if (pdfUri == null) {
                showToast("Сканер не вернул PDF-файл")
            } else {
                saveScannedPdf(pdfUri)
            }
        } else if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
            showToast("Не удалось получить результат сканирования")
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val source = pendingExportFile
        pendingExportFile = null
        if (destination != null && source != null) {
            copyToUri(source, destination)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 247, 251)
        window.navigationBarColor = Color.rgb(245, 247, 251)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildScreen()
        refreshScans()
    }

    override fun onResume() {
        super.onResume()
        if (::scansContainer.isInitialized) refreshScans()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 251))
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandMark = ImageView(this).apply {
            setImageResource(R.drawable.paperscan_logo)
            contentDescription = "Логотип PaperScan"
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        brandRow.addView(brandMark, LinearLayout.LayoutParams(dp(52), dp(52)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandText.addView(textView("PaperScan", 23f, Color.rgb(23, 32, 51), true))
        brandText.addView(textView("СКАНЕР ДОКУМЕНТОВ", 10f, Color.rgb(112, 124, 145), true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(2), 0, 0)
        })
        brandRow.addView(brandText)
        page.addView(brandRow)

        val headline = textView("Бумага в PDF", 30f, Color.rgb(23, 32, 51), true)
        headline.setPadding(0, dp(30), 0, dp(7))
        page.addView(headline)
        page.addView(textView("Снимите документ или добавьте фото из галереи. Выравнивание, обрезка и обработка доступны в режиме сканирования.", 15f, Color.rgb(89, 101, 122), false).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        scanButton = Button(this).apply {
            text = "＋   Сканировать документ"
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(47, 111, 237), 16)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { startScanner() }
        }
        val scanParams = LinearLayout.LayoutParams(-1, dp(60)).apply {
            topMargin = dp(22)
        }
        page.addView(scanButton, scanParams)

        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 16, Color.rgb(228, 233, 242))
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        val noteTitle = textView("Что доступно", 14f, Color.rgb(23, 32, 51), true)
        note.addView(noteTitle)
        val features = textView("•  Импорт фото из галереи\n•  Автоматическое определение границ и коррекция перспективы\n•  Чёрно-белые и цветные фильтры\n•  Несколько страниц в одном PDF", 13f, Color.rgb(89, 101, 122), false)
        features.setLineSpacing(dp(5).toFloat(), 1f)
        features.setPadding(0, dp(7), 0, 0)
        note.addView(features)
        page.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val listTitle = textView("Мои документы", 20f, Color.rgb(23, 32, 51), true)
        listHeader.addView(listTitle, LinearLayout.LayoutParams(0, -2, 1f))
        listHeader.addView(textView("ЛОКАЛЬНО", 10f, Color.rgb(112, 124, 145), true).apply {
            letterSpacing = 0.10f
        })
        page.addView(listHeader, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
            bottomMargin = dp(12)
        })

        emptyState = textView("Пока нет сканов. Создайте первый документ — PDF появится здесь.", 14f, Color.rgb(112, 124, 145), false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            background = rounded(Color.WHITE, 15, Color.rgb(228, 233, 242))
        }
        page.addView(emptyState, LinearLayout.LayoutParams(-1, -2))

        scansContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(scansContainer, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun startScanner() {
        scanButton.isEnabled = false
        scanButton.alpha = 0.75f
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scanButton.isEnabled = true
                scanButton.alpha = 1f
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                scanButton.isEnabled = true
                scanButton.alpha = 1f
                showToast("Не удалось запустить сканер. Проверьте Google Play services.")
            }
    }

    private fun saveScannedPdf(sourceUri: Uri) {
        scanButton.isEnabled = false
        scanButton.alpha = 0.75f
        Thread {
            var saved: File? = null
            var failure: Exception? = null
            try {
                val directory = File(filesDir, "scans")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Не удалось создать каталог документов")
                }
                val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val file = File(directory, "PaperScan_${formatter.format(Date())}.pdf")
                val input = contentResolver.openInputStream(sourceUri)
                    ?: throw IOException("Не удалось прочитать PDF сканера")
                input.use { source -> file.outputStream().use { destination -> source.copyTo(destination) } }
                if (file.length() == 0L) throw IOException("Получен пустой PDF")
                saved = file
            } catch (e: Exception) {
                failure = e
            }
            runOnUiThread {
                scanButton.isEnabled = true
                scanButton.alpha = 1f
                if (saved != null) {
                    refreshScans()
                    showToast("PDF сохранён в PaperScan")
                    AlertDialog.Builder(this)
                        .setTitle("Документ готов")
                        .setMessage("PDF сохранён в приложении. Его можно открыть, экспортировать в папку устройства или отправить.")
                        .setPositiveButton("Экспортировать") { _, _ -> exportPdf(saved!!) }
                        .setNeutralButton("Отправить") { _, _ -> sharePdf(saved!!) }
                        .setNegativeButton("Позже", null)
                        .show()
                } else {
                    showToast("Не удалось сохранить PDF: ${failure?.localizedMessage ?: "неизвестная ошибка"}")
                }
            }
        }.start()
    }

    private fun refreshScans() {
        if (!::scansContainer.isInitialized) return
        scansContainer.removeAllViews()
        val directory = File(filesDir, "scans")
        val files = directory.listFiles { file -> file.isFile && file.extension.equals("pdf", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        files.forEach { file -> scansContainer.addView(makeScanCard(file), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        }) }
    }

    private fun makeScanCard(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(Color.WHITE, 15, Color.rgb(228, 233, 242))
        }
        val fileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pdfBadge = TextView(this).apply {
            text = "PDF"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(47, 111, 237))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(235, 241, 255), 9)
        }
        fileRow.addView(pdfBadge, LinearLayout.LayoutParams(dp(46), dp(46)))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val name = textView(file.nameWithoutExtension, 14f, Color.rgb(23, 32, 51), true)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.MIDDLE
        details.addView(name)
        val dateText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified()))
        details.addView(textView("$dateText · ${humanSize(file.length())}", 12f, Color.rgb(112, 124, 145), false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        fileRow.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(fileRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(actionText("Открыть") { openPdf(file) }, weightedActionParams())
        actions.addView(actionText("Экспорт") { exportPdf(file) }, weightedActionParams())
        actions.addView(actionText("Word") { convertPdfToWord(file) }, weightedActionParams())
        actions.addView(actionText("Отправить") { sharePdf(file) }, weightedActionParams())
        actions.addView(actionText("Удалить", destructive = true) { confirmDelete(file) }, weightedActionParams())
        card.addView(actions, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        return card
    }


    /**
     * Exact-layout export: every rendered PDF page is placed as a full-page image in DOCX.
     * Tesseract OCR (rus+eng) is also embedded as hidden text so it can be searched/copied
     * in compatible Word processors without changing the visible page layout.
     */
    private fun convertPdfToWord(pdfFile: File) {
        val modes = arrayOf(
            "Редактируемый Word — настоящий текст на тех же местах",
            "Точная копия — страницы картинками + скрытый текст"
        )
        AlertDialog.Builder(this)
            .setTitle("Экспорт в Word")
            .setItems(modes) { _, which -> runWordExport(pdfFile, editable = which == 0) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun runWordExport(pdfFile: File, editable: Boolean) {
        showToast("Распознавание и подготовка Word… При первом запуске загружаются языковые модели.")
        Thread {
            var result: File? = null
            val recognized = StringBuilder()
            var error: Exception? = null
            try {
                result = buildExactLayoutDocx(pdfFile, editable) { text ->
                    recognized.append(text).append("\n\n")
                }
            } catch (e: Throwable) {
                error = if (e is Exception) e else Exception(e)
            }
            val finalFile = result
            val finalText = recognized.toString().trim()
            val finalError = error
            runOnUiThread {
                if (finalFile != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Word готов")
                        .setMessage(
                            if (editable) "Создан файл ${finalFile.name}. Текст можно редактировать, строки стоят на тех же местах, что и в скане. Фотографии, линии и печати в этот режим не переносятся."
                            else "Создан файл ${finalFile.name}. Страницы сохранены картинками, распознанный текст добавлен скрытым слоем."
                        )
                        .setPositiveButton("Экспортировать") { _, _ -> exportDocx(finalFile) }
                        .setNeutralButton("Проверить OCR") { _, _ -> showOcrPreview(finalText) }
                        .setNegativeButton("Позже", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Не удалось создать Word")
                        .setMessage(finalError?.localizedMessage ?: "Неизвестная ошибка")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun buildExactLayoutDocx(pdfFile: File, editable: Boolean, onOcrPage: (String) -> Unit): File {
        val modelDir = File(filesDir, "tesseract")
        val tessData = File(modelDir, "tessdata")
        if (!tessData.exists() && !tessData.mkdirs()) throw IOException("Не удалось создать каталог языковых моделей")
        downloadModelIfNeeded(File(tessData, "rus.traineddata"), "rus")
        downloadModelIfNeeded(File(tessData, "eng.traineddata"), "eng")

        val tess = TessBaseAPI()
        if (!tess.init(modelDir.absolutePath, "rus+eng")) throw IOException("Не удалось запустить OCR")
        val pageImages = mutableListOf<File>()
        val pageTexts = mutableListOf<String>()
        val pageLayouts = mutableListOf<OcrPage>()
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) throw IOException("В PDF нет страниц")
                    if (renderer.pageCount > 40) throw IOException("В текущей версии поддерживается до 40 страниц за один экспорт")
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = (1800f / page.width).coerceAtMost(2.5f)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            tess.setImage(bitmap)
                            val text: String
                            if (editable) {
                                val hocr = tess.getHOCRText(0) ?: ""
                                val lines = parseHocrLines(hocr)
                                pageLayouts.add(OcrPage(page.width.toFloat(), page.height.toFloat(), scale, lines))
                                text = lines.joinToString("\n") { it.text }
                            } else {
                                val imageFile = File(cacheDir, "docx_page_${i + 1}.jpg")
                                imageFile.outputStream().use { out ->
                                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw IOException("Не удалось подготовить изображение страницы")
                                }
                                text = tess.getUTF8Text() ?: ""
                                pageImages.add(imageFile)
                            }
                            tess.clear()
                            pageTexts.add(text)
                            onOcrPage(text)
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            tess.recycle()
        }

        val outDir = File(filesDir, "exports")
        if (!outDir.exists() && !outDir.mkdirs()) throw IOException("Не удалось создать каталог экспорта")
        if (editable) {
            val docx = File(outDir, pdfFile.nameWithoutExtension + "_editable.docx")
            writeEditableDocx(docx, pageLayouts)
            return docx
        }
        val docx = File(outDir, pdfFile.nameWithoutExtension + "_exact_layout.docx")
        writeDocxWithPageImages(docx, pageImages, pageTexts)
        pageImages.forEach { it.delete() }
        return docx
    }

    private class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    private class OcrPage(val widthPt: Float, val heightPt: Float, val scale: Float, val lines: List<OcrLine>)

    private fun decodeHtml(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

    /** Reads text lines with their pixel boxes from Tesseract hOCR output. */
    private fun parseHocrLines(hocr: String): List<OcrLine> {
        val lineStart = Regex("""<span class=['"]ocr(?:_line|_header|_textfloat|_caption)['"][^>]*?title=['"]bbox (\d+) (\d+) (\d+) (\d+)""")
        val word = Regex("""<span class=['"]ocrx_word['"][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        val tag = Regex("<[^>]+>")
        val starts = lineStart.findAll(hocr).toList()
        val result = ArrayList<OcrLine>()
        for ((index, m) in starts.withIndex()) {
            val end = if (index + 1 < starts.size) starts[index + 1].range.first else hocr.length
            val chunk = hocr.substring(m.range.last, end)
            val words = word.findAll(chunk)
                .map { decodeHtml(tag.replace(it.groupValues[1], "")).trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (words.isEmpty()) continue
            val (l, t, r, b) = m.destructured
            result.add(OcrLine(words.joinToString(" "), l.toInt(), t.toInt(), r.toInt(), b.toInt()))
        }
        return result
    }

    /**
     * Editable DOCX: each recognized line is a real text paragraph in an absolutely positioned frame,
     * placed where it was on the scanned page. Font size and width are fitted to the line's box.
     */
    private fun writeEditableDocx(docx: File, pages: List<OcrPage>) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SANS_SERIF
        }
        val body = StringBuilder()
        pages.forEachIndexed { pageIndex, page ->
            val pxToTwip = 20f / page.scale
            for (line in page.lines) {
                val heightPt = (line.bottom - line.top) / page.scale
                val widthPt = (line.right - line.left) / page.scale
                if (heightPt < 2f || widthPt < 2f) continue
                val fontPt = (heightPt * 0.78f).coerceIn(5f, 48f)
                paint.textSize = fontPt
                val natural = paint.measureText(line.text).coerceAtLeast(1f)
                val stretch = (widthPt / natural * 100f).coerceIn(50f, 160f).toInt()
                val x = (line.left * pxToTwip).toInt()
                val y = (line.top * pxToTwip).toInt()
                val w = ((line.right - line.left) * pxToTwip * 1.06f).toInt() + 40
                val h = ((line.bottom - line.top) * pxToTwip).toInt().coerceAtLeast(60)
                val halfPts = (fontPt * 2).toInt()
                body.append("<w:p><w:pPr><w:framePr w:w=\"$w\" w:h=\"$h\" w:hRule=\"exact\" w:wrap=\"around\" w:hAnchor=\"page\" w:vAnchor=\"page\" w:x=\"$x\" w:y=\"$y\"/>")
                body.append("<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"$h\" w:lineRule=\"exact\"/></w:pPr>")
                body.append("<w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/><w:w w:val=\"$stretch\"/><w:sz w:val=\"$halfPts\"/><w:szCs w:val=\"$halfPts\"/></w:rPr>")
                body.append("<w:t xml:space=\"preserve\">${xmlEscape(line.text)}</w:t></w:r></w:p>")
            }
            // Anchor paragraph for this page's frames; carries the page break.
            body.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"20\" w:lineRule=\"exact\"/></w:pPr>")
            if (pageIndex < pages.lastIndex) body.append("<w:r><w:br w:type=\"page\"/></w:r>")
            body.append("</w:p>")
        }
        val first = pages.first()
        val pgW = (first.widthPt * 20).toInt()
        val pgH = (first.heightPt * 20).toInt()
        ZipOutputStream(docx.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""")
            entry("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
            entry("word/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/><w:sz w:val="20"/></w:rPr></w:style></w:styles>""")
            entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="$pgW" w:h="$pgH"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>""")
        }
    }

    private fun downloadModelIfNeeded(file: File, language: String) {
        if (file.exists() && file.length() > 100_000L) return
        val url = URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/$language.traineddata")
        val temp = File(file.parentFile, file.name + ".part")
        try {
            url.openConnection().apply {
                connectTimeout = 20000
                readTimeout = 60000
            }.getInputStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (temp.length() < 100_000L) throw IOException("Языковая модель $language загружена не полностью")
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) throw IOException("Не удалось сохранить модель $language")
        } catch (e: Exception) {
            temp.delete()
            throw IOException("Не удалось загрузить OCR-модель $language. Проверьте подключение к интернету и повторите попытку.", e)
        }
    }

    private fun writeDocxWithPageImages(docx: File, images: List<File>, texts: List<String>) {
        val pageWidthEmu = 7560000L // 8.27 in content width with modest margins
        val pageHeightEmu = 10692000L
        ZipOutputStream(docx.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="jpg" ContentType="image/jpeg"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""")
            entry("word/_rels/document.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                append("""<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                images.indices.forEach { append("""<Relationship Id="rIdImg${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/page${it + 1}.jpg"/>""") }
                append("</Relationships>")
            })
            entry("word/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="10"/></w:rPr></w:style></w:styles>""")
            val body = StringBuilder()
            images.forEachIndexed { index, imageFile ->
                val id = index + 1
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
                val ratio = if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toDouble() / bounds.outHeight else 1.0
                val maxW = pageWidthEmu.toDouble()
                val maxH = pageHeightEmu.toDouble()
                val imageW = minOf(maxW, maxH * ratio).toLong()
                val imageH = minOf(maxH, maxW / ratio).toLong()
                body.append("""<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="1" w:lineRule="exact"/></w:pPr><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$imageW" cy="$imageH"/><wp:docPr id="$id" name="Scanned page $id"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="page$id.jpg"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImg$id" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$imageW" cy="$imageH"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
                // Hidden OCR text remains searchable/copyable in many Office-compatible editors.
                val escaped = xmlEscape(texts.getOrElse(index) { "" })
                if (escaped.isNotBlank()) {
                    body.append("""<w:p><w:pPr><w:spacing w:before="0" w:after="0"/></w:pPr><w:r><w:rPr><w:vanish/><w:sz w:val="1"/></w:rPr><w:t xml:space="preserve">$escaped</w:t></w:r></w:p>""")
                }
                if (index < images.lastIndex) body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
            entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>""")
            images.forEachIndexed { index, image ->
                zip.putNextEntry(ZipEntry("word/media/page${index + 1}.jpg"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun xmlEscape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
        .filter { it == '\n' || it == '\t' || it == '\r' || it >= ' ' }

    private fun exportDocx(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        docxExportFile = file
        docxExportLauncher.launch(intent)
    }

    private var docxExportFile: File? = null
    private val docxExportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val source = docxExportFile
        docxExportFile = null
        val destination = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && source != null && destination != null) {
            copyToUri(source, destination)
        }
    }

    private fun showOcrPreview(text: String) {
        val preview = if (text.isBlank()) "OCR не распознал текст на этих страницах." else text.take(12000)
        val content = TextView(this).apply {
            this.text = preview
            textSize = 14f
            setTextColor(Color.rgb(23, 32, 51))
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("Распознанный текст").setView(scroll)
            .setPositiveButton("Закрыть", null).show()
    }

    private fun openPdf(file: File) {
        val uri = fileUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToast("На устройстве нет приложения для просмотра PDF. Используйте «Экспорт».")
        } catch (e: Exception) {
            showToast("Не удалось открыть файл PDF")
        }
    }

    private fun exportPdf(file: File) {
        pendingExportFile = file
        exportLauncher.launch(file.name)
    }

    private fun sharePdf(file: File) {
        try {
            val uri = fileUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Отправить PDF"))
        } catch (e: Exception) {
            showToast("Не удалось подготовить PDF для отправки")
        }
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Удалить документ?")
            .setMessage("Файл «${file.name}» будет удалён из PaperScan.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                if (file.delete()) {
                    refreshScans()
                    showToast("Документ удалён")
                } else showToast("Не удалось удалить документ")
            }
            .show()
    }

    private fun copyToUri(source: File, destination: Uri) {
        Thread {
            var error: Exception? = null
            try {
                val output = contentResolver.openOutputStream(destination)
                    ?: throw IOException("Не удалось открыть место назначения")
                source.inputStream().use { input -> output.use { target -> input.copyTo(target) } }
            } catch (e: Exception) {
                error = e
            }
            runOnUiThread {
                if (error == null) showToast("Файл экспортирован")
                else showToast("Ошибка экспорта: ${error?.localizedMessage ?: "неизвестная ошибка"}")
            }
        }.start()
    }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun actionText(label: String, destructive: Boolean = false, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (destructive) Color.rgb(190, 59, 63) else Color.rgb(47, 111, 237))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun weightedActionParams() = LinearLayout.LayoutParams(0, -1, 1f)

    private fun textView(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f КБ", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
