/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import com.live2d.sdk.cubism.framework.CubismFramework;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/*
 * 纹理管理器 - 负责加载OpenGL纹理
 */
public class LAppTextureManager {

    // 纹理信息类
    public static class TextureInfo {
        public int id;          // OpenGL纹理ID
        public int width;       // 宽度
        public int height;      // 高度
        public String filePath; // 文件路径
    }

    // 从PNG文件创建纹理
    public TextureInfo createTextureFromPngFile(String filePath) {
        // 检查是否已加载（缓存）
        for (TextureInfo textureInfo : textures) {
            if (textureInfo.filePath.equals(filePath)) {
                return textureInfo;
            }
        }

        // 从assets加载PNG为Bitmap
        AssetManager assetManager = LAppDelegate.getInstance().getActivity().getAssets();
        InputStream stream = null;
        try {
            stream = assetManager.open(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 解码PNG为Bitmap
        Bitmap bitmap = BitmapFactory.decodeStream(stream);

        // 激活纹理单元0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // 创建OpenGL纹理
        int[] textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);

        // 将Bitmap绑定到纹理
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // 生成Mipmap
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        // 设置缩小过滤（使用Mipmap）
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        // 设置放大过滤
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // 保存纹理信息
        TextureInfo textureInfo = new TextureInfo();
        textureInfo.filePath = filePath;
        textureInfo.width = bitmap.getWidth();
        textureInfo.height = bitmap.getHeight();
        textureInfo.id = textureId[0];

        textures.add(textureInfo);

        // 释放Bitmap（已复制到GPU）
        bitmap.recycle();
        bitmap = null;

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            CubismFramework.coreLogFunction("Create texture: " + filePath);
        }

        return textureInfo;
    }

    // 纹理列表
    private final List<TextureInfo> textures = new ArrayList<TextureInfo>();
}
