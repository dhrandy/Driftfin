package tv.jetflix.phone

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/** Clearly labeled synthetic poster art for tests: a two-tone gradient carrying the item's
 *  real long fixture title and year, so captures prove artwork loads and the title stays legible
 *  (the grid itself is intentionally poster-only). */
object TestArt {
    fun poster(seed: Int, title: String, footer: String = ""): ByteArray {
        val w = 240; val h = 360
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val hue = ((seed * 47) % 360).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.75f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.7f, 0.3f))
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, 0x80000000.toInt())
        }
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, tp, w - 32).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save(); c.translate(16f, h * 0.42f); layout.draw(c); c.restore()
        if (footer.isNotEmpty()) {
            val fp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f; alpha = 220 }
            c.drawText(footer, 16f, h - 18f, fp)
        }
        val o = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, o)
        return o.toByteArray()
    }
}
