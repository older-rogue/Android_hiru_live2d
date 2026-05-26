/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import com.live2d.sdk.cubism.framework.CubismDefaultParameterId.ParameterId;
import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.CubismModelSettingJson;
import com.live2d.sdk.cubism.framework.ICubismModelSetting;
import com.live2d.sdk.cubism.framework.effect.CubismBreath;
import com.live2d.sdk.cubism.framework.effect.CubismEyeBlink;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.id.CubismIdManager;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.model.CubismMoc;
import com.live2d.sdk.cubism.framework.model.CubismUserModel;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion;
import com.live2d.sdk.cubism.framework.motion.CubismMotion;
import com.live2d.sdk.cubism.framework.motion.IBeganMotionCallback;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;
import com.live2d.sdk.cubism.framework.rendering.CubismRenderer;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRenderTargetAndroid;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid;
import com.live2d.sdk.cubism.framework.utils.CubismDebug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Live2D模型类 - 继承CubismUserModel
 * 负责加载模型、渲染、更新、事件处理
 */
public class LAppModel extends CubismUserModel {

    /**
     * 眼球跟随增益系数
     * 用于放大眼球跟随幅度，使视觉反馈更明显
     */
    private static final float EYE_BALL_DRAG_MULTIPLIER = 2.0f;

    /**
     * 模型资源数据容器
     * 用于在后台线程加载完成后传递给GL线程
     */
    private static class ModelResourceData {
        ICubismModelSetting setting;
        byte[] moc3Buffer;
        List<byte[]> expressionBuffers;
        List<String> expressionNames;
        byte[] physicsBuffer;
        byte[] poseBuffer;
        byte[] userDataBuffer;
    }

    // 构造函数 - 初始化参数ID
    public LAppModel() {
        // 根据配置启用MOC一致性验证
        if (LAppDefine.MOC_CONSISTENCY_VALIDATION_ENABLE) {
            mocConsistency = true;
        }

        if (LAppDefine.MOTION_CONSISTENCY_VALIDATION_ENABLE) {
            motionConsistency = true;
        }

        if (LAppDefine.DEBUG_LOG_ENABLE) {
            debugMode = true;
        }

        // 获取参数ID管理器
        CubismIdManager idManager = CubismFramework.getIdManager();
        if (idManager == null) {
            throw new IllegalStateException(
                "CubismFramework.getIdManager() returned null. " +
                "Ensure CubismFramework.startUp() and initialize() have been called before creating LAppModel."
            );
        }

        // 获取常用参数ID（头部角度、眼球位置等）
        idParamAngleX = idManager.getId(ParameterId.ANGLE_X.getId());
        idParamAngleY = idManager.getId(ParameterId.ANGLE_Y.getId());
        idParamAngleZ = idManager.getId(ParameterId.ANGLE_Z.getId());
        idParamBodyAngleX = idManager.getId(ParameterId.BODY_ANGLE_X.getId());
        idParamEyeBallX = idManager.getId(ParameterId.EYE_BALL_X.getId());
        idParamEyeBallY = idManager.getId(ParameterId.EYE_BALL_Y.getId());
    }

    // 加载模型资源
    public void loadAssets(final String dir, final String fileName) {
        loadAssetsWithCallback(dir, fileName, null);
    }

    /**
     * 加载模型资源（带进度回调）
     * @param dir 模型目录
     * @param fileName 模型配置文件名
     * @param callback 加载回调（可为null）
     */
    public void loadAssetsWithCallback(final String dir, final String fileName, final ModelLoadCallback callback) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("load model setting: " + fileName);
        }

        modelHomeDirectory = dir;
        String filePath = modelHomeDirectory + fileName;

        // 计算总文件数
        int totalFiles = 1; // model3.json

        // 加载model3.json设置文件
        notifyProgress(callback, 1, totalFiles, fileName);
        byte[] buffer = createBuffer(filePath);
        ICubismModelSetting setting = new CubismModelSettingJson(buffer);

        // 更新总文件数
        totalFiles += setting.getExpressionCount();
        if (!setting.getPhysicsFileName().equals("")) totalFiles++;
        if (!setting.getPoseFileName().equals("")) totalFiles++;
        if (!setting.getUserDataFile().equals("")) totalFiles++;
        totalFiles += setting.getTextureCount();

        // 设置模型（带进度回调）
        setupModelWithCallback(setting, callback, totalFiles);

        if (model == null) {
            notifyError(callback, "Failed to loadAssets().");
            return;
        }

        // 创建并设置渲染器
        CubismRenderer renderer = CubismRendererAndroid.create(
            LAppDelegate.getInstance().getWindowWidth(),
            LAppDelegate.getInstance().getWindowHeight()
        );
        setupRenderer(renderer);

        // 设置纹理
        setupTextures();

        notifyComplete(callback);
    }

    /**
     * 在UI线程通知加载完成
     */
    private void notifyComplete(final ModelLoadCallback callback) {
        if (callback == null) {
            return;
        }
        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onLoadComplete();
            }
        });
    }

    /**
     * 在UI线程通知加载错误
     */
    private void notifyError(final ModelLoadCallback callback, final String error) {
        if (callback == null) {
            return;
        }
        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onLoadError(error);
            }
        });
    }

    /**
     * 异步加载模型资源
     * 文件加载在后台线程进行，GL操作在GL线程执行
     * @param dir 模型目录
     * @param fileName 模型配置文件名
     * @param callback 加载回调
     */
    public void loadAssetsAsync(final String dir, final String fileName, final ModelLoadCallback callback) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("load model setting async: " + fileName);
        }

        // 防止并发加载
        if (isLoading) {
            callback.onLoadError("Model is already loading.");
            return;
        }
        isLoading = true;

        modelHomeDirectory = dir;
        final String filePath = modelHomeDirectory + fileName;

        // 后台线程加载所有文件
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ModelResourceData data = new ModelResourceData();

                    // 加载model3.json设置文件
                    byte[] settingBuffer = createBuffer(filePath);
                    data.setting = new CubismModelSettingJson(settingBuffer);

                    // 计算总文件数用于进度
                    int totalFiles = 1;
                    totalFiles += data.setting.getExpressionCount();
                    if (!data.setting.getPhysicsFileName().equals("")) totalFiles++;
                    if (!data.setting.getPoseFileName().equals("")) totalFiles++;
                    if (!data.setting.getUserDataFile().equals("")) totalFiles++;

                    int currentFile = 0;
                    final int finalTotalFiles = totalFiles;
                    final String finalFileName = fileName;

                    // 进度回调在UI线程执行
                    currentFile++;
                    final int finalCurrentFile1 = currentFile;
                    LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onLoadProgress(finalCurrentFile1, finalTotalFiles, finalFileName);
                        }
                    });

                    // 加载 .moc3
                    String moc3FileName = data.setting.getModelFileName();
                    if (!moc3FileName.equals("")) {
                        data.moc3Buffer = createBuffer(modelHomeDirectory + moc3FileName);
                        currentFile++;
                        final int finalCurrentFile2 = currentFile;
                        final String finalMoc3FileName = moc3FileName;
                        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadProgress(finalCurrentFile2, finalTotalFiles, finalMoc3FileName);
                            }
                        });
                    }

                    // 加载表情文件
                    data.expressionBuffers = new ArrayList<byte[]>();
                    data.expressionNames = new ArrayList<String>();
                    int exprCount = data.setting.getExpressionCount();
                    for (int i = 0; i < exprCount; i++) {
                        String name = data.setting.getExpressionName(i);
                        String path = data.setting.getExpressionFileName(i);
                        byte[] buffer = createBuffer(modelHomeDirectory + path);
                        data.expressionNames.add(name);
                        data.expressionBuffers.add(buffer);
                        currentFile++;
                        final int finalCurrentFile3 = currentFile;
                        final String finalPath = path;
                        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadProgress(finalCurrentFile3, finalTotalFiles, finalPath);
                            }
                        });
                    }

                    // 加载物理文件
                    String physicsPath = data.setting.getPhysicsFileName();
                    if (!physicsPath.equals("")) {
                        data.physicsBuffer = createBuffer(modelHomeDirectory + physicsPath);
                        currentFile++;
                        final int finalCurrentFile4 = currentFile;
                        final String finalPhysicsPath = physicsPath;
                        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadProgress(finalCurrentFile4, finalTotalFiles, finalPhysicsPath);
                            }
                        });
                    }

                    // 加载姿势文件
                    String posePath = data.setting.getPoseFileName();
                    if (!posePath.equals("")) {
                        data.poseBuffer = createBuffer(modelHomeDirectory + posePath);
                        currentFile++;
                        final int finalCurrentFile5 = currentFile;
                        final String finalPosePath = posePath;
                        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadProgress(finalCurrentFile5, finalTotalFiles, finalPosePath);
                            }
                        });
                    }

                    // 加载用户数据
                    String userDataPath = data.setting.getUserDataFile();
                    if (!userDataPath.equals("")) {
                        data.userDataBuffer = createBuffer(modelHomeDirectory + userDataPath);
                        currentFile++;
                        final int finalCurrentFile6 = currentFile;
                        final String finalUserDataPath = userDataPath;
                        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadProgress(finalCurrentFile6, finalTotalFiles, finalUserDataPath);
                            }
                        });
                    }

                    // 在GL线程中初始化模型
                    final ModelResourceData finalData = data;
                    LAppDelegate.getInstance().runOnGLThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                setupModelFromData(finalData);
                                if (model == null) {
                                    isLoading = false;
                                    callback.onLoadError("Failed to setup model.");
                                    return;
                                }

                                // 创建渲染器
                                CubismRenderer renderer = CubismRendererAndroid.create(
                                    LAppDelegate.getInstance().getWindowWidth(),
                                    LAppDelegate.getInstance().getWindowHeight()
                                );
                                setupRenderer(renderer);

                                // 设置纹理
                                setupTextures();

                                isLoading = false;
                                callback.onLoadComplete();
                            } catch (Exception e) {
                                isLoading = false;
                                callback.onLoadError("GL setup error: " + e.getMessage());
                            }
                        }
                    });

                } catch (Exception e) {
                    isLoading = false;
                    callback.onLoadError("Error loading model: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 删除模型
     */
    public void deleteModel() {
        delete();
    }

    /**
     * 每帧更新模型状态 - 计算模型参数
     */
    public void update() {
        // 获取帧间隔时间
        final float deltaTimeSeconds = LAppPal.getDeltaTime();
        userTimeSeconds += deltaTimeSeconds;

        // 获取拖动位置
        dragManager.update(deltaTimeSeconds);
        dragX = dragManager.getX();
        dragY = dragManager.getY();

        // 动作更新标志
        boolean isMotionUpdated = false;

        // 加载保存的参数状态
        model.loadParameters();

        // 待机动作：如果没有动作在播放，播放随机待机动作
        if (motionManager.isFinished()) {
            startRandomMotion(LAppDefine.MotionGroup.IDLE.getId(), LAppDefine.Priority.IDLE.getPriority());
        } else {
            // 更新动作
            isMotionUpdated = motionManager.updateMotion(model, deltaTimeSeconds);
        }

        // 保存参数状态
        model.saveParameters();

        // 透明度
        opacity = model.getModelOpacity();

        // 眨眼：主动作不在更新时才眨眼
        if (!isMotionUpdated) {
            if (eyeBlink != null) {
                eyeBlink.updateParameters(model, deltaTimeSeconds);
            }
        }

        // 表情更新
        if (expressionManager != null) {
            expressionManager.updateMotion(model, deltaTimeSeconds);
        }

        // 拖动效果 - 调整头部朝向
        model.addParameterValue(idParamAngleX, dragX * 30);   // 左右转头 -30到30
        model.addParameterValue(idParamAngleY, dragY * 30);
        model.addParameterValue(idParamAngleZ, dragX * dragY * (-30));

        // 拖动效果 - 身体转向
        model.addParameterValue(idParamBodyAngleX, dragX * 10); // -10到10

        // 拖动效果 - 眼球移动（放大跟随幅度）
        model.addParameterValue(idParamEyeBallX, dragX * EYE_BALL_DRAG_MULTIPLIER);
        model.addParameterValue(idParamEyeBallY, dragY * EYE_BALL_DRAG_MULTIPLIER);

        // 呼吸效果
        if (breath != null) {
            breath.updateParameters(model, deltaTimeSeconds);
        }

        // 物理效果
        if (physics != null) {
            physics.evaluate(model, deltaTimeSeconds);
        }

        // 唇同步（口型）
        if (lipSync) {
            // 从 LipSyncManager 获取嘴部开合值
            float value = LipSyncManager.getInstance().getMouthValue();
            for (int i = 0; i < lipSyncIds.size(); i++) {
                CubismId lipSyncId = lipSyncIds.get(i);
                model.addParameterValue(lipSyncId, value, 0.8f);
            }
        }

        // 姿势效果
        if (pose != null) {
            pose.updateParameters(model, deltaTimeSeconds);
        }

        // 更新模型内部状态
        model.update();
    }

    /**
     * 开始播放指定动作
     * @param group 动作组名
     * @param number 动作编号
     * @param priority 优先级
     * @return 动作标识符
     */
    public int startMotion(final String group, int number, int priority) {
        return startMotion(group, number, priority, null, null);
    }

    /**
     * 开始播放指定动作（带回调）
     * @param group 动作组名
     * @param number 动作编号
     * @param priority 优先级
     * @param onFinishedMotionHandler 动作结束回调
     * @param onBeganMotionHandler 动作开始回调
     * @return 动作标识符
     */
    public int startMotion(final String group,
                           int number,
                           int priority,
                           IFinishedMotionCallback onFinishedMotionHandler,
                           IBeganMotionCallback onBeganMotionHandler
    ) {
        // 优先级检查
        if (!checkPriority(priority)) {
            return -1;
        }

        // 获取或加载动作（自动处理缓存）
        CubismMotion motion = getOrLoadMotion(group, number);
        if (motion == null) {
            motionManager.setReservationPriority(LAppDefine.Priority.NONE.getPriority());
            return -1;
        }

        // 设置回调
        motion.setBeganMotionHandler(onBeganMotionHandler);
        motion.setFinishedMotionHandler(onFinishedMotionHandler);

        // 播放音频（如有）
        playMotionSound(group, number);

        if (debugMode) {
            LAppPal.printLog("start motion: " + group + "_" + number);
        }
        return motionManager.startMotionPriority(motion, priority);
    }

    /**
     * 播放随机动作
     * @param group 动作组名
     * @param priority 优先级
     * @return 动作标识符
     */
    public int startRandomMotion(final String group, int priority) {
        return startRandomMotion(group, priority, null, null);
    }

    /**
     * 播放随机动作（带回调）
     * @param group 动作组名
     * @param priority 优先级
     * @param onFinishedMotionHandler 动作结束回调
     * @return 动作标识符
     */
    public int startRandomMotion(final String group, int priority, IFinishedMotionCallback onFinishedMotionHandler, IBeganMotionCallback onBeganMotionHandler) {
        if (modelSetting.getMotionCount(group) == 0) {
            return -1;
        }

        Random random = new Random();
        int number = random.nextInt(Integer.MAX_VALUE) % modelSetting.getMotionCount(group);

        return startMotion(group, number, priority, onFinishedMotionHandler, onBeganMotionHandler);
    }

    /**
     * 检查动作优先级是否允许播放
     * @param priority 优先级
     * @return 是否允许
     */
    private boolean checkPriority(int priority) {
        if (priority == LAppDefine.Priority.FORCE.getPriority()) {
            motionManager.setReservationPriority(priority);
            return true;
        }
        if (!motionManager.reserveMotion(priority)) {
            if (debugMode) {
                LAppPal.printLog("Cannot start motion.");
            }
            return false;
        }
        return true;
    }

    /**
     * 根据配置加载动作文件
     * @param group 动作组名
     * @param number 动作编号
     * @return 加载的动作对象，失败返回null
     */
    private CubismMotion loadMotionFromSetting(String group, int number) {
        String fileName = modelSetting.getMotionFileName(group, number);
        if (fileName.equals("")) {
            return null;
        }

        String path = modelHomeDirectory + fileName;
        byte[] buffer = createBuffer(path);

        CubismMotion motion = loadMotion(buffer, motionConsistency);
        if (motion == null) {
            CubismDebug.cubismLogError("Can't load motion %s", path);
            return null;
        }

        // 设置淡入时间
        float fadeInTime = modelSetting.getMotionFadeInTimeValue(group, number);
        if (fadeInTime != -1.0f) {
            motion.setFadeInTime(fadeInTime);
        }

        // 设置淡出时间
        float fadeOutTime = modelSetting.getMotionFadeOutTimeValue(group, number);
        if (fadeOutTime != -1.0f) {
            motion.setFadeOutTime(fadeOutTime);
        }

        // 设置效果ID
        motion.setEffectIds(eyeBlinkIds, lipSyncIds);

        return motion;
    }

    /**
     * 从缓存获取动作，缓存未命中时自动加载
     * @param group 动作组名
     * @param number 动作编号
     * @return 动作对象，失败返回null
     */
    private CubismMotion getOrLoadMotion(String group, int number) {
        String name = group + "_" + number;
        CubismMotion motion = (CubismMotion) motions.get(name);

        if (motion != null) {
            return motion;
        }

        // 懒加载
        motion = loadMotionFromSetting(group, number);
        if (motion != null) {
            motions.put(name, motion);
        }
        return motion;
    }

    /**
     * 播放动作关联的音频文件
     * @param group 动作组名
     * @param number 动作编号
     */
    private void playMotionSound(String group, int number) {
        String voice = modelSetting.getMotionSoundFileName(group, number);
        if (!voice.equals("")) {
            String path = modelHomeDirectory + voice;
            LAppWavFileHandler voicePlayer = new LAppWavFileHandler(path);
            voicePlayer.start();
        }
    }

    // 绘制模型
    public void draw(CubismMatrix44 matrix) {
        if (model == null) {
            return;
        }

        // 组合模型矩阵和投影矩阵
        CubismMatrix44.multiply(
            modelMatrix.getArray(),
            matrix.getArray(),
            matrix.getArray()
        );

        // 设置MVP矩阵并绘制
        this.<CubismRendererAndroid>getRenderer().setMvpMatrix(matrix);
        this.<CubismRendererAndroid>getRenderer().drawModel();
    }

    /**
     * 碰撞检测 - 判断点击是否在模型区域
     * @param hitAreaName 命中区域名称
     * @param x x坐标
     * @param y y坐标
     * @return 是否命中
     */
    public boolean hitTest(final String hitAreaName, float x, float y) {
        // 透明时不检测
        if (opacity < 1) {
            return false;
        }

        // 遍历命中区域
        final int count = modelSetting.getHitAreasCount();
        for (int i = 0; i < count; i++) {
            if (modelSetting.getHitAreaName(i).equals(hitAreaName)) {
                final CubismId drawID = modelSetting.getHitAreaId(i);
                return isHit(drawID, x, y);
            }
        }
        return false;
    }

    /**
     * 设置唇同步开关
     * @param enabled 是否启用
     */
    public void setLipSync(boolean enabled) {
        this.lipSync = enabled;
    }

    /**
     * 设置表情
     * @param expressionID 表情ID
     */
    public void setExpression(final String expressionID) {
        ACubismMotion motion = expressions.get(expressionID);

        if (debugMode) {
            LAppPal.printLog("expression: " + expressionID);
        }

        if (motion != null) {
            expressionManager.startMotionPriority(motion, LAppDefine.Priority.FORCE.getPriority());
        } else {
            if (debugMode) {
                LAppPal.printLog("expression " + expressionID + "is null");
            }
        }
    }

    /**
     * 设置随机表情
     */
    public void setRandomExpression() {
        if (expressions.size() == 0) {
            return;
        }

        Random random = new Random();
        int number = random.nextInt(Integer.MAX_VALUE) % expressions.size();

        int i = 0;
        for (String key : expressions.keySet()) {
            if (i == number) {
                setExpression(key);
                return;
            }
            i++;
        }
    }

    public CubismRenderTargetAndroid getRenderingBuffer() {
        return renderingBuffer;
    }

    /**
     * 检查MOC3文件一致性
     * @param mocFileName MOC3文件名
     * @return 是否一致
     */
    public boolean hasMocConsistencyFromFile(String mocFileName) {
        assert mocFileName != null && !mocFileName.isEmpty();

        String path = mocFileName;
        path = modelHomeDirectory + path;

        byte[] buffer = createBuffer(path);
        boolean consistency = CubismMoc.hasMocConsistency(buffer);

        if (!consistency) {
            CubismDebug.cubismLogInfo("Inconsistent MOC3.");
        } else {
            CubismDebug.cubismLogInfo("Consistent MOC3.");
        }

        return consistency;
    }

    // 从文件创建字节缓冲区
    private static byte[] createBuffer(final String path) {
        if (LAppDefine.DEBUG_LOG_ENABLE) {
            LAppPal.printLog("create buffer: " + path);
        }
        return LAppPal.loadFileAsBytes(path);
    }

    // 从model3.json设置模型
    private void setupModel(ICubismModelSetting setting) {
        modelSetting = setting;

        isUpdated = true;
        isInitialized = false;

        // 1. 加载模型文件 (.moc3)
        {
            String fileName = modelSetting.getModelFileName();
            if (!fileName.equals("")) {
                String path = modelHomeDirectory + fileName;

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("create model: " + modelSetting.getModelFileName());
                }

                byte[] buffer = createBuffer(path);
                loadModel(buffer, mocConsistency);
            }
        }

        // 2. 加载表情文件 (.exp3.json)
        {
            if (modelSetting.getExpressionCount() > 0) {
                final int count = modelSetting.getExpressionCount();

                for (int i = 0; i < count; i++) {
                    String name = modelSetting.getExpressionName(i);
                    String path = modelSetting.getExpressionFileName(i);
                    path = modelHomeDirectory + path;

                    byte[] buffer = createBuffer(path);
                    CubismExpressionMotion motion = loadExpression(buffer);

                    if (motion != null) {
                        expressions.put(name, motion);
                    }
                }
            }
        }

        // 3. 加载物理效果文件
        {
            String path = modelSetting.getPhysicsFileName();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;
                byte[] buffer = createBuffer(modelPath);

                loadPhysics(buffer);
            }
        }

        // 4. 加载姿势文件
        {
            String path = modelSetting.getPoseFileName();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;
                byte[] buffer = createBuffer(modelPath);
                loadPose(buffer);
            }
        }

        // 5. 创建眨眼效果
        if (modelSetting.getEyeBlinkParameterCount() > 0) {
            eyeBlink = CubismEyeBlink.create(modelSetting);
        }

        // 6. 创建呼吸效果
        breath = CubismBreath.create();
        List<CubismBreath.BreathParameterData> breathParameters = new ArrayList<CubismBreath.BreathParameterData>();

        // 设置呼吸参数
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.getId()), 0.5f, 0.5f, 3.2345f, 0.5f));

        breath.setParameters(breathParameters);

        // 7. 加载用户数据
        {
            String path = modelSetting.getUserDataFile();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;
                byte[] buffer = createBuffer(modelPath);
                loadUserData(buffer);
            }
        }


        // 8. 保存眨眼参数ID
        int eyeBlinkIdCount = modelSetting.getEyeBlinkParameterCount();
        for (int i = 0; i < eyeBlinkIdCount; i++) {
            eyeBlinkIds.add(modelSetting.getEyeBlinkParameterId(i));
        }

        // 9. 保存唇同步参数ID
        int lipSyncIdCount = modelSetting.getLipSyncParameterCount();
        for (int i = 0; i < lipSyncIdCount; i++) {
            lipSyncIds.add(modelSetting.getLipSyncParameterId(i));
        }

        if (modelSetting == null || modelMatrix == null) {
            LAppPal.printLog("Failed to setupModel().");
            return;
        }

        // 10. 设置布局
        Map<String, Float> layout = new HashMap<String, Float>();
        if (modelSetting.getLayoutMap(layout)) {
            modelMatrix.setupFromLayout(layout);
        }

        model.saveParameters();

        motionManager.stopAllMotions();

        isUpdated = false;
        isInitialized = true;
    }

    /**
     * 从model3.json设置模型（带进度回调）
     */
    private void setupModelWithCallback(ICubismModelSetting setting, final ModelLoadCallback callback, int totalFiles) {
        modelSetting = setting;
        int currentFile = 1; // model3.json already counted

        isUpdated = true;
        isInitialized = false;

        // 1. 加载模型文件 (.moc3)
        {
            String fileName = modelSetting.getModelFileName();
            if (!fileName.equals("")) {
                String path = modelHomeDirectory + fileName;

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("create model: " + modelSetting.getModelFileName());
                }

                currentFile++;
                notifyProgress(callback, currentFile, totalFiles, fileName);
                byte[] buffer = createBuffer(path);
                loadModel(buffer, mocConsistency);
            }
        }

        // 2. 加载表情文件 (.exp3.json)
        {
            if (modelSetting.getExpressionCount() > 0) {
                final int count = modelSetting.getExpressionCount();

                for (int i = 0; i < count; i++) {
                    String name = modelSetting.getExpressionName(i);
                    String path = modelSetting.getExpressionFileName(i);
                    path = modelHomeDirectory + path;

                    currentFile++;
                    notifyProgress(callback, currentFile, totalFiles, path);
                    byte[] buffer = createBuffer(path);
                    CubismExpressionMotion motion = loadExpression(buffer);

                    if (motion != null) {
                        expressions.put(name, motion);
                    }
                }
            }
        }

        // 3. 加载物理效果文件
        {
            String path = modelSetting.getPhysicsFileName();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;

                currentFile++;
                notifyProgress(callback, currentFile, totalFiles, path);
                byte[] buffer = createBuffer(modelPath);
                loadPhysics(buffer);
            }
        }

        // 4. 加载姿势文件
        {
            String path = modelSetting.getPoseFileName();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;

                currentFile++;
                notifyProgress(callback, currentFile, totalFiles, path);
                byte[] buffer = createBuffer(modelPath);
                loadPose(buffer);
            }
        }

        // 5. 创建眨眼效果
        if (modelSetting.getEyeBlinkParameterCount() > 0) {
            eyeBlink = CubismEyeBlink.create(modelSetting);
        }

        // 6. 创建呼吸效果
        breath = CubismBreath.create();
        List<CubismBreath.BreathParameterData> breathParameters = new ArrayList<CubismBreath.BreathParameterData>();
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.getId()), 0.5f, 0.5f, 3.2345f, 0.5f));
        breath.setParameters(breathParameters);

        // 7. 加载用户数据
        {
            String path = modelSetting.getUserDataFile();
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;

                currentFile++;
                notifyProgress(callback, currentFile, totalFiles, path);
                byte[] buffer = createBuffer(modelPath);
                loadUserData(buffer);
            }
        }

        // 8. 保存眨眼参数ID
        int eyeBlinkIdCount = modelSetting.getEyeBlinkParameterCount();
        for (int i = 0; i < eyeBlinkIdCount; i++) {
            eyeBlinkIds.add(modelSetting.getEyeBlinkParameterId(i));
        }

        // 9. 保存唇同步参数ID
        int lipSyncIdCount = modelSetting.getLipSyncParameterCount();
        for (int i = 0; i < lipSyncIdCount; i++) {
            lipSyncIds.add(modelSetting.getLipSyncParameterId(i));
        }

        if (modelSetting == null || modelMatrix == null) {
            LAppPal.printLog("Failed to setupModelWithCallback().");
            return;
        }

        // 10. 设置布局
        Map<String, Float> layout = new HashMap<String, Float>();
        if (modelSetting.getLayoutMap(layout)) {
            modelMatrix.setupFromLayout(layout);
        }

        // 11. 加载纹理（计入进度）
        for (int modelTextureNumber = 0; modelTextureNumber < modelSetting.getTextureCount(); modelTextureNumber++) {
            if (modelSetting.getTextureFileName(modelTextureNumber).equals("")) {
                continue;
            }
            String texturePath = modelSetting.getTextureFileName(modelTextureNumber);
            texturePath = modelHomeDirectory + texturePath;

            currentFile++;
            notifyProgress(callback, currentFile, totalFiles, texturePath);
        }

        model.saveParameters();
        motionManager.stopAllMotions();

        isUpdated = false;
        isInitialized = true;
    }

    /**
     * 在UI线程通知加载进度
     */
    private void notifyProgress(final ModelLoadCallback callback, final int current, final int total, final String fileName) {
        if (callback == null) {
            return;
        }
        LAppDelegate.getInstance().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onLoadProgress(current, total, fileName);
            }
        });
    }

    /**
     * 从预加载的数据设置模型
     * 必须在GL线程调用
     */
    private void setupModelFromData(ModelResourceData data) {
        modelSetting = data.setting;
        isUpdated = true;
        isInitialized = false;

        // 1. 加载模型文件 (.moc3)
        if (data.moc3Buffer != null) {
            if (LAppDefine.DEBUG_LOG_ENABLE) {
                LAppPal.printLog("create model: " + modelSetting.getModelFileName());
            }
            loadModel(data.moc3Buffer, mocConsistency);
        }

        // 2. 加载表情
        for (int i = 0; i < data.expressionBuffers.size(); i++) {
            CubismExpressionMotion motion = loadExpression(data.expressionBuffers.get(i));
            if (motion != null) {
                expressions.put(data.expressionNames.get(i), motion);
            }
        }

        // 3. 加载物理效果
        if (data.physicsBuffer != null) {
            loadPhysics(data.physicsBuffer);
        }

        // 4. 加载姿势
        if (data.poseBuffer != null) {
            loadPose(data.poseBuffer);
        }

        // 5. 创建眨眼效果
        if (modelSetting.getEyeBlinkParameterCount() > 0) {
            eyeBlink = CubismEyeBlink.create(modelSetting);
        }

        // 6. 创建呼吸效果
        breath = CubismBreath.create();
        List<CubismBreath.BreathParameterData> breathParameters = new ArrayList<CubismBreath.BreathParameterData>();
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleX, 0.0f, 15.0f, 6.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleY, 0.0f, 8.0f, 3.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamAngleZ, 0.0f, 10.0f, 5.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(idParamBodyAngleX, 0.0f, 4.0f, 15.5345f, 0.5f));
        breathParameters.add(new CubismBreath.BreathParameterData(CubismFramework.getIdManager().getId(ParameterId.BREATH.getId()), 0.5f, 0.5f, 3.2345f, 0.5f));
        breath.setParameters(breathParameters);

        // 7. 加载用户数据
        if (data.userDataBuffer != null) {
            loadUserData(data.userDataBuffer);
        }

        // 8. 保存眨眼参数ID
        int eyeBlinkIdCount = modelSetting.getEyeBlinkParameterCount();
        for (int i = 0; i < eyeBlinkIdCount; i++) {
            eyeBlinkIds.add(modelSetting.getEyeBlinkParameterId(i));
        }

        // 9. 保存唇同步参数ID
        int lipSyncIdCount = modelSetting.getLipSyncParameterCount();
        for (int i = 0; i < lipSyncIdCount; i++) {
            lipSyncIds.add(modelSetting.getLipSyncParameterId(i));
        }

        if (modelSetting == null || modelMatrix == null) {
            LAppPal.printLog("Failed to setupModelFromData().");
            return;
        }

        // 10. 设置布局
        Map<String, Float> layout = new HashMap<String, Float>();
        if (modelSetting.getLayoutMap(layout)) {
            modelMatrix.setupFromLayout(layout);
        }

        model.saveParameters();
        motionManager.stopAllMotions();

        isUpdated = false;
        isInitialized = true;
    }

    /**
     * 预加载动作组
     * @param group 动作组名
     */
    private void preLoadMotionGroup(final String group) {
        final int count = modelSetting.getMotionCount(group);

        for (int i = 0; i < count; i++) {
            // 构建动作名称 如: idle_0
            String name = group + "_" + i;

            String path = modelSetting.getMotionFileName(group, i);
            if (!path.equals("")) {
                String modelPath = modelHomeDirectory + path;

                if (debugMode) {
                    LAppPal.printLog("load motion: " + path + "==>[" + group + "_" + i + "]");
                }

                byte[] buffer;
                buffer = createBuffer(modelPath);

                // 加载动作
                CubismMotion tmp = loadMotion(buffer, motionConsistency);
                if (tmp == null) {
                    continue;
                }

                // 设置淡入时间
                final float fadeInTime = modelSetting.getMotionFadeInTimeValue(group, i);
                if (fadeInTime != -1.0f) {
                    tmp.setFadeInTime(fadeInTime);
                }

                // 设置淡出时间
                final float fadeOutTime = modelSetting.getMotionFadeOutTimeValue(group, i);
                if (fadeOutTime != -1.0f) {
                    tmp.setFadeOutTime(fadeOutTime);
                }

                // 设置效果ID
                tmp.setEffectIds(eyeBlinkIds, lipSyncIds);
                motions.put(name, tmp);
            }
        }
    }

    /**
     * 加载纹理到OpenGL
     */
    private void setupTextures() {
        for (int modelTextureNumber = 0; modelTextureNumber < modelSetting.getTextureCount(); modelTextureNumber++) {
            // 跳过空纹理名
            if (modelSetting.getTextureFileName(modelTextureNumber).equals("")) {
                continue;
            }

            // 加载纹理
            String texturePath = modelSetting.getTextureFileName(modelTextureNumber);
            texturePath = modelHomeDirectory + texturePath;

            LAppTextureManager.TextureInfo texture =
                LAppDelegate.getInstance()
                            .getTextureManager()
                            .createTextureFromPngFile(texturePath);
            final int glTextureNumber = texture.id;

            // 绑定纹理
            this.<CubismRendererAndroid>getRenderer().bindTexture(modelTextureNumber, glTextureNumber);

            // 设置是否预乘Alpha
            if (LAppDefine.PREMULTIPLIED_ALPHA_ENABLE) {
                this.<CubismRendererAndroid>getRenderer().isPremultipliedAlpha(true);
            } else {
                this.<CubismRendererAndroid>getRenderer().isPremultipliedAlpha(false);
            }
        }
    }

    private ICubismModelSetting modelSetting;

    public ICubismModelSetting getModelSetting() {
        return modelSetting;
    }

    /**
     * 模型所在目录
     */
    private String modelHomeDirectory;
    /**
     * 累计时间（秒）
     */
    private float userTimeSeconds;

    /**
     * 眨眼参数ID列表
     */
    private final List<CubismId> eyeBlinkIds = new ArrayList<CubismId>();
    /**
     * 唇同步参数ID列表
     */
    private final List<CubismId> lipSyncIds = new ArrayList<CubismId>();
    /**
     * 动作缓存
     */
    private final Map<String, ACubismMotion> motions = new ConcurrentHashMap<String, ACubismMotion>();
    /**
     * 表情缓存
     */
    private final Map<String, ACubismMotion> expressions = new HashMap<String, ACubismMotion>();

    public Map<String, ACubismMotion> getExpressions() {
        return expressions;
    }

    public Map<String, ACubismMotion> getMotions() {
        return motions;
    }

    /**
     * 参数ID: 头部X角度
     */
    private final CubismId idParamAngleX;
    /**
     * 参数ID: 头部Y角度
     */
    private final CubismId idParamAngleY;
    /**
     * 参数ID: 头部Z角度
     */
    private final CubismId idParamAngleZ;
    /**
     * 参数ID: 身体X角度
     */
    private final CubismId idParamBodyAngleX;
    /**
     * 参数ID: 眼球X位置
     */
    private final CubismId idParamEyeBallX;
    /**
     * 参数ID: 眼球Y位置
     */
    private final CubismId idParamEyeBallY;

    /**
     * 离屏渲染缓冲
     */
    private final CubismRenderTargetAndroid renderingBuffer = new CubismRenderTargetAndroid();

    /**
     * 加载状态标志 - 防止并发加载
     */
    private volatile boolean isLoading = false;
}
