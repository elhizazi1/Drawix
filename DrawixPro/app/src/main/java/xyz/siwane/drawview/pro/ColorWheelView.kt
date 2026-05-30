package xyz.siwane.drawix.pro

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class ColorWheelView(context: Context) : View(context) {
    var onColorSelected: ((Int) -> Unit)? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // إعداد طلاء إطار المؤشر مع الظل الاحترافي
    private val pointerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
        setShadowLayer(6f, 0f, 4f, Color.parseColor("#60000000")) // ظل ناعم وأنيق
    }
    
    // إعداد طلاء قلب المؤشر ليعرض اللون الحي
    private val pointerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private var pointerX = 0f
    private var pointerY = 0f
    private var isFirstDraw = true
    private var currentColor = Color.WHITE // اللون الحي داخل المؤشر

    init {
        // ضروري جداً لتفعيل خاصية الظل (ShadowLayer) في الأندرويد
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) - 15f // مساحة مريحة للظل

        if (isFirstDraw) {
            // التعديل الجراحي المدمج هنا: ترتيب الألوان القياسي (HSV) مع عقارب الساعة
            val sweep = SweepGradient(cx, cy, intArrayOf(
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
            ), null)
            
            val radial = RadialGradient(cx, cy, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
            paint.shader = ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER)
            
            pointerX = cx
            pointerY = cy
            isFirstDraw = false
        }

        // رسم عجلة الألوان
        canvas.drawCircle(cx, cy, radius, paint)

        // رسم المؤشر الاحترافي (لون حي بالداخل + إطار أبيض + ظل)
        pointerFillPaint.color = currentColor
        canvas.drawCircle(pointerX, pointerY, 16f, pointerFillPaint)
        canvas.drawCircle(pointerX, pointerY, 16f, pointerStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) - 15f

        val dx = event.x - cx
        val dy = event.y - cy
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            if (dist <= radius) {
                pointerX = event.x
                pointerY = event.y
            } else {
                pointerX = cx + dx * (radius / dist)
                pointerY = cy + dy * (radius / dist)
            }

            // حساب درجة اللون (Hue) بالاتجاه الصحيح برمجياً
            var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angle < 0) angle += 360f
            
            val actualDist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val sat = (actualDist / radius).coerceIn(0f, 1f)

            // تحويل القيم إلى لون حقيقي وإرساله للوحة وتلوين قلب المؤشر
            currentColor = Color.HSVToColor(floatArrayOf(angle, sat, 1f))
            onColorSelected?.invoke(currentColor)
            
            invalidate() 
            return true
        }
        return super.onTouchEvent(event)
    }
}
