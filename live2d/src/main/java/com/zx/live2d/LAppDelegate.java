/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glClearDepthf;

import android.app.Activity;
import android.opengl.GLES20;

import com.live2d.sdk.cubism.framework.CubismFramework;

/*
 * 应用程序委托类 - 单例模式
 * 管理整个应用程序的状态、资源和主循环
 */
public class LAppDelegate {

    // 获取单例实例
    public static LAppDelegate getInstance() {
        if (s_instance == null) {
            s_instance = new LAppDelegate();
        }
        return s_instance;
    }

    /**
     * 释放类的实例（单例）。
     */
    public static void releaseInstance() {
        if (s_instance != null) {
            s_instance = null;
        }
    }

    /**
     * 停用应用程序
     */
    public void deactivateApp() {
        isActive = false;
    }

    // Activity启动时调用 - 初始化资源
    public void onStart(Activity activity) {
        // 创建纹理管理器
        textureManager = new LAppTextureManager();
        // 创建主视图
        view = new LAppView();

        this.activity = activity;

        // 初始化时间
        LAppPal.updateTime();
    }

    public void onPause() {}

    // Activity停止时调用 - 释放资源
    public void onStop() {
        // 关闭视图
        if (view != null) {
            view.close();
        }
        textureManager = null;

        // 释放Live2D管理器
        LAppLive2DManager.releaseInstance();
        // 释放Cubism框架
        CubismFramework.dispose();
    }

    public void onDestroy() {
        releaseInstance();
    }

    // OpenGL表面创建时调用
    public void onSurfaceCreated() {
        // 设置纹理采样参数
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        // 启用混合（透明度）
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        // 初始化Cubism SDK框架
        CubismFramework.initialize();
    }

    // OpenGL表面尺寸变化时调用（如旋转屏幕）
    public void onSurfaceChanged(int width, int height) {
        // 设置视口
        GLES20.glViewport(0, 0, width, height);
        windowWidth = width;
        windowHeight = height;

        // 初始化视图
        view.initialize();
        view.initializeSprite();

        // 设置渲染目标大小
        LAppLive2DManager.getInstance().setRenderTargetSize(width, height);

        // 加载模型（如未加载）
        LAppLive2DManager manager = LAppLive2DManager.getInstance();
        if (manager.getModelNum() == 0) {
            manager.changeScene(sceneIndex);
        }

        isActive = true;
    }

    // 主渲染循环 - 每帧调用
    public void run() {
        // 更新时间（计算deltaTime）
        LAppPal.updateTime();

        // 清屏（白色背景）
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearDepthf(1.0f);

        // 渲染视图
        if (view != null) {
            view.render();
        }

        // 应用停止时退出
        if (!isActive) {
            activity.finishAndRemoveTask();
        }
    }


    // 触摸开始
    public void onTouchBegan(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = true;
            view.onTouchesBegan(mouseX, mouseY);
        }
    }

    // 触摸结束
    public void onTouchEnd(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (view != null) {
            isCaptured = false;
            view.onTouchesEnded(mouseX, mouseY);
        }
    }

    // 触摸移动
    public void onTouchMoved(float x, float y) {
        mouseX = x;
        mouseY = y;

        if (isCaptured && view != null) {
            view.onTouchesMoved(mouseX, mouseY);
        }
    }

    // getter, setter 群组
    public Activity getActivity() {
        return activity;
    }

    public LAppTextureManager getTextureManager() {
        return textureManager;
    }

    public LAppView getView() {
        return view;
    }

    /**
     * 设置场景索引。
     *
     * @param index 场景索引
     */
    public void setSceneIndex(int index) {
        sceneIndex = index;
    }

    /**
     * 获取场景索引。
     *
     * @return 场景索引
     */
    public int getSceneIndex() {
        return sceneIndex;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    /**
     * 设置GLSurfaceView引用
     * @param view GLSurfaceView实例
     */
    public void setGLSurfaceView(android.opengl.GLSurfaceView view) {
        this.glSurfaceView = view;
    }

    /**
     * 在GL线程执行任务
     * @param runnable 要执行的任务
     */
    public void runOnGLThread(Runnable runnable) {
        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(runnable);
        }
    }

    private static LAppDelegate s_instance;

    // 私有构造函数 - 初始化Cubism框架
    private LAppDelegate() {
        sceneIndex = 0;

        // 设置Cubism SDK框架选项
        cubismOption.logFunction = new LAppPal.PrintLogFunction();
        cubismOption.loggingLevel = LAppDefine.cubismLoggingLevel;
        cubismOption.loadFileFunction = new LAppPal.LoadFileFunction();

        CubismFramework.cleanUp();
        CubismFramework.startUp(cubismOption);
    }

    private Activity activity;

    private final CubismFramework.Option cubismOption = new CubismFramework.Option();

    private LAppTextureManager textureManager;
    private LAppView view;
    private int windowWidth;
    private int windowHeight;
    private boolean isActive = true;

    /**
     * 模型场景索引
     */
    private int sceneIndex;

    /**
     * 是否点击中
     */
    private boolean isCaptured;
    /**
     * 鼠标的X坐标
     */
    private float mouseX;
    /**
     * 鼠标的Y坐标
     */
    private float mouseY;

    /**
     * GLSurfaceView引用
     */
    private android.opengl.GLSurfaceView glSurfaceView;
}
