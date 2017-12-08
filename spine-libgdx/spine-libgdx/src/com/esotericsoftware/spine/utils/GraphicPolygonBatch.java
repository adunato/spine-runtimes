package com.esotericsoftware.spine.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.NumberUtils;

/**
 * Aggregation of LibGDX PolygonSpriteBatch and Spine TwoColorPolygonBatch
 */
public class GraphicPolygonBatch implements Batch {
    private final Mesh mesh;
    private final float[] vertices;
    private final short[] triangles;
    private final Matrix4 transformMatrix = new Matrix4();
    private final Matrix4 projectionMatrix = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();
    private final ShaderProgram defaultShader;
    private ShaderProgram shader;
    private ShaderProgram customShader;
    private int vertexIndex, triangleIndex;
    private Texture lastTexture;
    private boolean drawing;
    private int blendSrcFunc = GL20.GL_SRC_ALPHA;
    private int blendDstFunc = GL20.GL_ONE_MINUS_SRC_ALPHA;
    private int blendSrcFuncAlpha = GL20.GL_SRC_ALPHA;
    private int blendDstFuncAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA;
    private boolean premultipliedAlpha;

    float color;
    float darkColor;
    private Color tempColor;
    public int renderCalls;
    public int totalRenderCalls;
    public int maxTrianglesInBatch;
    private boolean blendingDisabled;
    private float invTexWidth;
    private float invTexHeight;

    public GraphicPolygonBatch(int size) {
        this(size, size * 2);
    }

    public GraphicPolygonBatch(int maxVertices, int maxTriangles) {
        renderCalls = 0;
        totalRenderCalls = 0;
        maxTrianglesInBatch = 0;
        this.blendSrcFunc = 770;
        this.blendDstFunc = 771;
        this.blendSrcFuncAlpha = 770;
        this.blendDstFuncAlpha = 771;
        this.invTexWidth = 0.0F;
        this.invTexHeight = 0.0F;
        darkColor = 0;
        // 32767 is max vertex index.
        if (maxVertices > 32767)
            throw new IllegalArgumentException("Can't have more than 32767 vertices per batch: " + maxTriangles);

        Mesh.VertexDataType vertexDataType = Mesh.VertexDataType.VertexArray;
        if (Gdx.gl30 != null) vertexDataType = Mesh.VertexDataType.VertexBufferObjectWithVAO;
        mesh = new Mesh(vertexDataType, false, maxVertices, maxTriangles * 3, //
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"), //
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_light"), //
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_dark"), // Dark alpha is unused, but colors are packed as 4 byte floats.
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));

        vertices = new float[maxVertices * 6];
        triangles = new short[maxTriangles * 3];
        defaultShader = createDefaultShader();
        shader = defaultShader;
        projectionMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void begin() {
        if (drawing) throw new IllegalStateException("end must be called before begin.");
        this.renderCalls = 0;
        Gdx.gl.glDepthMask(false);
        if (this.customShader != null) {
            this.customShader.begin();
        } else {
            this.shader.begin();
        }

        this.setupMatrices();
        this.drawing = true;
    }

    public void end() {
        if (!drawing) throw new IllegalStateException("begin must be called before end.");
        if (this.vertexIndex > 0) {
            this.flush();
        }

        this.lastTexture = null;
        this.drawing = false;
        GL20 gl = Gdx.gl;
        gl.glDepthMask(true);
        if (this.isBlendingEnabled()) {
            gl.glDisable(3042);
        }

        if (this.customShader != null) {
            this.customShader.end();
        } else {
            this.shader.end();
        }
    }

    @Override
    public void setColor(Color tint) {
        this.color = tint.toFloatBits();
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        int intBits = (int) (255.0F * a) << 24 | (int) (255.0F * b) << 16 | (int) (255.0F * g) << 8 | (int) (255.0F * r);
        this.color = NumberUtils.intToFloatColor(intBits);
    }

    @Override
    public void setColor(float color) {
        this.color = color;
    }

    @Override
    public Color getColor() {
        int intBits = NumberUtils.floatToIntColor(this.color);
        Color color = this.tempColor;
        color.r = (float) (intBits & 255) / 255.0F;
        color.g = (float) (intBits >>> 8 & 255) / 255.0F;
        color.b = (float) (intBits >>> 16 & 255) / 255.0F;
        color.a = (float) (intBits >>> 24 & 255) / 255.0F;
        return color;
    }

    @Override
    public float getPackedColor() {
        return this.color;
    }

    @Override
    public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Texture texture, float v, float v1, float v2, float v3, int i, int i1, int i2, int i3, boolean b, boolean b1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Texture texture, float v, float v1, int i, int i1, int i2, int i3) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Texture texture, float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Texture texture, float v, float v1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Texture texture, float v, float v1, float v2, float v3) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(Texture texture, float[] spriteVertices, int offset, int count) {
        if (!this.drawing) {
            throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
        } else {
            short[] triangles = this.triangles;
            float[] vertices = this.vertices;
            int triangleCount = count / 20 * 6;
            if (texture != this.lastTexture) {
                this.switchTexture(texture);
            } else if (this.triangleIndex + triangleCount > triangles.length || this.vertexIndex + count > vertices.length) {
                this.flush();
            }

            int vertexIndex = this.vertexIndex;
            int triangleIndex = this.triangleIndex;
            short vertex = (short) (vertexIndex / 5);

            for (int n = triangleIndex + triangleCount; triangleIndex < n; vertex = (short) (vertex + 4)) {
                triangles[triangleIndex] = vertex;
                triangles[triangleIndex + 1] = (short) (vertex + 1);
                triangles[triangleIndex + 2] = (short) (vertex + 2);
                triangles[triangleIndex + 3] = (short) (vertex + 2);
                triangles[triangleIndex + 4] = (short) (vertex + 3);
                triangles[triangleIndex + 5] = vertex;
                triangleIndex += 6;
            }

            this.triangleIndex = triangleIndex;
            System.arraycopy(spriteVertices, offset, vertices, vertexIndex, count);
            this.vertexIndex += count;
        }
    }

    @Override
    public void draw(TextureRegion textureRegion, float v, float v1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        if (!this.drawing) {
            throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
        } else {
            short[] triangles = this.triangles;
            float[] vertices = this.vertices;
            Texture texture = region.getTexture();
            if (texture != this.lastTexture) {
                this.switchTexture(texture);
            } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
                this.flush();
            }

            int triangleIndex = this.triangleIndex;
            int startVertex = this.vertexIndex / 5;
            triangles[triangleIndex++] = (short) startVertex;
            triangles[triangleIndex++] = (short) (startVertex + 1);
            triangles[triangleIndex++] = (short) (startVertex + 2);
            triangles[triangleIndex++] = (short) (startVertex + 2);
            triangles[triangleIndex++] = (short) (startVertex + 3);
            triangles[triangleIndex++] = (short) startVertex;
            this.triangleIndex = triangleIndex;
            float fx2 = x + width;
            float fy2 = y + height;
            float u = region.getU();
            float v = region.getV2();
            float u2 = region.getU2();
            float v2 = region.getV();
            float color = this.color;
            float darkColor = this.darkColor;

            int idx = this.vertexIndex;
            vertices[idx++] = x;
            vertices[idx++] = y;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u;
            vertices[idx++] = v;
            vertices[idx++] = x;
            vertices[idx++] = fy2;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u;
            vertices[idx++] = v2;
            vertices[idx++] = fx2;
            vertices[idx++] = fy2;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u2;
            vertices[idx++] = v2;
            vertices[idx++] = fx2;
            vertices[idx++] = y;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u2;
            vertices[idx++] = v;
            this.vertexIndex = idx;
        }
    }

    @Override
    public void draw(TextureRegion textureRegion, float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(TextureRegion textureRegion, float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8, boolean b) {
        throw new UnsupportedOperationException();
    }

    private void switchTexture(Texture texture) {
        this.flush();
        this.lastTexture = texture;
        this.invTexWidth = 1.0F / (float) texture.getWidth();
        this.invTexHeight = 1.0F / (float) texture.getHeight();
    }

    @Override
    public void draw(TextureRegion region, float width, float height, Affine2 transform) {
        if (!this.drawing) {
            throw new IllegalStateException("PolygonSpriteBatch.begin must be called before draw.");
        } else {
            short[] triangles = this.triangles;
            float[] vertices = this.vertices;
            Texture texture = region.getTexture();
            if (texture != this.lastTexture) {
                this.switchTexture(texture);
            } else if (this.triangleIndex + 6 > triangles.length || this.vertexIndex + 20 > vertices.length) {
                this.flush();
            }

            int triangleIndex = this.triangleIndex;
            int startVertex = this.vertexIndex / 5;
            triangles[triangleIndex++] = (short) startVertex;
            triangles[triangleIndex++] = (short) (startVertex + 1);
            triangles[triangleIndex++] = (short) (startVertex + 2);
            triangles[triangleIndex++] = (short) (startVertex + 2);
            triangles[triangleIndex++] = (short) (startVertex + 3);
            triangles[triangleIndex++] = (short) startVertex;
            this.triangleIndex = triangleIndex;
            float x1 = transform.m02;
            float y1 = transform.m12;
            float x2 = transform.m01 * height + transform.m02;
            float y2 = transform.m11 * height + transform.m12;
            float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
            float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
            float x4 = transform.m00 * width + transform.m02;
            float y4 = transform.m10 * width + transform.m12;
            float u = region.getU();
            float v = region.getV2();
            float u2 = region.getU2();
            float v2 = region.getV();
            float color = this.color;
            float darkColor = this.darkColor;
            int idx = this.vertexIndex;
            vertices[idx++] = x1;
            vertices[idx++] = y1;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u;
            vertices[idx++] = v;
            vertices[idx++] = x2;
            vertices[idx++] = y2;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u;
            vertices[idx++] = v2;
            vertices[idx++] = x3;
            vertices[idx++] = y3;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u2;
            vertices[idx++] = v2;
            vertices[idx++] = x4;
            vertices[idx++] = y4;
            vertices[idx++] = color;
            vertices[idx++] = darkColor;
            vertices[idx++] = u2;
            vertices[idx++] = v;
            this.vertexIndex = idx;
        }
    }

    public void draw(Texture texture, float[] polygonVertices, int verticesOffset, int verticesCount, short[] polygonTriangles,
                     int trianglesOffset, int trianglesCount) {
        if (!drawing) throw new IllegalStateException("begin must be called before draw.");

        final short[] triangles = this.triangles;
        final float[] vertices = this.vertices;

        if (texture != lastTexture) {
            flush();
            lastTexture = texture;
        } else if (triangleIndex + trianglesCount > triangles.length || vertexIndex + verticesCount > vertices.length) //
            flush();

        int triangleIndex = this.triangleIndex;
        final int vertexIndex = this.vertexIndex;
        final int startVertex = vertexIndex / 6;

        for (int i = trianglesOffset, n = i + trianglesCount; i < n; i++)
            triangles[triangleIndex++] = (short) (polygonTriangles[i] + startVertex);
        this.triangleIndex = triangleIndex;

        System.arraycopy(polygonVertices, verticesOffset, vertices, vertexIndex, verticesCount);
        this.vertexIndex += verticesCount;
    }

    public void flush() {
        if (this.vertexIndex != 0) {
            ++this.renderCalls;
            ++this.totalRenderCalls;
            int trianglesInBatch = this.triangleIndex;
            if (trianglesInBatch > this.maxTrianglesInBatch) {
                this.maxTrianglesInBatch = trianglesInBatch;
            }

            this.lastTexture.bind();
            Mesh mesh = this.mesh;
            mesh.setVertices(this.vertices, 0, this.vertexIndex);
            mesh.setIndices(this.triangles, 0, this.triangleIndex);
            if (this.blendingDisabled) {
                Gdx.gl.glDisable(3042);
            } else {
                Gdx.gl.glEnable(3042);
                if (this.blendSrcFunc != -1) {
                    Gdx.gl.glBlendFuncSeparate(this.blendSrcFunc, this.blendDstFunc, this.blendSrcFuncAlpha, this.blendDstFuncAlpha);
                }
            }

            mesh.render(this.customShader != null ? this.customShader : this.shader, 4, 0, trianglesInBatch);
            this.vertexIndex = 0;
            this.triangleIndex = 0;
        }
    }

    @Override
    public void disableBlending() {
        this.flush();
        this.blendingDisabled = true;
    }

    @Override
    public void enableBlending() {
        this.flush();
        this.blendingDisabled = false;
    }

    public void dispose() {
        mesh.dispose();
        shader.dispose();
    }

    public Matrix4 getProjectionMatrix() {
        return projectionMatrix;
    }

    public Matrix4 getTransformMatrix() {
        return transformMatrix;
    }

    /**
     * Flushes the batch.
     */
    public void setProjectionMatrix(Matrix4 projection) {
        if (drawing) flush();
        projectionMatrix.set(projection);
        if (drawing) setupMatrices();
    }

    /**
     * Flushes the batch.
     */
    public void setTransformMatrix(Matrix4 transform) {
        if (drawing) flush();
        transformMatrix.set(transform);
        if (drawing) setupMatrices();
    }

    /**
     * Specifies whether the texture colors have premultiplied alpha. Required for correct dark color tinting. Does not change the
     * blending function. Flushes the batch if the setting was changed.
     */
    public void setPremultipliedAlpha(boolean premultipliedAlpha) {
        if (this.premultipliedAlpha == premultipliedAlpha) return;
        if (drawing) flush();
        this.premultipliedAlpha = premultipliedAlpha;
        if (drawing) setupMatrices();
    }

    private void setupMatrices() {
        this.combinedMatrix.set(this.projectionMatrix).mul(this.transformMatrix);
        if (this.customShader != null) {
            this.customShader.setUniformf("u_pma", premultipliedAlpha ? 1 : 0);
            this.customShader.setUniformMatrix("u_projTrans", this.combinedMatrix);
            this.customShader.setUniformi("u_texture", 0);
        } else {
            this.shader.setUniformf("u_pma", premultipliedAlpha ? 1 : 0);
            this.shader.setUniformMatrix("u_projTrans", this.combinedMatrix);
            this.shader.setUniformi("u_texture", 0);
        }
    }

    /**
     * Flushes the batch if the shader was changed.
     */
    public void setShader(ShaderProgram shader) {
        if (this.drawing) {
            this.flush();
            if (this.customShader != null) {
                this.customShader.end();
            } else {
                this.shader.end();
            }
        }

        this.customShader = shader;
        if (this.drawing) {
            if (this.customShader != null) {
                this.customShader.begin();
            } else {
                this.shader.begin();
            }
            this.setupMatrices();
        }
    }

    @Override
    public ShaderProgram getShader() {
        return this.customShader == null ? this.shader : this.customShader;
    }

    @Override
    public boolean isBlendingEnabled() {
        return !this.blendingDisabled;
    }

    @Override
    public boolean isDrawing() {
        return this.drawing;
    }

    /**
     * Flushes the batch if the blend function was changed.
     */
    public void setBlendFunction(int srcFunc, int dstFunc) {
        setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    /**
     * Flushes the batch if the blend function was changed.
     */
    public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha) {
        if (blendSrcFunc == srcFuncColor && blendDstFunc == dstFuncColor && blendSrcFuncAlpha == srcFuncAlpha
                && blendDstFuncAlpha == dstFuncAlpha) return;
        flush();
        blendSrcFunc = srcFuncColor;
        blendDstFunc = dstFuncColor;
        blendSrcFuncAlpha = srcFuncAlpha;
        blendDstFuncAlpha = dstFuncAlpha;
    }

    @Override
    public int getBlendSrcFunc() {
        return this.blendSrcFunc;
    }

    @Override
    public int getBlendDstFunc() {
        return this.blendDstFunc;
    }

    @Override
    public int getBlendSrcFuncAlpha() {
        return this.blendSrcFuncAlpha;
    }

    @Override
    public int getBlendDstFuncAlpha() {
        return this.blendDstFuncAlpha;
    }

    public static ShaderProgram createDefaultShader() {
        String vertexShader = "attribute vec4 a_position;\n" //
                + "attribute vec4 a_light;\n" //
                + "attribute vec4 a_dark;\n" //
                + "attribute vec2 a_texCoord0;\n" //
                + "uniform mat4 u_projTrans;\n" //
                + "varying vec4 v_light;\n" //
                + "varying vec4 v_dark;\n" //
                + "varying vec2 v_texCoords;\n" //
                + "\n" //
                + "void main()\n" //
                + "{\n" //
                + "   v_light = a_light;\n" //
                + "   v_light.a = v_light.a * (255.0/254.0);\n" //
                + "   v_dark = a_dark;\n" //
                + "   v_texCoords = a_texCoord0;\n" //
                + "   gl_Position =  u_projTrans * a_position;\n" //
                + "}\n";
        String fragmentShader = "#ifdef GL_ES\n" //
                + "#define LOWP lowp\n" //
                + "precision mediump float;\n" //
                + "#else\n" //
                + "#define LOWP \n" //
                + "#endif\n" //
                + "varying LOWP vec4 v_light;\n" //
                + "varying LOWP vec4 v_dark;\n" //
                + "uniform float u_pma;\n" //
                + "varying vec2 v_texCoords;\n" //
                + "uniform sampler2D u_texture;\n" //
                + "void main()\n"//
                + "{\n" //
                + "  vec4 texColor = texture2D(u_texture, v_texCoords);\n" //
                + "  gl_FragColor.a = texColor.a * v_light.a;\n" //
                + "  gl_FragColor.rgb = ((texColor.a - 1.0) * u_pma + 1.0 - texColor.rgb) * v_dark.rgb + texColor.rgb * v_light.rgb;\n" //
                + "}";

        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);
        if (shader.isCompiled() == false)
            throw new IllegalArgumentException("Error compiling shader: " + shader.getLog());
        return shader;
    }

}
