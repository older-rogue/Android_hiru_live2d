/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/*
 * OpenGL渲染器
 * 实现GLSurfaceView.Renderer接口，处理OpenGL生命周期回调
 */
public class GLRenderer implements GLSurfaceView.Renderer {

    // 表面创建时调用（EGL上下文创建/重建时）
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        LAppDelegate.getInstance().onSurfaceCreated();
    }

    // 表面尺寸变化时调用（如旋转屏幕）
    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        LAppDelegate.getInstance().onSurfaceChanged(width, height);
    }

    // 每帧渲染时调用（主渲染循环）
    @Override
    public void onDrawFrame(GL10 unused) {
        // 执行主循环
        LAppDelegate.getInstance().run();
    }
}
