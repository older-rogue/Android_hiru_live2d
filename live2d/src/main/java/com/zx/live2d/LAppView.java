/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static com.zx.live2d.LAppDefine.*;

import android.opengl.GLES20;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.math.CubismViewMatrix;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRenderTargetAndroid;

/*
 * 视图类 - 负责UI渲染和事件处理
 * 管理背景、按钮和模型渲染目标
 */
public class LAppView implements AutoCloseable {

    // 渲染目标类型
    public enum RenderingTarget {
        NONE,               // 默认帧缓冲
        MODEL_FRAME_BUFFER, // 模型各自的帧缓冲
        VIEW_FRAME_BUFFER  // 视图的帧缓冲
    }

    // 构造函数 - 初始化清除颜色
    public LAppView() {
        clearColor[0] = 1.0f;
        clearColor[1] = 1.0f;
        clearColor[2] = 1.0f;
        clearColor[3] = 0.0f;
    }

    // 释放资源
    @Override
    public void close() {
        spriteShader.close();
    }

    // 初始化视图
    public void initialize() {
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();

        float ratio = (float) width / (float) height;
        float left = -ratio;
        float right = ratio;
        float bottom = LogicalView.LEFT.getValue();
        float top = LogicalView.RIGHT.getValue();

        // 设置屏幕范围
        viewMatrix.setScreenRect(left, right, bottom, top);
        viewMatrix.scale(Scale.DEFAULT.getValue(), Scale.DEFAULT.getValue());

        // 初始化为单位矩阵
        deviceToScreen.loadIdentity();

        // 根据屏幕方向设置缩放
        if (width > height) {
            float screenW = Math.abs(right - left);
            deviceToScreen.scaleRelative(screenW / width, -screenW / width);
        } else {
            float screenH = Math.abs(top - bottom);
            deviceToScreen.scaleRelative(screenH / height, -screenH / height);
        }
        deviceToScreen.translateRelative(-width * 0.5f, -height * 0.5f);

        // 设置缩放限制
        viewMatrix.setMaxScale(Scale.MAX.getValue());   // 最大放大率
        viewMatrix.setMinScale(Scale.MIN.getValue());   // 最小缩小率

        // 设置最大显示范围
        viewMatrix.setMaxScreenRect(
            MaxLogicalView.LEFT.getValue(),
            MaxLogicalView.RIGHT.getValue(),
            MaxLogicalView.BOTTOM.getValue(),
            MaxLogicalView.TOP.getValue()
        );

        // 创建着色器
        spriteShader = new LAppSpriteShader();
    }

    // 初始化UI精灵
    public void initializeSprite() {
        int windowWidth = LAppDelegate.getInstance().getWindowWidth();
        int windowHeight = LAppDelegate.getInstance().getWindowHeight();
//
        LAppTextureManager textureManager = LAppDelegate.getInstance().getTextureManager();

        // 加载背景图
        LAppTextureManager.TextureInfo backgroundTexture = textureManager.createTextureFromPngFile(ResourcePath.ROOT.getPath() + ResourcePath.BACK_IMAGE.getPath());
//
        // 创建背景精灵（Cover模式：保持比例铺满屏幕）
        float x = windowWidth * 0.5f;
        float y = windowHeight * 0.5f;

        // 计算屏幕和图片的宽高比
        float screenRatio = (float) windowWidth / (float) windowHeight;
        float imageRatio = (float) backgroundTexture.width / (float) backgroundTexture.height;

        float fWidth, fHeight;
        if (screenRatio > imageRatio) {
            // 屏幕更宽，以宽度为准
            fWidth = windowWidth;
            fHeight = fWidth / imageRatio;
        } else {
            // 屏幕更高，以高度为准
            fHeight = windowHeight;
            fWidth = fHeight * imageRatio;
        }

        int programId = spriteShader.getShaderId();

        if (backSprite == null) {
            backSprite = new LAppSprite(x, y, fWidth, fHeight, backgroundTexture.id, programId);
        } else {
            backSprite.resize(x, y, fWidth, fHeight);
        }

//        // 加载齿轮按钮
//        LAppTextureManager.TextureInfo gearTexture = textureManager.createTextureFromPngFile(ResourcePath.ROOT.getPath() + ResourcePath.GEAR_IMAGE.getPath());
//
//        x = windowWidth - gearTexture.width * 0.5f - 96.f;
//        y = windowHeight - gearTexture.height * 0.5f;
//        fWidth = (float) gearTexture.width;
//        fHeight = (float) gearTexture.height;
//
//        if (gearSprite == null) {
//            gearSprite = new LAppSprite(x, y, fWidth, fHeight, gearTexture.id, programId);
//        } else {
//            gearSprite.resize(x, y, fWidth, fHeight);
//        }

//        // 加载电源按钮
//        LAppTextureManager.TextureInfo powerTexture = textureManager.createTextureFromPngFile(ResourcePath.ROOT.getPath() + ResourcePath.POWER_IMAGE.getPath());
//
//        x = windowWidth - powerTexture.width * 0.5f - 96.0f;
//        y = powerTexture.height * 0.5f;
//        fWidth = (float) powerTexture.width;
//        fHeight = (float) powerTexture.height;
//
//        if (powerSprite == null) {
//            powerSprite = new LAppSprite(x, y, fWidth, fHeight, powerTexture.id, programId);
//        } else {
//            powerSprite.resize(x, y, fWidth, fHeight);
//        }

//        // 创建渲染精灵（全屏）
//        x = windowWidth * 0.5f;
//        y = windowHeight * 0.5f;
//
//        if (renderingSprite == null) {
//            renderingSprite = new LAppSprite(x, y, windowWidth, windowHeight, 0, programId);
//        } else {
//            renderingSprite.resize(x, y, windowWidth, windowHeight);
//        }
    }

    // 主渲染方法
    public void render() {
        // 获取屏幕尺寸
        int maxWidth = LAppDelegate.getInstance().getWindowWidth();
        int maxHeight = LAppDelegate.getInstance().getWindowHeight();

//        // 设置窗口大小
        backSprite.setWindowSize(maxWidth, maxHeight);
//        // 渲染UI
        backSprite.render();

        // 检查是否切换模型
        if (isChangedModel) {
            isChangedModel = false;
            LAppLive2DManager.getInstance().nextScene();
        }

        // 渲染Live2D模型
        LAppLive2DManager live2dManager = LAppLive2DManager.getInstance();
        live2dManager.onUpdate();
    }

    /**
     * 模型绘制前调用
     * @param refModel 模型数据
     */
    public void preModelDraw(LAppModel refModel) {
        // 获取渲染目标
        CubismRenderTargetAndroid useTarget;

        // 启用混合
        GLES20.glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        // 如果使用渲染目标
        if (renderingTarget != RenderingTarget.NONE) {
            // 确定渲染目标
            useTarget = (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER)
                        ? renderingBuffer
                        : refModel.getRenderingBuffer();
            int width = LAppDelegate.getInstance().getWindowWidth();
            int height = LAppDelegate.getInstance().getWindowHeight();

            // 创建渲染目标（如需要）
            if (!useTarget.isValid() || (int) useTarget.getBufferWidth() != width || (int) useTarget.getBufferHeight() != height) {
                useTarget.createRenderTarget((int) width, (int) height, null);
            }
            // 开始渲染
            useTarget.beginDraw();
            useTarget.clear(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        }
    }

    /**
     * 模型绘制后调用
     * @param refModel 模型数据
     */
    public void postModelDraw(LAppModel refModel) {
        CubismRenderTargetAndroid useTarget = null;

        // 如果使用渲染目标
        if (renderingTarget != RenderingTarget.NONE) {
            // 确定渲染目标
            useTarget = (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER)
                        ? renderingBuffer
                        : refModel.getRenderingBuffer();

            // 结束渲染
            useTarget.endDraw();

//            // 如果使用视图帧缓冲，渲染到精灵
//            if (renderingTarget == RenderingTarget.VIEW_FRAME_BUFFER && renderingSprite != null) {
//                final float[] uvVertex = {
//                    1.0f, 1.0f,
//                    0.0f, 1.0f,
//                    0.0f, 0.0f,
//                    1.0f, 0.0f
//                };
//                renderingSprite.setColor(1.0f * getSpriteAlpha(0), 1.0f * getSpriteAlpha(0), 1.0f * getSpriteAlpha(0), getSpriteAlpha(0));
//
//                int maxWidth = LAppDelegate.getInstance().getWindowWidth();
//                int maxHeight = LAppDelegate.getInstance().getWindowHeight();
//
//                renderingSprite.setWindowSize(maxWidth, maxHeight);
//                renderingSprite.renderImmediate(useTarget.getColorBuffer()[0], uvVertex);
//            }
        }
    }

    /**
     * 切换渲染目标
     * @param targetType 渲染目标类型
     */
    public void switchRenderingTarget(RenderingTarget targetType) {
        renderingTarget = targetType;
    }

    // 触摸开始
    public void onTouchesBegan(float pointX, float pointY) {
        touchManager.touchesBegan(pointX, pointY);
    }

    // 触摸移动
    public void onTouchesMoved(float pointX, float pointY) {
        // 先更新当前触点
        touchManager.touchesMoved(pointX, pointY);

        // 使用最新坐标转换为视图坐标并传给 onDrag
        float viewX = transformViewX(pointX);
        float viewY = transformViewY(pointY);

        // 触发拖动效果
        LAppLive2DManager.getInstance().onDrag(viewX, viewY);
    }

    // 触摸结束
    public void onTouchesEnded(float pointX, float pointY) {
        // 停止拖动
        LAppLive2DManager live2DManager = LAppLive2DManager.getInstance();
        live2DManager.onDrag(0.0f, 0.0f);

        // 获取逻辑坐标
        float x = deviceToScreen.transformX(touchManager.getLastX());
        float y = deviceToScreen.transformY(touchManager.getLastY());

        if (DEBUG_TOUCH_LOG_ENABLE) {
            LAppPal.printLog("Touches ended x: " + x + ", y:" + y);
        }

        // 触发点击事件
        // live2DManager.onTap(x, y);

        // 检查齿轮按钮点击
//        if (gearSprite.isHit(pointX, pointY)) {
//            isChangedModel = true;  // 切换模型
//        }

        // 检查电源按钮点击
//        if (powerSprite.isHit(pointX, pointY)) {
//            // 退出应用
//            LAppDelegate.getInstance().deactivateApp();
//        }
    }

    /**
     * 将X坐标转换为View坐标
     * @param deviceX 设备X坐标
     * @return ViewX坐标
     */
    public float transformViewX(float deviceX) {
        // 获取逻辑坐标变换后的坐标
        float screenX = deviceToScreen.transformX(deviceX);
        // 放大、缩小、移动后的值
        return viewMatrix.invertTransformX(screenX);
    }

    /**
     * 将Y坐标转换为View坐标
     * @param deviceY 设备Y坐标
     * @return ViewY坐标
     */
    public float transformViewY(float deviceY) {
        // 获取逻辑坐标变换后的坐标
        float screenY = deviceToScreen.transformY(deviceY);
        // 放大、缩小、移动后的值
        return viewMatrix.invertTransformX(screenY);
    }

    /**
     * 将X坐标转换为Screen坐标
     * @param deviceX 设备X坐标
     * @return ScreenX坐标
     */
    public float transformScreenX(float deviceX) {
        return deviceToScreen.transformX(deviceX);
    }

    /**
     * 将Y坐标转换为Screen坐标
     * @param deviceY 设备Y坐标
     * @return ScreenY坐标
     */
    public float transformScreenY(float deviceY) {
        return deviceToScreen.transformX(deviceY);
    }

    /**
     * 设置渲染目标清除颜色
     * @param r 红色(0.0~1.0)
     * @param g 绿色(0.0~1.0)
     * @param b 蓝色(0.0~1.0)
     */
    public void setRenderingTargetClearColor(float r, float g, float b) {
        clearColor[0] = r;
        clearColor[1] = g;
        clearColor[2] = b;
    }

    /**
     * 获取精灵透明度
     * @param assign 精灵索引
     * @return 透明度
     */
    public float getSpriteAlpha(int assign) {
        // 根据索引计算透明度
        float alpha = 0.4f + (float) assign * 0.5f;

        if (alpha > 1.0f) {
            alpha = 1.0f;
        }
        if (alpha < 0.1f) {
            alpha = 0.1f;
        }
        return alpha;
    }

    /**
     * 获取渲染目标
     * @return 渲染目标
     */
    public RenderingTarget getRenderingTarget() {
        return renderingTarget;
    }

    // 设备到屏幕转换矩阵
    private final CubismMatrix44 deviceToScreen = CubismMatrix44.create();
    // 视图矩阵
    private final CubismViewMatrix viewMatrix = new CubismViewMatrix();
    private int windowWidth;
    private int windowHeight;

    /**
     * 渲染目标类型
     */
    private RenderingTarget renderingTarget = RenderingTarget.NONE;
    /**
     * 清除颜色
     */
    private final float[] clearColor = new float[4];

    private CubismRenderTargetAndroid renderingBuffer = new CubismRenderTargetAndroid();

    private LAppSprite backSprite;
//    private LAppSprite gearSprite;
//    private LAppSprite powerSprite;
//    private LAppSprite renderingSprite;

    /**
     * 模型切换标志
     */
    private boolean isChangedModel;

    public void nextScene(){
        isChangedModel = true;
    }

    private final TouchManager touchManager = new TouchManager();

    /**
     * 着色器
     */
    private LAppSpriteShader spriteShader;
}
