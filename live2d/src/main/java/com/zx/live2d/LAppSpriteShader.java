package com.zx.live2d;

import android.opengl.GLES20;

import com.live2d.sdk.cubism.framework.utils.CubismDebug;

/*
 * 着色器类 - 编译和管理GLSL着色器程序
 */
public class LAppSpriteShader implements AutoCloseable {

    // 构造函数 - 创建着色器程序
    public LAppSpriteShader() {
        programId = createShader();
    }

    // 释放资源
    @Override
    public void close() {
        GLES20.glDeleteShader(programId);
    }

    /**
     * 获取着色器ID
     * @return 着色器ID
     */
    public int getShaderId() {
        return programId;
    }

    // 创建着色器程序
    private int createShader() {
        // 构建着色器路径
        String vertShaderFile = LAppDefine.ResourcePath.SHADER_ROOT.getPath();
        vertShaderFile += ("/" + LAppDefine.ResourcePath.VERT_SHADER.getPath());

        String fragShaderFile = LAppDefine.ResourcePath.SHADER_ROOT.getPath();
        fragShaderFile += ("/" + LAppDefine.ResourcePath.FRAG_SHADER.getPath());

        // 编译着色器
        int vertexShaderId = compileShader(vertShaderFile, GLES20.GL_VERTEX_SHADER);
        int fragmentShaderId = compileShader(fragShaderFile, GLES20.GL_FRAGMENT_SHADER);

        if (vertexShaderId == 0 || fragmentShaderId == 0) {
            return 0;
        }

        // 创建程序对象
        int programId = GLES20.glCreateProgram();

        // 绑定着色器到程序
        GLES20.glAttachShader(programId, vertexShaderId);
        GLES20.glAttachShader(programId, fragmentShaderId);

        // 链接程序
        GLES20.glLinkProgram(programId);
        GLES20.glUseProgram(programId);

        // 删除着色器（已链接到程序）
        GLES20.glDeleteShader(vertexShaderId);
        GLES20.glDeleteShader(fragmentShaderId);

        return programId;
    }

    // 检查着色器编译状态
    private boolean checkShader(int shaderId) {
        int[] logLength = new int[1];
        GLES20.glGetShaderiv(shaderId, GLES20.GL_INFO_LOG_LENGTH, logLength, 0);

        if (logLength[0] > 0) {
            String log = GLES20.glGetShaderInfoLog(shaderId);
            CubismDebug.cubismLogError("Shader compile log: %s", log);
        }

        int[] status = new int[1];
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, status, 0);

        if (status[0] == GLES20.GL_FALSE) {
            GLES20.glDeleteShader(shaderId);
            return false;
        }

        return true;
    }

    // 编译着色器
    private int compileShader(String fileName, int shaderType) {
        // 加载着色器源码
        byte[] shaderBuffer = LAppPal.loadFileAsBytes(fileName);

        // 创建着色器
        int shaderId = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shaderId, new String(shaderBuffer));
        GLES20.glCompileShader(shaderId);

        // 检查编译结果
        if (!checkShader(shaderId)) {
            return 0;
        }

        return shaderId;
    }

    private final int programId; // 着色器ID
}
