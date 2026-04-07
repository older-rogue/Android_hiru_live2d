/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import static com.zx.live2d.LAppDefine.*;

import android.content.res.AssetManager;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.IBeganMotionCallback;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenManagerAndroid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Live2D模型管理器 - 单例模式
 * 负责模型加载、场景管理、事件处理
 */
public class LAppLive2DManager {

    /**
     * 加载状态枚举
     */
    public enum LoadState {
        IDLE,       // 空闲
        LOADING,    // 加载中
        READY,      // 就绪
        ERROR       // 加载失败
    }

    private volatile LoadState loadState = LoadState.IDLE;

    // 获取单例实例
    public static LAppLive2DManager getInstance() {
        if (s_instance == null) {
            s_instance = new LAppLive2DManager();
        }
        return s_instance;
    }

    // 释放单例实例
    public static void releaseInstance() {
        if (s_instance != null) {
            s_instance.releaseAllModel();
            CubismOffscreenManagerAndroid.releaseInstance();
        }
        s_instance = null;
    }

    /**
     * 释放当前场景所有模型
     */
    public void releaseAllModel() {
        for (LAppModel model : models) {
            model.deleteModel();
        }
        models.clear();
    }

    /**
     * 扫描assets目录查找模型
     * 遍历assets文件夹，查找与文件夹同名的.model3.json文件
     */
    public void setUpModel() {
        modelDir.clear();

        final AssetManager assets = LAppDelegate.getInstance().getActivity().getResources().getAssets();
        try {
            String[] root = assets.list("");
            for (String subdir: root) {
                String[] files = assets.list(subdir);
                String target = subdir + ".model3.json";
                // 查找与文件夹同名的.model3.json
                for (String file : files) {
                    if (file.equals(target)) {
                        modelDir.add(subdir);
                        break;
                    }
                }
            }
            Collections.sort(modelDir);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // 每帧更新和绘制模型
    public void onUpdate() {
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();
        float aspectRatio = (float) width / (float) height;
        float displayRatio = (float) height / (float) width;

        // 开始离屏渲染处理
        CubismOffscreenManagerAndroid.getInstance().beginFrameProcess();

        // 遍历所有模型进行渲染
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);

            if (model.getModel() == null) {
                LAppPal.printLog("Failed to model.getModel().");
                continue;
            }

            projection.loadIdentity();

            // 获取模型画布比例
            float canvasRatio = model.getModel().getCanvasHeight() / model.getModel().getCanvasWidth();

            // 根据模型比例调整投影矩阵
            if (canvasRatio < displayRatio) {
                // 横长模型：宽度适配，调整高度
                model.getModelMatrix().setWidth(4.0f);
                projection.scale(1.0f, aspectRatio);
                projection.translateY(-0.4f);
            } else {
                // 竖长模型：高度适配，调整宽度
                model.getModelMatrix().setHeight(2.0f);
                projection.scale(1.0f / aspectRatio, 1.0f);
            }

            // 应用视图矩阵
            if (viewMatrix != null) {
                viewMatrix.multiplyByMatrix(projection);
            }

            // 渲染前回调
            LAppDelegate.getInstance().getView().preModelDraw(model);

            // 更新模型
            model.update();

            // 绘制模型
            model.draw(projection);

            // 渲染后回调
            LAppDelegate.getInstance().getView().postModelDraw(model);
        }

        // 结束离屏渲染
        CubismOffscreenManagerAndroid.getInstance().endFrameProcess();
        // 释放过期纹理
        CubismOffscreenManagerAndroid.getInstance().releaseStaleRenderTextures();
    }

    /**
     * 拖动处理 - 调整模型视线/朝向
     * @param x 屏幕x坐标
     * @param y 屏幕y坐标
     */
    public void onDrag(float x, float y) {
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = getModel(i);
            model.setDragging(x, y);
        }
    }

    /**
     * 点击处理 - 触发表情/动作
     * @param x 屏幕x坐标
     * @param y 屏幕y坐标
     */
    public void onTap(float x, float y) {
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();
        float aspectRatio = (float) width / (float) height;
        float displayRatio = (float) height / (float) width;

        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("tap point: {" + x + ", y: " + y);
        }

        // 遍历模型检测点击位置
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);
            float canvasRatio = model.getModel().getCanvasHeight() / model.getModel().getCanvasWidth();

            float adjustedX = x;
            float adjustedY = y;

            // 坐标转换
            if (canvasRatio < displayRatio) {
                adjustedX = x / aspectRatio;
                adjustedY = y / aspectRatio;
            }

            // 头部区域：随机表情
            if (model.hitTest(HitAreaName.HEAD.getId(), adjustedX, adjustedY)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: " + HitAreaName.HEAD.getId());
                }
                model.setRandomExpression();
            }
            // 身体区域：随机动作
            else if (model.hitTest(HitAreaName.BODY.getId(), adjustedX, adjustedY)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("hit area: " + HitAreaName.HEAD.getId());
                }

                model.startRandomMotion(MotionGroup.TAP_BODY.getId(), Priority.NORMAL.getPriority(), finishedMotion, beganMotion);
            }
        }
    }

    /**
     * 切换到下一场景
     */
    public void nextScene() {
        final int number = (LAppDelegate.getInstance().getSceneIndex() + 1) % modelDir.size();
        changeScene(number);
    }

    /**
     * 切换场景 - 加载新模型
     * @param index 场景索引
     */
    public void changeScene(int index) {
        changeScene(index, null);
    }

    /**
     * 切换场景 - 加载新模型（带进度回调）
     * @param index 场景索引
     * @param callback 加载回调（可为null）
     */
    public void changeScene(int index, final ModelLoadCallback callback) {
        if (index < 0 || index >= modelDir.size()) {
            LAppPal.printLog("Invalid model index: " + index);
            loadState = LoadState.ERROR;
            if (callback != null) {
                callback.onLoadError("Invalid model index: " + index);
            }
            return;
        }
        loadState = LoadState.LOADING;
        LAppDelegate.getInstance().setSceneIndex(index);
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("model index: " + index);
        }

        String modelDirName = modelDir.get(index);

        String modelPath = ResourcePath.ROOT.getPath() + modelDirName + "/";
        String modelJsonName = modelDirName + ".model3.json";

        // 释放旧模型
        releaseAllModel();

        // 创建新模型（带进度回调）
        models.add(new LAppModel());
        models.get(0).loadAssetsWithCallback(modelPath, modelJsonName, callback);
        loadState = LoadState.READY;

        // 如使用渲染目标，创建第二个模型用于演示半透明效果
        LAppView.RenderingTarget useRenderingTarget;
        if (USE_RENDER_TARGET) {
            // 使用LAppView的渲染目标
            useRenderingTarget = LAppView.RenderingTarget.VIEW_FRAME_BUFFER;
        } else if (USE_MODEL_RENDER_TARGET) {
            // 使用各模型的渲染目标
            useRenderingTarget = LAppView.RenderingTarget.MODEL_FRAME_BUFFER;
        } else {
            // 默认渲染到主帧缓冲
            useRenderingTarget = LAppView.RenderingTarget.NONE;
        }

        // 创建第二个模型用于演示半透明效果
        if (USE_RENDER_TARGET || USE_MODEL_RENDER_TARGET) {
            models.add(new LAppModel());
            models.get(1).loadAssets(modelPath, modelJsonName);
            models.get(1).getModelMatrix().translateX(0.2f);  // 偏移位置
        }

        // 切换渲染目标
        LAppDelegate.getInstance().getView().switchRenderingTarget(useRenderingTarget);

        // 设置背景清除颜色
        float[] clearColor = {0.0f, 0.0f, 0.0f};
        LAppDelegate.getInstance().getView().setRenderingTargetClearColor(clearColor[0], clearColor[1], clearColor[2]);
    }

    /**
     * 异步切换场景 - 加载新模型
     * @param index 场景索引
     * @param callback 加载回调
     */
    public void changeSceneAsync(int index, final ModelLoadCallback callback) {
        if (loadState == LoadState.LOADING) {
            if (callback != null) {
                callback.onLoadError("Another model is loading.");
            }
            return;
        }

        if (index < 0 || index >= modelDir.size()) {
            if (callback != null) {
                callback.onLoadError("Invalid model index: " + index);
            }
            return;
        }

        loadState = LoadState.LOADING;
        LAppDelegate.getInstance().setSceneIndex(index);

        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("model index: " + index);
        }

        String modelDirName = modelDir.get(index);
        String modelPath = ResourcePath.ROOT.getPath() + modelDirName + "/";
        String modelJsonName = modelDirName + ".model3.json";

        // 释放旧模型
        releaseAllModel();

        // 创建新模型并异步加载
        final LAppModel newModel = new LAppModel();
        models.add(newModel);

        newModel.loadAssetsAsync(modelPath, modelJsonName, new ModelLoadCallback() {
            @Override
            public void onLoadProgress(int current, int total, String currentFile) {
                if (callback != null) {
                    callback.onLoadProgress(current, total, currentFile);
                }
            }

            @Override
            public void onLoadComplete() {
                loadState = LoadState.READY;
                if (callback != null) {
                    callback.onLoadComplete();
                }
            }

            @Override
            public void onLoadError(String error) {
                loadState = LoadState.ERROR;
                if (callback != null) {
                    callback.onLoadError(error);
                }
            }
        });

        // 切换渲染目标
        LAppView.RenderingTarget useRenderingTarget = LAppView.RenderingTarget.NONE;
        LAppDelegate.getInstance().getView().switchRenderingTarget(useRenderingTarget);

        float[] clearColor = {0.0f, 0.0f, 0.0f};
        LAppDelegate.getInstance().getView().setRenderingTargetClearColor(clearColor[0], clearColor[1], clearColor[2]);
    }

    /**
     * 获取模型
     * @param number 模型索引
     * @return 模型实例
     */
    public LAppModel getModel(int number) {
        if (number < models.size()) {
            return models.get(number);
        }
        return null;
    }

    /**
     * 获取模型数量
     * @return 模型数量
     */
    public int getModelNum() {
        if (models == null) {
            return 0;
        }
        return models.size();
    }

    /**
     * 设置模型离屏渲染尺寸
     * @param width 窗口宽度
     * @param height 窗口高度
     */
    public void setRenderTargetSize(int width, int height) {
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);
            model.setRenderTargetSize(width, height);
        }
    }

    /**
     * 获取当前加载状态
     * @return 加载状态
     */
    public LoadState getLoadState() {
        return loadState;
    }

    /**
     * 设置唇同步开关
     * @param enabled 是否启用唇同步
     */
    public void setLipSyncEnabled(boolean enabled) {
        LipSyncManager.getInstance().setEnabled(enabled);
        for (LAppModel model : models) {
            model.setLipSync(enabled);
        }
    }

    /**
     * 动作开始时的回调
     */
    private static class BeganMotion implements IBeganMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("Motion Began: " + motion);
        }
    }

    private static final BeganMotion beganMotion = new BeganMotion();

    /**
     * 动作结束时的回调
     */
    private static class FinishedMotion implements IFinishedMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("Motion Finished: " + motion);
        }
    }

    private static final FinishedMotion finishedMotion = new FinishedMotion();

    /**
     * 单例实例
     */
    private static LAppLive2DManager s_instance;

    // 私有构造函数
    private LAppLive2DManager() {
        setUpModel();
        changeScene(LAppDelegate.getInstance().getSceneIndex());
    }

    private final List<LAppModel> models = new ArrayList<>();

    public List<LAppModel> getModels() {
        return models;
    }

    /**
     * 模型目录列表
     */
    private final List<String> modelDir = new ArrayList<>();

    // 视图矩阵和投影矩阵
    private final CubismMatrix44 viewMatrix = CubismMatrix44.create();
    private final CubismMatrix44 projection = CubismMatrix44.create();
}
