package xyz.siwane.drawix.pro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

// الممحاة تعود كأداة رسم مستقلة
enum class DrawMode { BRUSH, RECT, CIRCLE, ARROW, ERASER, TEXT, MOVE, LASER, NONE }

@Suppress("DEPRECATION")
data class DrawShape(
    val type: DrawMode,
    val path: Path,
    val paint: Paint,
    var startX: Float,
    var startY: Float,
    var endX: Float,
    var endY: Float,
    var text: String = "",
    var scale: Float = 1.0f,
    var rotation: Float = 0f 
) {
    fun moveBy(dx: Float, dy: Float) {
        startX += dx
        endX += dx
        startY += dy
        endY += dy
        if (type == DrawMode.BRUSH || type == DrawMode.ERASER || type == DrawMode.LASER) {
            path.offset(dx, dy)
        }
    }

    fun getCenterX(): Float {
        return when (type) {
            DrawMode.BRUSH, DrawMode.ERASER, DrawMode.LASER -> { 
                val b = RectF()
                path.computeBounds(b, true)
                b.centerX() 
            }
            DrawMode.TEXT -> startX + (paint.measureText(text) / 2)
            DrawMode.CIRCLE -> startX 
            else -> (startX + endX) / 2
        }
    }

    fun getCenterY(): Float {
        return when (type) {
            DrawMode.BRUSH, DrawMode.ERASER, DrawMode.LASER -> { 
                val b = RectF()
                path.computeBounds(b, true)
                b.centerY() 
            }
            DrawMode.TEXT -> startY - (paint.textSize / 2)
            DrawMode.CIRCLE -> startY 
            else -> (startY + endY) / 2
        }
    }

    fun contains(x: Float, y: Float): Boolean {
        val cx = getCenterX()
        val cy = getCenterY()
        
        val angleRad = Math.toRadians(-rotation.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        
        val unRotX = cx + (x - cx) * cosA - (y - cy) * sinA
        val unRotY = cy + (x - cx) * sinA + (y - cy) * cosA

        val unscaledX = cx + (unRotX - cx) / scale
        val unscaledY = cy + (unRotY - cy) / scale
        val padding = 100f 

        return when (type) {
            DrawMode.BRUSH, DrawMode.ERASER, DrawMode.LASER -> {
                val b = RectF()
                path.computeBounds(b, true)
                unscaledX in (b.left - padding)..(b.right + padding) && 
                unscaledY in (b.top - padding)..(b.bottom + padding)
            }
            DrawMode.TEXT -> {
                val w = paint.measureText(text)
                unscaledX in (startX - padding)..(startX + w + padding) && 
                unscaledY in (startY - paint.textSize - padding)..(startY + padding)
            }
            DrawMode.CIRCLE -> {
                val radius = hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
                hypot((unscaledX - startX).toDouble(), (unscaledY - startY).toDouble()).toFloat() <= (radius + padding)
            }
            else -> {
                val left = startX.coerceAtMost(endX)
                val right = startX.coerceAtLeast(endX)
                val top = startY.coerceAtMost(endY)
                val bottom = startY.coerceAtLeast(endY)
                unscaledX in (left - padding)..(right + padding) && 
                unscaledY in (top - padding)..(bottom + padding)
            }
        }
    }
}

@Suppress("DEPRECATION")
class DrawView(context: Context) : View(context) {

    var currentMode = DrawMode.NONE 
    var currentColor = Color.parseColor("#F44336")
    var isCensorMode = false 
    // تم حذف isEraserMode للتبسيط
    
    private val shapes = CopyOnWriteArrayList<DrawShape>()
    private var currentShape: DrawShape? = null
    
    private var selectedShape: DrawShape? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastDrawX = 0f
    private var lastDrawY = 0f
    
    private var isDraggingShape = false
    private var isScalingShape = false
    private var isRotatingShape = false
    private var initialRotationAngle = 0f
    private var initialShapeRotation = 0f

    private var isCometModeCache = true
    private var laserDurationCache = 2

    var onTextInputRequested: ((x: Float, y: Float) -> Unit)? = null
    var onCanvasTouched: (() -> Boolean)? = null 

    private var cacheBitmap: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private var cachedMosaicShader: BitmapShader? = null

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        setShadowLayer(4f, 0f, 0f, Color.parseColor("#80000000"))
    }

    private val controlBgPaint = Paint().apply { 
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL 
        setShadowLayer(6f, 0f, 3f, Color.parseColor("#60000000")) 
    }

    private val iconTrash = ContextCompat.getDrawable(context, R.drawable.ic_trash)?.apply { setTint(Color.parseColor("#EF4444")) }
    private val iconDuplicate = ContextCompat.getDrawable(context, R.drawable.ic_copy)?.apply { setTint(Color.parseColor("#10B981")) }
    private val iconScale = ContextCompat.getDrawable(context, R.drawable.ic_maximize)?.apply { setTint(Color.parseColor("#3B82F6")) }
    private val iconRotate = ContextCompat.getDrawable(context, R.drawable.ic_rotate_right)?.apply { setTint(Color.parseColor("#F59E0B")) }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (currentMode == DrawMode.MOVE && selectedShape != null) {
                selectedShape!!.scale *= detector.scaleFactor
                selectedShape!!.scale = selectedShape!!.scale.coerceIn(0.2f, 10.0f) 
                invalidate()
                return true
            }
            return false
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            try {
                val oldBitmap = cacheBitmap
                cacheBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                cacheCanvas = Canvas(cacheBitmap!!)
                oldBitmap?.recycle() 
                updateCache()
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            }
        }
    }

    private fun updateCache(excludeShape: DrawShape? = null) {
        if (cacheBitmap == null || cacheCanvas == null) return
        try {
            cacheBitmap!!.eraseColor(Color.TRANSPARENT)
            shapes.forEach { if (it != excludeShape) drawShapeWithScaleAndRotation(cacheCanvas!!, it) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getMosaicShader(): BitmapShader {
        if (cachedMosaicShader == null) {
            val size = 15 
            val bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { style = Paint.Style.FILL }
            paint.color = Color.parseColor("#222222") 
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            canvas.drawRect(size.toFloat(), size.toFloat(), size * 2f, size * 2f, paint)
            paint.color = Color.parseColor("#EEEEEE") 
            canvas.drawRect(size.toFloat(), 0f, size * 2f, size.toFloat(), paint)
            canvas.drawRect(0f, size.toFloat(), size.toFloat(), size * 2f, paint)
            cachedMosaicShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        return cachedMosaicShader!!
    }

    private fun createPaint(): Paint {
        val paint = Paint().apply { 
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND 
        }

        when (currentMode) {
            DrawMode.ERASER -> { 
                paint.color = Color.TRANSPARENT
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 60f
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) 
            }
            DrawMode.LASER -> { 
                paint.color = Color.parseColor("#FF1744")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 14f
                paint.setShadowLayer(20f, 0f, 0f, Color.parseColor("#FF8A80")) 
            }
            DrawMode.TEXT -> { 
                paint.color = currentColor
                paint.style = Paint.Style.FILL
                paint.textSize = 80f 
            }
            else -> {
                // التعتيم يعمل مع المربع، الدائرة، والفرشاة
                if (isCensorMode && (currentMode == DrawMode.BRUSH || currentMode == DrawMode.RECT || currentMode == DrawMode.CIRCLE)) {
                    paint.shader = getMosaicShader()
                    if (currentMode == DrawMode.BRUSH) { 
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 45f 
                    } else { 
                        paint.style = Paint.Style.FILL 
                    }
                } else {
                    paint.color = currentColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 12f
                }
            }
        }
        return paint
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try { 
            cacheBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            
            selectedShape?.let { shape ->
                drawShapeWithScaleAndRotation(canvas, shape)
                
                if (currentMode == DrawMode.MOVE) {
                    val cx = shape.getCenterX()
                    val cy = shape.getCenterY()

                    canvas.save()
                    canvas.rotate(shape.rotation, cx, cy)

                    val bounds = getVisualBounds(shape)
                    canvas.drawRect(bounds, framePaint)
                    
                    val r = 32f 
                    val iconSize = 40 

                    fun drawNativeControl(ctrlX: Float, ctrlY: Float, icon: Drawable?) {
                        canvas.save()
                        canvas.rotate(-shape.rotation, ctrlX, ctrlY) 
                        canvas.drawCircle(ctrlX, ctrlY, r, controlBgPaint)
                        icon?.let {
                            it.setBounds((ctrlX - iconSize/2).toInt(), (ctrlY - iconSize/2).toInt(), (ctrlX + iconSize/2).toInt(), (ctrlY + iconSize/2).toInt())
                            it.draw(canvas)
                        }
                        canvas.restore()
                    }

                    drawNativeControl(bounds.left, bounds.top, iconTrash)     
                    drawNativeControl(bounds.right, bounds.top, iconDuplicate)     
                    drawNativeControl(bounds.right, bounds.bottom, iconScale) 
                    drawNativeControl(bounds.left, bounds.bottom, iconRotate) 

                    canvas.restore()
                }
            }
            currentShape?.let { drawShapeWithScaleAndRotation(canvas, it) }
        } catch (e: Throwable) { e.printStackTrace() }
    }

    private fun getVisualBounds(shape: DrawShape): RectF {
        val cx = shape.getCenterX()
        val cy = shape.getCenterY()
        val padding = 50f
        val bounds = RectF()
        
        when (shape.type) {
            DrawMode.BRUSH, DrawMode.ERASER, DrawMode.LASER -> shape.path.computeBounds(bounds, true)
            DrawMode.TEXT -> { 
                val w = shape.paint.measureText(shape.text)
                bounds.set(shape.startX, shape.startY - shape.paint.textSize, shape.startX + w, shape.startY + shape.paint.descent()) 
            }
            DrawMode.CIRCLE -> { 
                val radius = hypot((shape.endX - shape.startX).toDouble(), (shape.endY - shape.startY).toDouble()).toFloat()
                bounds.set(shape.startX - radius, shape.startY - radius, shape.startX + radius, shape.startY + radius) 
            }
            else -> bounds.set(shape.startX.coerceAtMost(shape.endX), shape.startY.coerceAtMost(shape.endY), shape.startX.coerceAtLeast(shape.endX), shape.startY.coerceAtLeast(shape.endY))
        }
        
        if (bounds.width() == 0f) bounds.right += 10f
        if (bounds.height() == 0f) bounds.bottom += 10f
        
        val width = (bounds.width() + padding) * shape.scale
        val height = (bounds.height() + padding) * shape.scale
        return RectF(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
    }

    private fun drawShapeWithScaleAndRotation(canvas: Canvas, shape: DrawShape) {
        canvas.save()
        val cx = shape.getCenterX()
        val cy = shape.getCenterY()
        canvas.translate(cx, cy)
        canvas.scale(shape.scale, shape.scale)
        canvas.rotate(shape.rotation)
        canvas.translate(-cx, -cy)

        when (shape.type) {
            DrawMode.BRUSH, DrawMode.ERASER, DrawMode.LASER -> canvas.drawPath(shape.path, shape.paint)
            DrawMode.RECT -> canvas.drawRect(shape.startX.coerceAtMost(shape.endX), shape.startY.coerceAtMost(shape.endY), shape.startX.coerceAtLeast(shape.endX), shape.startY.coerceAtLeast(shape.endY), shape.paint)
            DrawMode.CIRCLE -> canvas.drawCircle(shape.startX, shape.startY, hypot((shape.endX - shape.startX).toDouble(), (shape.endY - shape.startY).toDouble()).toFloat(), shape.paint)
            DrawMode.ARROW -> drawSleekArrow(canvas, shape) 
            DrawMode.TEXT -> canvas.drawText(shape.text, shape.startX, shape.startY, shape.paint)
            DrawMode.MOVE, DrawMode.NONE -> {}
        }
        canvas.restore()
    }

    private fun drawSleekArrow(canvas: Canvas, shape: DrawShape) {
        val dx = shape.endX - shape.startX
        val dy = shape.endY - shape.startY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        
        if (distance < 5f) return 
        
        val ux = dx / distance
        val uy = dy / distance
        val px = -uy
        val py = ux
        
        val headLength = 45f.coerceAtMost(distance * 0.35f)
        val headWidth = 45f.coerceAtMost(distance * 0.35f)  
        val baseThickness = 2f
        val neckThickness = 14f  
        
        val neckX = shape.startX + ux * (distance - headLength)
        val neckY = shape.startY + uy * (distance - headLength)
        
        val arrowPath = Path().apply { 
            moveTo(shape.startX + px * (baseThickness / 2), shape.startY + py * (baseThickness / 2))
            lineTo(neckX + px * (neckThickness / 2), neckY + py * (neckThickness / 2))
            lineTo(neckX + px * (headWidth / 2), neckY + py * (headWidth / 2))
            lineTo(shape.endX, shape.endY)
            lineTo(neckX - px * (headWidth / 2), neckY - py * (headWidth / 2))
            lineTo(neckX - px * (neckThickness / 2), neckY - py * (neckThickness / 2))
            lineTo(shape.startX - px * (baseThickness / 2), shape.startY - py * (baseThickness / 2))
            close() 
        }
        
        canvas.drawPath(arrowPath, Paint(shape.paint).apply { 
            style = Paint.Style.FILL
            strokeWidth = 0f
            strokeJoin = Paint.Join.MITER 
        })
    }

    private fun mapPointBack(x: Float, y: Float, cx: Float, cy: Float, angleDegrees: Float): Pair<Float, Float> {
        val angleRad = Math.toRadians(angleDegrees.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val dx = x - cx
        val dy = y - cy
        return Pair(cx + dx * cosA - dy * sinA, cy + dx * sinA + dy * cosA)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try { 
            scaleDetector.onTouchEvent(event)
            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (onCanvasTouched?.invoke() == true) return true
                    if (currentMode == DrawMode.NONE) return true

                    if (currentMode == DrawMode.LASER) {
                        val prefs = context.getSharedPreferences("DrawSettings", Context.MODE_PRIVATE)
                        isCometModeCache = prefs.getBoolean("laser_comet_mode", true)
                        laserDurationCache = prefs.getInt("laser_duration_seconds", 2)
                    }

                    if (currentMode == DrawMode.MOVE) {
                        if (selectedShape != null) {
                            val shape = selectedShape!!
                            val cx = shape.getCenterX()
                            val cy = shape.getCenterY()

                            val (mappedX, mappedY) = mapPointBack(x, y, cx, cy, -shape.rotation)
                            val bounds = getVisualBounds(shape)
                            val touchRadius = 60f 
                            
                            if (hypot(mappedX - bounds.left, mappedY - bounds.top) <= touchRadius) {
                                shapes.remove(shape)
                                selectedShape = null
                                updateCache()
                                invalidate()
                                return true
                            }
                            
                            if (hypot(mappedX - bounds.right, mappedY - bounds.top) <= touchRadius) {
                                val duplicatedPath = Path(shape.path)
                                val duplicatedPaint = Paint(shape.paint)
                                val duplicatedShape = shape.copy(path = duplicatedPath, paint = duplicatedPaint)
                                duplicatedShape.moveBy(50f, 50f)
                                shapes.add(duplicatedShape)
                                selectedShape = duplicatedShape
                                updateCache(excludeShape = selectedShape)
                                invalidate()
                                return true
                            }
                            
                            if (hypot(mappedX - bounds.right, mappedY - bounds.bottom) <= touchRadius) {
                                isScalingShape = true
                                lastTouchX = x
                                lastTouchY = y
                                return true
                            }
                            if (hypot(mappedX - bounds.left, mappedY - bounds.bottom) <= touchRadius) {
                                isRotatingShape = true
                                initialRotationAngle = Math.toDegrees(Math.atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                                initialShapeRotation = shape.rotation
                                return true
                            }
                            if (bounds.contains(mappedX, mappedY)) {
                                isDraggingShape = true
                                lastTouchX = x
                                lastTouchY = y
                                return true
                            }
                        }
                        
                        val newShape = shapes.lastOrNull { it.contains(x, y) }
                        selectedShape = newShape
                        if (newShape != null) {
                            shapes.remove(newShape)
                            shapes.add(newShape)
                            updateCache(excludeShape = newShape)
                        }
                        invalidate()
                        return true
                        
                    } else if (currentMode == DrawMode.TEXT) {
                        onTextInputRequested?.invoke(x, y)
                    } else {
                        currentShape = DrawShape(currentMode, Path(), createPaint(), x, y, x, y)
                        if (currentMode == DrawMode.BRUSH || currentMode == DrawMode.ERASER || currentMode == DrawMode.LASER) {
                            currentShape?.path?.moveTo(x, y)
                            lastDrawX = x
                            lastDrawY = y
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (currentMode == DrawMode.NONE) return true
                    
                    if (currentMode == DrawMode.MOVE && selectedShape != null) {
                        val shape = selectedShape!!
                        val cx = shape.getCenterX()
                        val cy = shape.getCenterY()

                        if (isScalingShape) {
                            val oldDist = hypot(lastTouchX - cx, lastTouchY - cy)
                            val newDist = hypot(x - cx, y - cy)
                            if (oldDist > 0) {
                                shape.scale *= (newDist / oldDist)
                                shape.scale = shape.scale.coerceIn(0.2f, 10.0f) 
                            }
                            lastTouchX = x
                            lastTouchY = y
                            invalidate()
                            return true
                        } else if (isRotatingShape) {
                            val currentAngle = Math.toDegrees(Math.atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                            val deltaAngle = currentAngle - initialRotationAngle
                            shape.rotation = initialShapeRotation + deltaAngle
                            invalidate()
                            return true
                        } else if (isDraggingShape) {
                            shape.moveBy(x - lastTouchX, y - lastTouchY)
                            lastTouchX = x
                            lastTouchY = y
                            invalidate()
                            return true
                        }
                    } else if (currentMode != DrawMode.TEXT && currentMode != DrawMode.MOVE) {
                        currentShape?.endX = x
                        currentShape?.endY = y
                        if (currentMode == DrawMode.BRUSH || currentMode == DrawMode.ERASER || currentMode == DrawMode.LASER) {
                            val dx = abs(x - lastDrawX)
                            val dy = abs(y - lastDrawY)
                            if (dx >= 3f || dy >= 3f) {
                                currentShape?.path?.quadTo(lastDrawX, lastDrawY, (x + lastDrawX) / 2, (y + lastDrawY) / 2)
                                lastDrawX = x
                                lastDrawY = y
                            }
                            
                            if (currentMode == DrawMode.LASER && isCometModeCache) {
                                currentShape?.path?.let { path ->
                                    val measure = PathMeasure(path, false)
                                    val length = measure.length
                                    val maxLength = 250f 
                                    if (length > maxLength) { 
                                        val tailPath = Path()
                                        measure.getSegment(length - maxLength, length, tailPath, true)
                                        path.set(tailPath) 
                                    }
                                }
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentMode == DrawMode.NONE) return true
                    
                    if (currentMode == DrawMode.MOVE) {
                        isDraggingShape = false
                        isScalingShape = false
                        isRotatingShape = false
                        updateCache(excludeShape = selectedShape)
                        invalidate()
                        
                    } else if (currentMode == DrawMode.LASER) {
                        
                        if (isCometModeCache) {
                            currentShape = null
                            updateCache()
                            invalidate()
                        } else {
                            currentShape?.let { laserShape ->
                                shapes.add(laserShape)
                                currentShape = null
                                updateCache()

                                val animator = ValueAnimator.ofInt(255, 0)
                                animator.duration = laserDurationCache * 1000L
                                animator.addUpdateListener { anim ->
                                    laserShape.paint.alpha = anim.animatedValue as Int
                                    updateCache()
                                    invalidate()
                                }
                                animator.addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        shapes.remove(laserShape)
                                        updateCache()
                                        invalidate()
                                    }
                                })
                                animator.start()
                            }
                        }
                        
                    } else if (currentMode != DrawMode.TEXT) {
                        currentShape?.let { shapes.add(it) }
                        currentShape = null
                        updateCache() 
                    }
                }
            }
            invalidate()
        } catch (e: Throwable) { e.printStackTrace() }
        return true
    }

    fun addTextShape(text: String, x: Float, y: Float) {
        shapes.add(DrawShape(DrawMode.TEXT, Path(), createPaint(), x, y, x, y, text))
        updateCache()
        invalidate()
    }
    
    fun clearCanvas() { 
        shapes.clear()
        updateCache()
        invalidate() 
    }
    
    fun undoLastAction() { 
        if (shapes.isNotEmpty()) { 
            shapes.removeAt(shapes.size - 1)
            updateCache()
            invalidate() 
        } 
    }
}
