package fr.mangatheque.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Enregistrement, redimensionnement et recadrage des photos (tout reste sur le téléphone). */
object Images {
    private const val MAX_SIDE = 1600
    private val client = OkHttpClient()

    fun photosDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(context: Context, prefix: String = "img"): File =
        File(photosDir(context), "${prefix}_${UUID.randomUUID().toString().take(8)}.jpg")

    /** Copie une image (URI de l'appareil photo ou de la galerie) dans le dossier de l'appli, réduite. */
    suspend fun importUri(context: Context, uri: Uri, prefix: String = "img"): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decodeScaled(context, uri) ?: return@runCatching null
            val out = newFile(context, prefix)
            save(bmp, out)
            out
        }.getOrNull()
    }

    /** Télécharge une couverture distante dans le dossier de l'appli. */
    suspend fun download(context: Context, url: String, prefix: String = "cover"): File? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "Mangatheque/1.0").build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val bytes = r.body?.bytes() ?: return@runCatching null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                val out = newFile(context, prefix)
                save(bmp, out)
                out
            }
        }.getOrNull()
    }

    /**
     * Recadre une photo prise par l'appareil : [frame] est le rectangle (en fraction 0..1 de l'image
     * déjà orientée) correspondant au cadre affiché à l'écran.
     */
    suspend fun cropToFrame(context: Context, source: File, frame: android.graphics.RectF, prefix: String = "cover"): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bmp = decodeFileOriented(source, 2400) ?: return@runCatching null
                val rect = Rect(
                    (frame.left * bmp.width).toInt().coerceIn(0, bmp.width - 1),
                    (frame.top * bmp.height).toInt().coerceIn(0, bmp.height - 1),
                    (frame.right * bmp.width).toInt().coerceIn(1, bmp.width),
                    (frame.bottom * bmp.height).toInt().coerceIn(1, bmp.height)
                )
                val cropped = Bitmap.createBitmap(bmp, rect.left, rect.top, rect.width(), rect.height())
                val tightened = AutoCrop.tighten(cropped)
                val out = newFile(context, prefix)
                save(scaleDown(tightened, MAX_SIDE), out)
                source.delete()
                out
            }.getOrNull()
        }

    fun save(bmp: Bitmap, file: File, quality: Int = 88) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = context.contentResolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        return scaleDown(rotate(bmp, rotation), MAX_SIDE)
    }

    fun decodeFileOriented(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = exifRotation(ExifInterface(file))
        return scaleDown(rotate(bmp, rotation), maxSide)
    }

    private fun exifRotation(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val s = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
    }
}

/**
 * Recadrage automatique simple : on retire les bords dont la couleur est uniforme (fond de table,
 * ombre) autour de la couverture, à partir du cadre déjà aligné par l'utilisateur.
 */
object AutoCrop {
    fun tighten(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 40 || h < 40) return src
        val step = maxOf(1, minOf(w, h) / 200)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Couleur de fond estimée : moyenne des pixels sur la bordure extérieure.
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (x in 0 until w step step) { for (y in listOf(0, h - 1)) { val p = pixels[y * w + x]; r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255; n++ } }
        for (y in 0 until h step step) { for (x in listOf(0, w - 1)) { val p = pixels[y * w + x]; r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255; n++ } }
        if (n == 0L) return src
        val br = (r / n).toInt(); val bg = (g / n).toInt(); val bb = (b / n).toInt()

        fun differs(p: Int): Boolean {
            val d = kotlin.math.abs((p shr 16 and 255) - br) + kotlin.math.abs((p shr 8 and 255) - bg) + kotlin.math.abs((p and 255) - bb)
            return d > 90
        }
        fun rowHasContent(y: Int): Boolean { var c = 0; for (x in 0 until w step step) if (differs(pixels[y * w + x])) c++; return c * step > w * 0.08 }
        fun colHasContent(x: Int): Boolean { var c = 0; for (y in 0 until h step step) if (differs(pixels[y * w + x])) c++; return c * step > h * 0.08 }

        var top = 0; while (top < h / 3 && !rowHasContent(top)) top += step
        var bottom = h - 1; while (bottom > h * 2 / 3 && !rowHasContent(bottom)) bottom -= step
        var left = 0; while (left < w / 3 && !colHasContent(left)) left += step
        var right = w - 1; while (right > w * 2 / 3 && !colHasContent(right)) right -= step

        val cw = right - left + 1
        val ch = bottom - top + 1
        // Si on retire presque rien, ou trop, on garde l'image telle quelle.
        if (cw < w * 0.5 || ch < h * 0.5 || (cw > w * 0.97 && ch > h * 0.97)) return src
        return Bitmap.createBitmap(src, left, top, cw, ch)
    }
}
