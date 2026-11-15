package com.nexora.world

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs

/**
 * Minimal GLB 2.0 renderer for the NEXORA avatar asset.
 * Supports embedded BIN data, indexed triangle primitives and PBR base colors.
 */
internal class GlbAvatarRenderer(private val context: Context) {
    private data class Part(val name: String, val vertices: FloatBuffer, val count: Int, val color: FloatArray)
    private val parts = ArrayList<Part>()
    private var program = 0
    private var posHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var loaded = false

    fun load() {
        if (loaded) return
        val bytes = context.assets.open("NEXORA_Character_v1.glb").use { it.readBytes() }
        val file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(file.int == 0x46546C67) { "Invalid GLB magic" }
        require(file.int == 2) { "Only GLB 2.0 is supported" }
        file.int
        val jsonLength = file.int
        file.int
        val jsonBytes = ByteArray(jsonLength)
        file.get(jsonBytes)
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8).trim())
        val binLength = file.int
        file.int
        val bin = ByteArray(binLength)
        file.get(bin)
        val data = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        val views = root.getJSONArray("bufferViews")
        val accessors = root.getJSONArray("accessors")
        val meshes = root.getJSONArray("meshes")
        val materials = root.optJSONArray("materials")
        val nodes = root.getJSONArray("nodes")

        fun accessorFloats(index: Int): FloatArray {
            val a = accessors.getJSONObject(index)
            val view = views.getJSONObject(a.getInt("bufferView"))
            val count = a.getInt("count")
            val components = when (a.getString("type")) {
                "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1
            }
            val offset = view.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val result = FloatArray(count * components)
            val stride = view.optInt("byteStride", components * 4)
            for (i in 0 until count) for (c in 0 until components) {
                result[i * components + c] = data.getFloat(offset + i * stride + c * 4)
            }
            return result
        }
        fun accessorIndices(index: Int): IntArray {
            val a = accessors.getJSONObject(index)
            val view = views.getJSONObject(a.getInt("bufferView"))
            val count = a.getInt("count")
            val offset = view.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val result = IntArray(count)
            when (a.getInt("componentType")) {
                5121 -> for (i in 0 until count) result[i] = data.get(offset + i).toInt() and 255
                5123 -> for (i in 0 until count) result[i] = data.getShort(offset + i * 2).toInt() and 65535
                5125 -> for (i in 0 until count) result[i] = data.getInt(offset + i * 4)
                else -> error("Unsupported GLB index type")
            }
            return result
        }
        fun vec3(node: JSONObject, key: String, default: FloatArray): FloatArray {
            val a = node.optJSONArray(key) ?: return default
            return FloatArray(3) { a.getDouble(it).toFloat() }
        }

        for (n in 0 until nodes.length()) {
            val node = nodes.getJSONObject(n)
            if (!node.has("mesh")) continue
            val mesh = meshes.getJSONObject(node.getInt("mesh"))
            val translation = vec3(node, "translation", floatArrayOf(0f, 0f, 0f))
            val scale = vec3(node, "scale", floatArrayOf(1f, 1f, 1f))
            val primitives = mesh.getJSONArray("primitives")
            for (p in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(p)
                if (primitive.optInt("mode", 4) != 4) continue
                val attributes = primitive.getJSONObject("attributes")
                val positions = accessorFloats(attributes.getInt("POSITION"))
                val indices = if (primitive.has("indices")) accessorIndices(primitive.getInt("indices"))
                              else IntArray(positions.size / 3) { it }
                val materialIndex = primitive.optInt("material", -1)
                val factor = if (materials != null && materialIndex >= 0 && materialIndex < materials.length()) {
                    val m = materials.getJSONObject(materialIndex)
                    val pbr = m.optJSONObject("pbrMetallicRoughness")
                    val c = pbr?.optJSONArray("baseColorFactor")
                    if (c != null) FloatArray(4) { c.getDouble(it).toFloat() } else floatArrayOf(.08f,.08f,.1f,1f)
                } else floatArrayOf(.08f,.08f,.1f,1f)

                val expanded = ArrayList<Float>(indices.size * 6)
                var i = 0
                while (i + 2 < indices.size) {
                    val ia = indices[i] * 3; val ib = indices[i + 1] * 3; val ic = indices[i + 2] * 3
                    val ax=positions[ia]*scale[0]+translation[0]; val ay=positions[ia+1]*scale[1]+translation[1]; val az=positions[ia+2]*scale[2]+translation[2]
                    val bx=positions[ib]*scale[0]+translation[0]; val by=positions[ib+1]*scale[1]+translation[1]; val bz=positions[ib+2]*scale[2]+translation[2]
                    val cx=positions[ic]*scale[0]+translation[0]; val cy=positions[ic+1]*scale[1]+translation[1]; val cz=positions[ic+2]*scale[2]+translation[2]
                    val ux=bx-ax; val uy=by-ay; val uz=bz-az
                    val vx=cx-ax; val vy=cy-ay; val vz=cz-az
                    var nx=uy*vz-uz*vy; var ny=uz*vx-ux*vz; var nz=ux*vy-uy*vx
                    val length=kotlin.math.sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.00001f)
                    nx/=length; ny/=length; nz/=length
                    for (index in intArrayOf(ia,ib,ic)) {
                        expanded.add(positions[index]*scale[0]+translation[0])
                        expanded.add(positions[index+1]*scale[1]+translation[1])
                        expanded.add(positions[index+2]*scale[2]+translation[2])
                        expanded.add(nx); expanded.add(ny); expanded.add(nz)
                    }
                    i += 3
                }
                if (expanded.isNotEmpty()) {
                    val fb = ByteBuffer.allocateDirect(expanded.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                    for (v in expanded) fb.put(v)
                    fb.position(0)
                    parts.add(Part(node.optString("name", "part"), fb, expanded.size / 6, factor))
                }
            }
        }

        val vs = """
            uniform mat4 uMVP;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying float vLight;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
              vec3 n = normalize(aNormal);
              vNormal = n;
              vPosition = aPosition;
              vLight = 0.48 + 0.52 * max(dot(n, normalize(vec3(-0.35,0.85,0.42))), 0.0);
              gl_Position = uMVP * vec4(aPosition,1.0);
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec4 uColor;
            varying float vLight;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
              vec3 n = normalize(vNormal);
              vec3 l = normalize(vec3(-0.35,0.85,0.42));
              vec3 v = normalize(vec3(0.0,0.0,1.0));
              float spec = pow(max(dot(reflect(-l,n),v),0.0),22.0)*0.16;
              vec3 base = uColor.rgb*vLight + vec3(spec);
              gl_FragColor = vec4(base,uColor.a);
            }
        """.trimIndent()
        fun shader(type: Int, source: String): Int {
            val id=GLES20.glCreateShader(type)
            GLES20.glShaderSource(id,source); GLES20.glCompileShader(id)
            val status=IntArray(1); GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
            check(status[0] != 0) { GLES20.glGetShaderInfoLog(id) }
            return id
        }
        val v=shader(GLES20.GL_VERTEX_SHADER,vs); val f=shader(GLES20.GL_FRAGMENT_SHADER,fs)
        program=GLES20.glCreateProgram()
        GLES20.glAttachShader(program,v); GLES20.glAttachShader(program,f); GLES20.glLinkProgram(program)
        val status=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        posHandle=GLES20.glGetAttribLocation(program,"aPosition")
        mvpHandle=GLES20.glGetUniformLocation(program,"uMVP")
        colorHandle=GLES20.glGetUniformLocation(program,"uColor")
        loaded=true
    }

    fun draw(vp: FloatArray, x: Float, z: Float, yaw: Float, bob: Float, avatarScale: Float) {
        if (!loaded || parts.isEmpty()) return
        val base = FloatArray(16)
        Matrix.setIdentityM(base,0)
        Matrix.translateM(base,0,x,bob,z)
        Matrix.rotateM(base,0,Math.toDegrees(yaw.toDouble()).toFloat(),0f,1f,0f)
        Matrix.scaleM(base,0,avatarScale,avatarScale,avatarScale)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posHandle)
        val normalHandle=GLES20.glGetAttribLocation(program,"aNormal")
        GLES20.glEnableVertexAttribArray(normalHandle)
        val moving = abs(bob) > 0.001f
        val gait = if (moving) kotlin.math.sin(System.nanoTime() * 0.000000006).toFloat() else 0f
        for (part in parts) {
            val name = part.name
            val angle = when {
                name.startsWith("Leg_L_thigh") -> gait * 0.48f
                name.startsWith("Leg_R_thigh") -> -gait * 0.48f
                name.startsWith("Leg_L_shin") -> maxOf(0f, -gait) * 0.42f
                name.startsWith("Leg_R_shin") -> maxOf(0f, gait) * 0.42f
                name.startsWith("Shoe_L") -> gait * 0.12f
                name.startsWith("Shoe_R") -> -gait * 0.12f
                name.startsWith("Arm_L") || name == "Hand_L" || name == "Cuff_L" -> -gait * 0.42f
                name.startsWith("Arm_R") || name == "Hand_R" || name == "Cuff_R" -> gait * 0.42f
                else -> 0f
            }
            val pivot = when {
                name.startsWith("Leg_L") || name.startsWith("Cargo_L") || name.startsWith("Shoe_L") -> floatArrayOf(-0.22f, if (name.startsWith("Shoe_L")) 0.36f else if (name.startsWith("Leg_L_shin")) 0.68f else 0.99f, 0f)
                name.startsWith("Leg_R") || name.startsWith("Cargo_R") || name.startsWith("Shoe_R") -> floatArrayOf(0.22f, if (name.startsWith("Shoe_R")) 0.36f else if (name.startsWith("Leg_R_shin")) 0.68f else 0.99f, 0f)
                name.contains("_L") && (name.startsWith("Arm") || name == "Hand_L" || name == "Cuff_L") -> floatArrayOf(-0.46f,1.68f,0f)
                name.contains("_R") && (name.startsWith("Arm") || name == "Hand_R" || name == "Cuff_R") -> floatArrayOf(0.46f,1.68f,0f)
                else -> floatArrayOf(0f,0f,0f)
            }
            val partModel = FloatArray(16)
            val partMvp = FloatArray(16)
            Matrix.setIdentityM(partModel,0)
            Matrix.multiplyMM(partModel,0,base,0,partModel,0)
            if (angle != 0f) {
                Matrix.translateM(partModel,0,pivot[0],pivot[1],pivot[2])
                Matrix.rotateM(partModel,0,Math.toDegrees(angle.toDouble()).toFloat(),1f,0f,0f)
                Matrix.translateM(partModel,0,-pivot[0],-pivot[1],-pivot[2])
            }
            Matrix.multiplyMM(partMvp,0,vp,0,partModel,0)
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,partMvp,0)
            part.vertices.position(0)
            GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,24,part.vertices)
            part.vertices.position(3)
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,24,part.vertices)
            GLES20.glUniform4fv(colorHandle,1,part.color,0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,part.count)
        }
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
}
