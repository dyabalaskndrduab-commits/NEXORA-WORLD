package com.nexora.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.util.Base64
import android.opengl.GLUtils
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

    fun resumeWorld() { world.onResume() }
    fun pauseWorld() { world.onPause() }

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
        private var movePointerId = -1
        private var lookPointerId = -1
        private var lastLookX = 0f
        private var joyCenterX = 112f
        private var joyCenterY = 0f
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

            if (joyCenterY == 0f) { joyCenterX = 112f; joyCenterY = h - 118f }
            val cx = joyCenterX; val cy = joyCenterY
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
                    val downX = event.getX(event.actionIndex)
                    val downY = event.getY(event.actionIndex)
                    if (!movePointer && downX < width * .48f && downY > height * .38f) {
                        movePointer = true
                        movePointerId = event.getPointerId(event.actionIndex)
                        joyCenterX = downX
                        joyCenterY = downY
                        updateStick(joyCenterX, joyCenterY)
                    } else if (!lookPointer) {
                        lookPointer = true
                        lookPointerId = event.getPointerId(event.actionIndex)
                        lastLookX = downX
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (movePointer) {
                        val index = event.findPointerIndex(movePointerId)
                        if (index >= 0) updateStick(event.getX(index), event.getY(index))
                    }
                    if (lookPointer) {
                        val index = event.findPointerIndex(lookPointerId)
                        if (index >= 0) {
                            val lookX = event.getX(index)
                            val dx = lookX - lastLookX
                            lastLookX = lookX
                            onLook?.invoke(dx / width.coerceAtLeast(1))
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    val liftedId = event.getPointerId(event.actionIndex)
                    if (movePointer && liftedId == movePointerId) {
                        movePointer = false; movePointerId = -1
                        stickX = 0f; stickY = 0f
                        onMove?.invoke(0f, 0f)
                    }
                    if (lookPointer && liftedId == lookPointerId) {
                        lookPointer = false; lookPointerId = -1
                    }
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        movePointer = false; lookPointer = false
                        movePointerId = -1; lookPointerId = -1
                        stickX = 0f; stickY = 0f
                        onMove?.invoke(0f, 0f)
                    }
                    invalidate()
                    return true
                }
            }
            return true
        }

        private fun updateStick(x: Float, y: Float) {
            val dx = x - joyCenterX
            val dy = y - joyCenterY
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val scale = min(1f, 58f / length)
            stickX = dx / 58f * scale
            stickY = dy / 58f * scale
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
    private var avatarProgram = 0
    private var avatarPositionHandle = 0
    private var avatarUvHandle = 0
    private var avatarMvpHandle = 0
    private var avatarSamplerHandle = 0
    private var avatarTexture = 0
    private lateinit var avatarQuad: FloatBuffer
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
    @Volatile private var cameraYaw = 0f
    private var px = 0f
    private var pz = 3f
    private var velocityX = 0f
    private var velocityZ = 0f
    private var yaw = 0f
    private var walk = 0f
    private var cameraX = 0f
    private var cameraY = 4f
    private var cameraZ = 10f
    private var last = System.nanoTime()

    fun setMove(x: Float, y: Float) { moveX = x; moveY = y }
    fun rotateCamera(amount: Float) { cameraYaw += amount * 4.4f }

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
              vec3 n = normalize(aNormal);
              vLight = 0.62 + 0.38 * max(dot(n, normalize(vec3(-0.35, 0.85, 0.42))), 0.0);
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
        sphereCount = 6 * 20 * 24

        // The approved NEXORA character artwork is now rendered as a textured plane
        // inside the perspective 3D world, rather than as a HUD/screen-space image.
        val avatarVertex = """
            uniform mat4 uMVP;
            attribute vec3 aPosition;
            attribute vec2 aUV;
            varying vec2 vUV;
            void main() {
              vUV = aUV;
              gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """.trimIndent()
        val avatarFragment = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vUV;
            void main() {
              vec4 texel = texture2D(uTexture, vUV);
              if (texel.a < 0.08) discard;
              gl_FragColor = texel;
            }
        """.trimIndent()
        avatarProgram = linkProgram(avatarVertex, avatarFragment)
        avatarPositionHandle = GLES20.glGetAttribLocation(avatarProgram, "aPosition")
        avatarUvHandle = GLES20.glGetAttribLocation(avatarProgram, "aUV")
        avatarMvpHandle = GLES20.glGetUniformLocation(avatarProgram, "uMVP")
        avatarSamplerHandle = GLES20.glGetUniformLocation(avatarProgram, "uTexture")
        avatarQuad = floatBuffer(floatArrayOf(
            -.68f, 0f, 0f, 0f, 1f,
             .68f, 0f, 0f, 1f, 1f,
             .68f, 2.8f, 0f, 1f, 0f,
            -.68f, 0f, 0f, 0f, 1f,
             .68f, 2.8f, 0f, 1f, 0f,
            -.68f, 2.8f, 0f, 0f, 0f
        ))
        val imageBytes = Base64.decode(NexoraAvatarAsset.WEBP, Base64.DEFAULT)
        val avatarBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalStateException("NEXORA avatar texture could not be decoded")
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        avatarTexture = textureIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, avatarTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, avatarBitmap, 0)
        avatarBitmap.recycle()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 58f, width.toFloat() / height.coerceAtLeast(1), .1f, 140f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9).toFloat().coerceIn(0f, .05f)
        last = now
        val mx = moveX.coerceIn(-1f, 1f)
        val my = moveY.coerceIn(-1f, 1f)
        val rawLength = sqrt(mx * mx + my * my)
        val inputLength = rawLength.coerceAtMost(1f)
        val inputX = if (rawLength > 1f) mx / rawLength else mx
        val inputY = if (rawLength > 1f) my / rawLength else my

        // Camera-relative movement: joystick up follows the camera's forward vector (-Z at yaw 0).
        // Right is (cos(yaw), 0, sin(yaw)); forward is (sin(yaw), 0, -cos(yaw)).
        val desiredX = (cos(cameraYaw) * inputX - sin(cameraYaw) * inputY) * RemoteGameConfig.moveSpeed * inputLength
        val desiredZ = (sin(cameraYaw) * inputX + cos(cameraYaw) * inputY) * RemoteGameConfig.moveSpeed * inputLength
        val blend = (dt * if (inputLength > .04f) RemoteGameConfig.acceleration else RemoteGameConfig.deceleration).coerceAtMost(1f)
        velocityX += (desiredX - velocityX) * blend
        velocityZ += (desiredZ - velocityZ) * blend
        px = (px + velocityX * dt).coerceIn(-18f, 18f)
        pz = (pz + velocityZ * dt).coerceIn(-20f, 18f)
        val speed = sqrt(velocityX*velocityX + velocityZ*velocityZ)
        if (speed > .045f) {
            val targetYaw = atan2(-velocityX, -velocityZ)
            val delta = ((targetYaw - yaw + PI.toFloat()) % (2f*PI.toFloat()) + 2f*PI.toFloat()) % (2f*PI.toFloat()) - PI.toFloat()
            yaw += delta * (dt * RemoteGameConfig.turnResponsiveness).coerceAtMost(1f)
            walk += dt * (RemoteGameConfig.walkCycleBase + speed * RemoteGameConfig.walkCycleSpeed)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val desiredCameraX = px - sin(cameraYaw) * RemoteGameConfig.cameraDistance
        val desiredCameraZ = pz + cos(cameraYaw) * RemoteGameConfig.cameraDistance
        val cameraBlend = (dt * RemoteGameConfig.cameraFollow).coerceAtMost(1f)
        cameraX += (desiredCameraX - cameraX) * cameraBlend
        cameraZ += (desiredCameraZ - cameraZ) * cameraBlend
        cameraY += (RemoteGameConfig.cameraHeight - cameraY) * cameraBlend
        Matrix.setLookAtM(view, 0, cameraX, cameraY, cameraZ, px, 1.15f, pz, 0f, 1f, 0f)
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
        drawApprovedAvatarTexture()
        return
        val speed = sqrt(velocityX * velocityX + velocityZ * velocityZ)
        val moving = speed > .045f
        val gaitPower = (speed / RemoteGameConfig.moveSpeed.coerceAtLeast(0.5f)).coerceIn(0f, 1f)
        val stride = if (moving) sin(walk) * gaitPower else 0f
        val swingDegrees = Math.toDegrees((stride * RemoteGameConfig.legSwingRadians).toDouble()).toFloat()
        val leftLift = if (moving) max(0f, sin(walk)) * RemoteGameConfig.footLift * gaitPower else 0f
        val rightLift = if (moving) max(0f, -sin(walk)) * RemoteGameConfig.footLift * gaitPower else 0f
        val leftKnee = if (moving) max(0f, -sin(walk)) * RemoteGameConfig.kneeBendDegrees * gaitPower else 0f
        val rightKnee = if (moving) max(0f, sin(walk)) * RemoteGameConfig.kneeBendDegrees * gaitPower else 0f
        val bob = if (moving) abs(sin(walk * 2f)) * RemoteGameConfig.bodyBounce * gaitPower else 0f

        fun part(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float,
                 color: FloatArray, rx: Float = 0f, rz: Float = 0f) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, px, bob, pz)
            Matrix.rotateM(model, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.scaleM(model, 0, RemoteGameConfig.avatarScale, RemoteGameConfig.avatarScale, RemoteGameConfig.avatarScale)
            Matrix.translateM(model, 0, x, y, z)
            if (rx != 0f) Matrix.rotateM(model, 0, rx, 1f, 0f, 0f)
            if (rz != 0f) Matrix.rotateM(model, 0, rz, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, sx, sy, sz)
            drawMesh(sphere, sphereCount, color)
        }

        // Rotate each leg and arm around its joint, not around its own centre.
        fun jointLimb(pivotX: Float, pivotY: Float, pivotZ: Float, offsetY: Float,
                      sx: Float, sy: Float, sz: Float, color: FloatArray, angle: Float,
                      offsetZ: Float = 0f) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, px, bob, pz)
            Matrix.rotateM(model, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.scaleM(model, 0, RemoteGameConfig.avatarScale, RemoteGameConfig.avatarScale, RemoteGameConfig.avatarScale)
            Matrix.translateM(model, 0, pivotX, pivotY, pivotZ)
            Matrix.rotateM(model, 0, angle, 1f, 0f, 0f)
            Matrix.translateM(model, 0, 0f, offsetY, offsetZ)
            Matrix.scaleM(model, 0, sx, sy, sz)
            drawMesh(sphere, sphereCount, color)
        }

        val darkPants = rgb(.035f, .045f, .065f)
        val shoeWhite = rgb(.94f, .95f, .97f)
        val leftSwing = swingDegrees
        val rightSwing = -swingDegrees

        // Legs: upper and lower segments swing in opposition with a slight knee lag.
        jointLimb(-.22f, 1.02f, 0f, -.22f, .17f, .43f, .18f, darkPants, leftSwing)
        jointLimb(.22f, 1.02f, 0f, -.22f, .17f, .43f, .18f, darkPants, rightSwing)
        jointLimb(-.22f, .59f, 0f, -.18f, .145f, .34f, .15f, darkPants, -leftSwing * .20f + leftKnee)
        jointLimb(.22f, .59f, 0f, -.18f, .145f, .34f, .15f, darkPants, -rightSwing * .20f + rightKnee)
        jointLimb(-.22f, .22f, -.02f, -.055f + leftLift, .24f, .13f, .39f, shoeWhite, -leftSwing * .28f, -.12f)
        jointLimb(.22f, .22f, -.02f, -.055f + rightLift, .24f, .13f, .39f, shoeWhite, -rightSwing * .28f, -.12f)

        // Black NEXORA hoodie, hood and torso.
        part(0f, 1.43f, 0f, .43f, .58f, .29f, rgb(.025f, .033f, .052f))
        part(0f, 1.82f, .12f, .30f, .30f, .20f, rgb(.045f, .052f, .072f))
        part(0f, 1.06f, -.005f, .39f, .15f, .27f, rgb(.018f, .026f, .042f))

        // Arms pivot at the shoulders and counter-swing naturally against the legs.
        jointLimb(-.40f, 1.76f, 0f, -.22f, .19f, .43f, .19f, rgb(.028f, .036f, .055f), -leftSwing * .82f)
        jointLimb(.40f, 1.76f, 0f, -.22f, .19f, .43f, .19f, rgb(.028f, .036f, .055f), -rightSwing * .82f)
        jointLimb(-.40f, 1.34f, 0f, -.10f, .14f, .13f, .15f, rgb(.018f, .025f, .041f), -leftSwing * .25f)
        jointLimb(.40f, 1.34f, 0f, -.10f, .14f, .13f, .15f, rgb(.018f, .025f, .041f), -rightSwing * .25f)

        // Neck, face and layered black hair.
        part(0f, 2.02f, -.015f, .14f, .20f, .14f, rgb(.61f, .38f, .29f))
        part(0f, 2.37f, -.015f, .29f, .37f, .25f, rgb(.72f, .49f, .37f))
        part(0f, 2.61f, .005f, .30f, .20f, .27f, rgb(.018f, .022f, .034f))
        part(-.20f, 2.46f, .005f, .105f, .25f, .22f, rgb(.018f, .022f, .034f))
        part(.20f, 2.46f, .005f, .105f, .25f, .22f, rgb(.018f, .022f, .034f))
        part(-.13f, 2.38f, -.238f, .045f, .035f, .018f, rgb(.035f, .045f, .055f))
        part(.13f, 2.38f, -.238f, .045f, .035f, .018f, rgb(.035f, .045f, .055f))
        part(0f, 2.28f, -.255f, .07f, .025f, .018f, rgb(.42f, .22f, .19f))

        // Signature chibi face: large glossy eyes, irises, highlights, brows and smile.
        val skin = rgb(.76f, .52f, .40f)
        val hair = rgb(.018f, .022f, .034f)
        val hairLight = rgb(.055f, .060f, .082f)
        part(-.285f, 2.38f, -.015f, .065f, .12f, .09f, skin)
        part(.285f, 2.38f, -.015f, .065f, .12f, .09f, skin)
        part(-.115f, 2.405f, -.244f, .085f, .112f, .035f, rgb(.98f, .98f, .96f))
        part(.115f, 2.405f, -.244f, .085f, .112f, .035f, rgb(.98f, .98f, .96f))
        part(-.108f, 2.402f, -.275f, .054f, .079f, .024f, rgb(.30f, .17f, .11f))
        part(.122f, 2.402f, -.275f, .054f, .079f, .024f, rgb(.30f, .17f, .11f))
        part(-.108f, 2.401f, -.294f, .030f, .052f, .014f, rgb(.012f, .014f, .020f))
        part(.122f, 2.401f, -.294f, .030f, .052f, .014f, rgb(.012f, .014f, .020f))
        part(-.127f, 2.438f, -.308f, .020f, .024f, .010f, rgb(1f, 1f, 1f))
        part(.103f, 2.438f, -.308f, .020f, .024f, .010f, rgb(1f, 1f, 1f))
        part(-.115f, 2.535f, -.238f, .105f, .035f, .045f, hair, 0f, -9f)
        part(.115f, 2.535f, -.238f, .105f, .035f, .045f, hair, 0f, 9f)
        part(0f, 2.245f, -.258f, .075f, .018f, .014f, rgb(.55f, .25f, .22f))
        part(-.19f, 2.30f, -.222f, .055f, .035f, .018f, rgb(.86f, .43f, .38f))
        part(.19f, 2.30f, -.222f, .055f, .035f, .018f, rgb(.86f, .43f, .38f))

        // Sculpted layered fringe and swept black hair silhouette.
        part(-.19f, 2.67f, -.055f, .17f, .13f, .18f, hairLight, 0f, -22f)
        part(-.07f, 2.73f, -.035f, .19f, .14f, .18f, hair, 0f, -12f)
        part(.09f, 2.72f, -.035f, .20f, .14f, .18f, hairLight, 0f, 12f)
        part(.22f, 2.64f, -.015f, .13f, .12f, .16f, hair, 0f, 25f)
        part(-.20f, 2.57f, -.19f, .12f, .13f, .10f, hairLight, 0f, -28f)
        part(-.08f, 2.62f, -.205f, .13f, .12f, .09f, hair, 0f, -18f)
        part(.06f, 2.64f, -.20f, .14f, .13f, .09f, hairLight, 0f, 12f)
        part(.19f, 2.58f, -.17f, .12f, .12f, .09f, hair, 0f, 25f)
        part(-.04f, 2.57f, -.255f, .105f, .15f, .055f, hair, 0f, -14f)
        part(.09f, 2.55f, -.252f, .10f, .14f, .055f, hairLight, 0f, 12f)

        // Hoodie details: raised crown emblem, drawstrings and front pouch.
        part(0f, 1.53f, -.292f, .18f, .12f, .018f, rgb(.92f, .93f, .97f))
        part(0f, 1.46f, -.309f, .16f, .025f, .012f, rgb(.92f, .93f, .97f))
        part(-.12f, 1.57f, -.307f, .035f, .07f, .014f, rgb(.92f, .93f, .97f), 0f, -24f)
        part(-.04f, 1.62f, -.307f, .035f, .09f, .014f, rgb(.92f, .93f, .97f), 0f, -8f)
        part(.04f, 1.62f, -.307f, .035f, .09f, .014f, rgb(.92f, .93f, .97f), 0f, 8f)
        part(.12f, 1.57f, -.307f, .035f, .07f, .014f, rgb(.92f, .93f, .97f), 0f, 24f)
        part(-.075f, 1.75f, -.285f, .014f, .18f, .012f, rgb(.62f, .63f, .67f))
        part(.075f, 1.75f, -.285f, .014f, .18f, .012f, rgb(.62f, .63f, .67f))
        part(0f, 1.22f, -.267f, .28f, .12f, .025f, rgb(.018f, .025f, .040f))
        part(-.37f, 1.15f, -.025f, .13f, .14f, .12f, skin)
        part(.37f, 1.15f, -.025f, .13f, .14f, .12f, skin)

        // Cargo jogger pockets and signature white side piping.
        part(-.225f, .70f, -.155f, .12f, .20f, .035f, rgb(.055f, .065f, .085f), 0f, -5f)
        part(.225f, .70f, -.155f, .12f, .20f, .035f, rgb(.055f, .065f, .085f), 0f, 5f)
        part(-.39f, .72f, -.105f, .025f, .28f, .022f, rgb(.88f, .90f, .94f))
        part(.39f, .72f, -.105f, .025f, .28f, .022f, rgb(.88f, .90f, .94f))
        part(-.22f, .89f, -.19f, .10f, .025f, .015f, rgb(.11f, .12f, .15f))
        part(.22f, .89f, -.19f, .10f, .025f, .015f, rgb(.11f, .12f, .15f))

        // High-top sneakers: white toe caps, tongues, laces and black crown marks.
        part(-.22f, .16f, -.30f, .18f, .10f, .10f, rgb(.99f, .99f, 1f))
        part(.22f, .16f, -.30f, .18f, .10f, .10f, rgb(.99f, .99f, 1f))
        part(-.22f, .28f, -.12f, .12f, .13f, .07f, rgb(.96f, .97f, .99f))
        part(.22f, .28f, -.12f, .12f, .13f, .07f, rgb(.96f, .97f, .99f))
        for (i in 0..2) {
            val laceY = .22f + i * .035f
            part(-.22f, laceY, -.205f, .13f, .012f, .012f, rgb(.98f, .98f, 1f), 0f, 8f)
            part(.22f, laceY, -.205f, .13f, .012f, .012f, rgb(.98f, .98f, 1f), 0f, -8f)
        }
    }

    private fun drawApprovedAvatarTexture() {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, px, 0f, pz)
        Matrix.rotateM(model, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(model, 0, RemoteGameConfig.avatarScale, RemoteGameConfig.avatarScale, RemoteGameConfig.avatarScale)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)

        GLES20.glUseProgram(avatarProgram)
        GLES20.glUniformMatrix4fv(avatarMvpHandle, 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, avatarTexture)
        GLES20.glUniform1i(avatarSamplerHandle, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnableVertexAttribArray(avatarPositionHandle)
        GLES20.glEnableVertexAttribArray(avatarUvHandle)
        avatarQuad.position(0)
        GLES20.glVertexAttribPointer(avatarPositionHandle, 3, GLES20.GL_FLOAT, false, 20, avatarQuad)
        avatarQuad.position(3)
        GLES20.glVertexAttribPointer(avatarUvHandle, 2, GLES20.GL_FLOAT, false, 20, avatarQuad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(avatarPositionHandle)
        GLES20.glDisableVertexAttribArray(avatarUvHandle)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
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
        val lat=20; val lon=24
        for (i in 0 until lat) {
            val a1=PI*i/lat; val a2=PI*(i+1)/lat
            for (j in 0 until lon) {
                val b1=2*PI*j/lon; val b2=2*PI*(j+1)/lon
                val pts=arrayOf(point(a1,b1),point(a1,b2),point(a2,b2),point(a1,b1),point(a2,b2),point(a2,b1))
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
