/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.glVertexAttribPointer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/*
 * 2D精灵类 - 用于渲染UI元素（背景、按钮）
 */
public class LAppSprite {

    // 构造函数
    public LAppSprite(
        float x,
        float y,
        float width,
        float height,
        int textureId,
        int programId
    ) {
        // 设置矩形区域（以中心点为准）
        rect.left = x - width * 0.5f;
        rect.right = x + width * 0.5f;
        rect.up = y + height * 0.5f;
        rect.down = y - height * 0.5f;

        this.textureId = textureId;

        // 获取着色器中的属性位置
        positionLocation = GLES20.glGetAttribLocation(programId, "position");
        uvLocation = GLES20.glGetAttribLocation(programId, "uv");
        textureLocation = GLES20.glGetUniformLocation(programId, "texture");
        colorLocation = GLES20.glGetUniformLocation(programId, "baseColor");

        // 初始化颜色（白色，完全不透明）
        spriteColor[0] = 1.0f;
        spriteColor[1] = 1.0f;
        spriteColor[2] = 1.0f;
        spriteColor[3] = 1.0f;
    }

    // 渲染精灵
    public void render() {
        // 设置UV坐标
        uvVertex[0] = 1.0f;
        uvVertex[1] = 0.0f;
        uvVertex[2] = 0.0f;
        uvVertex[3] = 0.0f;
        uvVertex[4] = 0.0f;
        uvVertex[5] = 1.0f;
        uvVertex[6] = 1.0f;
        uvVertex[7] = 1.0f;

        // 设置混合模式（透明度）
        GLES20.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 启用顶点属性
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glEnableVertexAttribArray(uvLocation);

        // 设置纹理单元
        GLES20.glUniform1i(textureLocation, 0);

        // 计算顶点坐标
        positionVertex[0] = (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f);
        positionVertex[1] = (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f);
        positionVertex[2] = (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f);
        positionVertex[3] = (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f);
        positionVertex[4] = (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f);
        positionVertex[5] = (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f);
        positionVertex[6] = (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f);
        positionVertex[7] = (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f);

        // 创建顶点缓冲
        if (posVertexFloatBuffer == null) {
            ByteBuffer posVertexByteBuffer = ByteBuffer.allocateDirect(positionVertex.length * 4);
            posVertexByteBuffer.order(ByteOrder.nativeOrder());
            posVertexFloatBuffer = posVertexByteBuffer.asFloatBuffer();
        }
        if (uvVertexFloatBuffer == null) {
            ByteBuffer uvVertexByteBuffer = ByteBuffer.allocateDirect(uvVertex.length * 4);
            uvVertexByteBuffer.order(ByteOrder.nativeOrder());
            uvVertexFloatBuffer = uvVertexByteBuffer.asFloatBuffer();
        }
        posVertexFloatBuffer.put(positionVertex).position(0);
        uvVertexFloatBuffer.put(uvVertex).position(0);

        // 设置顶点属性指针
        glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, posVertexFloatBuffer);
        glVertexAttribPointer(uvLocation, 2, GL_FLOAT, false, 0, uvVertexFloatBuffer);

        // 设置颜色
        GLES20.glUniform4f(colorLocation, spriteColor[0], spriteColor[1], spriteColor[2], spriteColor[3]);

        // 绑定纹理并绘制
        GLES20.glBindTexture(GL_TEXTURE_2D, textureId);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
    }

    private final float[] uvVertex = new float[8];
    private final float[] positionVertex = new float[8];

    private FloatBuffer posVertexFloatBuffer;
    private FloatBuffer uvVertexFloatBuffer;

    /**
     * 使用指定纹理ID立即渲染
     * @param textureId 纹理ID
     * @param uvVertex UV坐标
     */
    public void renderImmediate(int textureId, final float[] uvVertex) {
        // 启用顶点属性
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glEnableVertexAttribArray(uvLocation);

        // 设置纹理单元
        GLES20.glUniform1i(textureLocation, 0);

        // 计算顶点坐标
        float[] positionVertex = {
            (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f),
            (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.up - maxHeight * 0.5f) / (maxHeight * 0.5f),
            (rect.left - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f),
            (rect.right - maxWidth * 0.5f) / (maxWidth * 0.5f), (rect.down - maxHeight * 0.5f) / (maxHeight * 0.5f)
        };

        // 设置位置属性
        {
            ByteBuffer bb = ByteBuffer.allocateDirect(positionVertex.length * 4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer buffer = bb.asFloatBuffer();
            buffer.put(positionVertex);
            buffer.position(0);

            GLES20.glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, buffer);
        }
        // 设置UV属性
        {
            ByteBuffer bb = ByteBuffer.allocateDirect(uvVertex.length * 4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer buffer = bb.asFloatBuffer();
            buffer.put(uvVertex);
            buffer.position(0);

            GLES20.glVertexAttribPointer(uvLocation, 2, GL_FLOAT, false, 0, buffer);
        }

        // 设置颜色
        GLES20.glUniform4f(colorLocation, spriteColor[0], spriteColor[1], spriteColor[2], spriteColor[3]);

        // 绑定纹理并绘制
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
    }

    // 调整大小
    public void resize(float x, float y, float width, float height) {
        rect.left = x - width * 0.5f;
        rect.right = x + width * 0.5f;
        rect.up = y + height * 0.5f;
        rect.down = y - height * 0.5f;
    }

    /**
     * 碰撞检测
     * @param pointX 触摸点X坐标
     * @param pointY 触摸点Y坐标
     * @return 是否命中
     */
    public boolean isHit(float pointX, float pointY) {
        // Y坐标需要转换
        float y = maxHeight - pointY;

        return (pointX >= rect.left && pointX <= rect.right && y <= rect.up && y >= rect.down);
    }

    // 设置颜色
    public void setColor(float r, float g, float b, float a) {
        spriteColor[0] = r;
        spriteColor[1] = g;
        spriteColor[2] = b;
        spriteColor[3] = a;
    }

    /**
     * 设置窗口大小
     * @param width 窗口宽度
     * @param height 窗口高度
     */
    public void setWindowSize(int width, int height) {
        maxWidth = width;
        maxHeight = height;
    }

    /**
     * 矩形类
     */
    private static class Rect {
        /**
         * 左边界
         */
        public float left;
        /**
         * 右边界
         */
        public float right;
        /**
         * 上边界
         */
        public float up;
        /**
         * 下边界
         */
        public float down;
    }


    private final Rect rect = new Rect();
    private final int textureId;

    private final int positionLocation;  // 位置属性
    private final int uvLocation;        // UV属性
    private final int textureLocation;   // 纹理属性
    private final int colorLocation;     // 颜色属性
    private final float[] spriteColor = new float[4];   // 颜色

    private int maxWidth;   // 窗口宽度
    private int maxHeight;  // 窗口高度
}
