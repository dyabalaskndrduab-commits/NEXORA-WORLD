package com.nexora.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * A real perspective 3D scene rendered with OpenGL ES 2.0.
 * The avatar is assembled from animated 3D meshes; it is not a 2D sprite.
 */
class Nexora3DView(context: Context) : FrameLayout(context) {
    private val world = WorldSurface(context)
    private val overlay = TouchHud(context)

    init {
        setBackgroundColor(Color.rgb(9, 14, 26))
        addView(world, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.onMove = { x, y -> world.renderer.setMove(x, y) }
        overlay.onLook = { amount -> world.renderer.rotateCamera(amount) }
    }

    override fun onResume() { super.onResume(); world.onResume() }
    override fun onPause() { world.onPause(); super.onPause() }

    private class WorldSurface(context: Context) : GLSurfaceView(context) {
        val renderer = WorldRenderer()
        init {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
            preserveEGLContextOnPause = true
        }
    }

    private class TouchHud(context: Context) : View(context) {
        var onMove: ((Float, Float) -> Unit)? = null
        var onLook: ((Float) -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var stickX = 0f
        private var stickY = 0f
        private var activePointer = -1
        private var lastLookX = 0f
        private var movePointer = false
        private var lookPointer = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            paint.color = 0xB9152032.toInt()
            canvas.drawRoundRect(22f, 18f, 270f, 88f, 18f, 18f, paint)
            paint.color = Color.WHITE
            paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            paint.textSize = 22f
            canvas.drawText("NEXORA", 40f, 48f, paint)
            paint.color = 0xFF62E8DC.toInt()
            paint.textSize = 11f
            canvas.drawText("3D SOCIAL CITY  •  PLAZA DISTRICT", 40f, 69f, paint)

            paint.color = 0xB9152032.toInt()
            canvas.drawRoundRect(w - 224f, 18f, w - 22f, 88f, 18f, 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 14f
            canvas.drawText("WORLD LEVEL 01", w - 204f, 46f, paint)
            paint.color = 0xFF62E8DC.toInt()
            paint.textSize = 12f
            canvas.drawText("Explore • Meet • Create", w - 204f, 68f, paint)

            val cx = 112f; val cy = h - 118f
            paint.color = 0x55313F55
            canvas.drawCircle(cx, cy, 72f, paint)
            paint.color = 0x995F718B.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            canvas.drawCircle(cx, cy, 72f, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xCC62E8DC.toInt()
            canvas.drawCircle(cx + stickX * 48f, cy + stickY * 48f, 27f, paint)
            paint.color = Color.WHITE
            paint.textSize = 11f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("MOVE", cx, cy + 96f, paint)
            paint.textAlign = Paint.Align.LEFT

            paint.color = 0xAA152032.toInt()
            canvas.drawRoundRect(w - 180f, h - 78f, w - 22f, h - 24f, 16f, 16f, paint)
            paint.color = Color.WHITE
            paint.textSize = 12f
            canvas.drawText("DRAG RIGHT SIDE TO LOOK AROUND", w - 166f, h - 45f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x; val y = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (x < width * .42f && y > height * .48f) {
                        movePointer = true
                        activePointer = event.getPointerId(event.actionIndex)
                        updateStick(x, y)
                    } else {
                        lookPointer = true
                        lastLookX = x
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (movePointer) {
                        val index = event.findPointerIndex(activePointer)
                        if (index >= 0) updateStick(event.getX(index), event.getY(index))
                    }
                    if (lookPointer) {
                        val dx = x - lastLookX
                        lastLookX = x
                        onLook?.invoke(dx / width.coerceAtLeast(1))
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    if (movePointer && event.getPointerId(event.actionIndex) == activePointer) {
                        movePointer = false; activePointer = -1
                        stickX = 0f; stickY = 0f
                        onMove?.invoke(0f, 0f)
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) lookPointer = false
                    invalidate()
                    return true
                }
            }
            return true
        }

        private fun updateStick(x: Float, y: Float) {
            val dx = x - 112f
            val dy = y - (height - 118f)
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val scale = min(1f, 58f / length)
            stickX = dx / length * scale
            stickY = dy / length * scale
            onMove?.invoke(stickX, stickY)
            invalidate()
        }
    }
}

private class WorldRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private var lightHandle = 0
    private lateinit var cube: FloatBuffer
    private lateinit var sphere: FloatBuffer
    private var cubeCount = 0
    private var sphereCount = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    @Volatile private var moveX = 0f
    @Volatile private var moveY = 0f
    @Volatile private var look = 0f
    private var px = 0f
    private var pz = 3f
    private var yaw = 0f
    private var walk = 0f
    private var last = System.nanoTime()

    fun setMove(x: Float, y: Float) { moveX = x; moveY = y }
    fun rotateCamera(amount: Float) { look = (look + amount * 3.0f).coerceIn(-1.35f, 1.35f) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.035f, .055f, .09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        val vertex = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying float vLight;
            void main() {
              vec3 n = normalize(mat3(uModel) * aNormal);
              vLight = 0.48 + 0.52 * max(dot(n, normalize(vec3(-0.35, 0.85, 0.42))), 0.0);
              gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            uniform vec4 uColor;
            varying float vLight;
            void main() { gl_FragColor = vec4(uColor.rgb * vLight, uColor.a); }
        """.trimIndent()
        program = linkProgram(vertex, fragment)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        cube = makeCube()
        cubeCount = 36
        sphere = makeSphere()
        sphereCount = 6 * 12 * 12
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 58f, width.toFloat() / height.coerceAtLeast(1), .1f, 140f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9).toFloat().coerceIn(0f, .05f)
        last = now
        val mx = moveX; val my = moveY
        if (abs(mx) + abs(my) > .08f) {
            val moveAngle = atan2(mx, -my) + look
            yaw = moveAngle
            px = (px + sin(moveAngle) * dt * 4.1f).coerceIn(-18f, 18f)
            pz = (pz - cos(moveAngle) * dt * 4.1f).coerceIn(-20f, 18f)
            walk += dt * 10f
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val cameraAngle = yaw + look
        val cameraX = px - sin(cameraAngle) * 7.2f
        val cameraZ = pz + cos(cameraAngle) * 7.2f
        val cameraY = 4.0f
        Matrix.setLookAtM(view, 0, cameraX, cameraY, cameraZ, px, 1.05f, pz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        drawCity()
        drawAvatar()
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private fun drawCity() {
        box(0f, -.22f, 0f, 48f, .4f, 48f, rgb(.20f,.29f,.31f))
        box(0f, -.005f, 0f, 12f, .04f, 48f, rgb(.18f,.21f,.25f))
        box(-7f, .01f, 0f, 1.2f, .06f, 48f, rgb(.63f,.62f,.55f))
        box(7f, .01f, 0f, 1.2f, .06f, 48f, rgb(.63f,.62f,.55f))
        for (i in -10..10 step 2) {
            box(i.toFloat(), .025f, 0f, .07f, .025f, .9f, rgb(.94f,.79f,.43f))
        }
        box(0f, .035f, 0f, 17f, .08f, 15f, rgb(.62f,.57f,.48f))
        // Buildings around the plaza form a navigable 3D city block.
        building(-11f, -7f, 5f, 8f, 5f, rgb(.24f,.37f,.52f))
        building(10f, -8f, 5f, 11f, 5f, rgb(.42f,.29f,.43f))
        building(-11f, 8f, 5f, 9f, 5f, rgb(.34f,.43f,.48f))
        building(11f, 8f, 5f, 7f, 5f, rgb(.49f,.34f,.27f))
        shop(-5.5f, -9.8f, rgb(.67f,.36f,.24f), "cafe")
        shop(5.5f, -9.8f, rgb(.25f,.34f,.62f), "arcade")
        shop(-5.5f, 10f, rgb(.52f,.27f,.49f), "style")
        // Garden islands, trees, benches and lamps.
        for (x in listOf(-8f, 8f)) for (z in listOf(-3f, 4f)) {
            box(x, .10f, z, 2.8f, .22f, 2.8f, rgb(.20f,.39f,.28f))
            tree(x, z)
        }
        tree(-2.7f, 5.7f); tree(2.8f, -5.8f)
        box(-3.4f,.45f,2.5f,1.5f,.12f,.42f,rgb(.40f,.23f,.13f))
        box(3.4f,.45f,-2.5f,1.5f,.12f,.42f,rgb(.40f,.23f,.13f))
        // Fountain basin and animated water.
        spherePart(0f,.32f,-.1f,1.45f,.25f,1.45f,rgb(.48f,.57f,.61f))
        spherePart(0f,.48f,-.1f,1.16f,.13f,1.16f,rgb(.15f,.55f,.70f))
        box(0f,.95f,-.1f,.20f,.8f,.20f,rgb(.76f,.82f,.83f))
        // Distant skyline.
        for (i in 0..8) {
            val x = -22f + i * 5.5f
            building(x, -22f, 3.4f, 8f + (i % 3) * 2f, 3.4f, rgb(.20f,.29f,.40f))
        }
    }

    private fun building(x: Float, z: Float, width: Float, height: Float, depth: Float, color: FloatArray) {
        box(x, height/2f, z, width, height, depth, color)
        box(x, height + .12f, z, width + .35f, .24f, depth + .35f, rgb(.13f,.18f,.25f))
        val rows = max(2, (height / 1.8f).toInt())
        for (r in 0 until rows) for (c in -1..1) {
            box(x + c * width * .27f, 1.05f + r * 1.65f, z - depth/2f - .025f,
                width*.13f, .65f, .045f, rgb(.77f,.68f,.43f))
        }
        box(x, .65f, z - depth/2f - .04f, width*.20f, 1.3f, .08f, rgb(.12f,.17f,.23f))
    }

    private fun shop(x: Float, z: Float, color: FloatArray, label: String) {
        box(x,.95f,z,4.4f,1.9f,2.7f,color)
        box(x,1.95f,z,4.8f,.18f,3.0f,rgb(.88f,.78f,.58f))
        box(x,.95f,z-1.38f,3.6f,1.05f,.08f,rgb(.12f,.22f,.29f))
        box(x-1f,1.02f,z-1.44f,1.1f,.72f,.035f,rgb(.25f,.68f,.73f))
        box(x+1f,1.02f,z-1.44f,1.1f,.72f,.035f,rgb(.25f,.68f,.73f))
    }

    private fun tree(x: Float, z: Float) {
        box(x,.75f,z,.30f,1.5f,.30f,rgb(.34f,.20f,.12f))
        spherePart(x,2.0f,z,1.05f,1.1f,1.05f,rgb(.13f,.42f,.28f))
        spherePart(x-.48f,1.78f,z-.05f,.65f,.72f,.65f,rgb(.18f,.50f,.31f))
    }

    private fun drawAvatar() {
        val phase = walk
        val swing = if (abs(moveX)+abs(moveY) > .08f) sin(phase) * .48f else 0f
        fun part(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray, rx: Float = 0f) {
            Matrix.setIdentityM(model,0)
            Matrix.translateM(model,0,px,0f,pz)
            Matrix.rotateM(model,0,Math.toDegrees(yaw.toDouble()).toFloat(),0f,1f,0f)
            Matrix.translateM(model,0,x,y,z)
            if (rx != 0f) Matrix.rotateM(model,0,rx,1f,0f,0f)
            Matrix.scaleM(model,0,sx,sy,sz)
            drawMesh(cube,cubeCount,color)
        }
        // White sneakers, black trousers and swinging legs.
        part(-.23f,.16f,0f,.34f,.24f,.52f,rgb(.92f,.94f,.96f),swing*32f)
        part(.23f,.16f,0f,.34f,.24f,.52f,rgb(.92f,.94f,.96f),-swing*32f)
        part(-.23f,.57f,0f,.29f,.72f,.34f,rgb(.035f,.045f,.065f),swing*32f)
        part(.23f,.57f,0f,.29f,.72f,.34f,rgb(.035f,.045f,.065f),-swing*32f)
        // Hoodie torso, hood and arms.
        part(0f,1.42f,0f,.92f,1.02f,.52f,rgb(.025f,.035f,.055f))
        part(0f,1.92f,.24f,.58f,.55f,.20f,rgb(.045f,.055f,.075f))
        part(-.59f,1.43f,0f,.27f,.88f,.30f,rgb(.025f,.035f,.055f),-swing*38f)
        part(.59f,1.43f,0f,.27f,.88f,.30f,rgb(.025f,.035f,.055f),swing*38f)
        // Neck, face and black styled hair: clearly visible when camera is orbited.
        part(0f,2.04f,-.02f,.25f,.24f,.25f,rgb(.63f,.39f,.28f))
        part(0f,2.42f,-.02f,.58f,.70f,.48f,rgb(.70f,.48f,.36f))
        part(0f,2.72f,.015f,.62f,.24f,.52f,rgb(.018f,.022f,.032f))
        part(-.25f,2.52f,.01f,.12f,.42f,.48f,rgb(.018f,.022f,.032f))
        part(.25f,2.52f,.01f,.12f,.42f,.48f,rgb(.018f,.022f,.032f))
        // Eyes and small NEXORA chest emblem.
        part(-.13f,2.46f,-.265f,.055f,.055f,.025f,rgb(.035f,.045f,.055f))
        part(.13f,2.46f,-.265f,.055f,.055f,.025f,rgb(.035f,.045f,.055f))
        part(0f,1.55f,-.268f,.22f,.10f,.025f,rgb(.20f,.83f,.80f))
    }

    private fun box(x: Float,y: Float,z: Float,sx: Float,sy: Float,sz: Float,color: FloatArray) {
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,x,y,z)
        Matrix.scaleM(model,0,sx,sy,sz)
        drawMesh(cube,cubeCount,color)
    }

    private fun spherePart(x: Float,y: Float,z: Float,sx: Float,sy: Float,sz: Float,color: FloatArray) {
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,x,y,z)
        Matrix.scaleM(model,0,sx,sy,sz)
        drawMesh(sphere,sphereCount,color)
    }

    private fun drawMesh(buffer: FloatBuffer, count: Int, color: FloatArray) {
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
        GLES20.glUniform4fv(colorHandle,1,color,0)
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,24,buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,24,buffer)
        buffer.position(0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count)
    }

    private fun makeCube(): FloatBuffer {
        val data = ArrayList<Float>()
        val faces = arrayOf(
            floatArrayOf(0f,0f,1f), floatArrayOf(0f,0f,-1f),
            floatArrayOf(1f,0f,0f), floatArrayOf(-1f,0f,0f),
            floatArrayOf(0f,1f,0f), floatArrayOf(0f,-1f,0f)
        )
        val corners = arrayOf(
            floatArrayOf(-.5f,-.5f,.5f),floatArrayOf(.5f,-.5f,.5f),floatArrayOf(.5f,.5f,.5f),floatArrayOf(-.5f,.5f,.5f),
            floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(-.5f,.5f,-.5f),floatArrayOf(.5f,.5f,-.5f),
            floatArrayOf(.5f,-.5f,.5f),floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(.5f,.5f,-.5f),floatArrayOf(.5f,.5f,.5f),
            floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(-.5f,-.5f,.5f),floatArrayOf(-.5f,.5f,.5f),floatArrayOf(-.5f,.5f,-.5f),
            floatArrayOf(-.5f,.5f,.5f),floatArrayOf(.5f,.5f,.5f),floatArrayOf(.5f,.5f,-.5f),floatArrayOf(-.5f,.5f,-.5f),
            floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(.5f,-.5f,.5f),floatArrayOf(-.5f,-.5f,.5f)
        )
        val idx = intArrayOf(0,1,2,0,2,3)
        for (f in 0..5) for (i in idx) {
            val v=corners[f*4+i]; val n=faces[f]
            data.add(v[0]);data.add(v[1]);data.add(v[2]);data.add(n[0]);data.add(n[1]);data.add(n[2])
        }
        return floatBuffer(data.toFloatArray())
    }

    private fun makeSphere(): FloatBuffer {
        val data=ArrayList<Float>()
        val lat=12; val lon=12
        for (i in 0 until lat) {
            val a1=PI*i/lat; val a2=PI*(i+1)/lat
            for (j in 0 until lon) {
                val b1=2*PI*j/lon; val b2=2*PI*(j+1)/lon
                val pts=arrayOf(point(a1,b1),point(a2,b1),point(a2,b2),point(a1,b1),point(a2,b2),point(a1,b2))
                for (v in pts) { data.add(v[0]);data.add(v[1]);data.add(v[2]);data.add(v[0]);data.add(v[1]);data.add(v[2]) }
            }
        }
        return floatBuffer(data.toFloatArray())
    }

    private fun point(a: Double,b: Double)=floatArrayOf((sin(a)*cos(b)).toFloat(),cos(a).toFloat(),(sin(a)*sin(b)).toFloat())
    private fun floatBuffer(values: FloatArray)=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    private fun rgb(r: Float,g: Float,b: Float)=floatArrayOf(r,g,b,1f)

    private fun linkProgram(vs: String, fs: String): Int {
        fun shader(type:Int, source:String):Int {
            val id=GLES20.glCreateShader(type)
            GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
            val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
            if(status[0]==0) throw RuntimeException("Shader compile failed: "+GLES20.glGetShaderInfoLog(id))
            return id
        }
        val v=shader(GLES20.GL_VERTEX_SHADER,vs);val f=shader(GLES20.GL_FRAGMENT_SHADER,fs)
        val id=GLES20.glCreateProgram()
        GLES20.glAttachShader(id,v);GLES20.glAttachShader(id,f);GLES20.glLinkProgram(id)
        val status=IntArray(1);GLES20.glGetProgramiv(id,GLES20.GL_LINK_STATUS,status,0)
        if(status[0]==0) throw RuntimeException("Shader link failed: "+GLES20.glGetProgramInfoLog(id))
        return id
    }
}
