/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

import android.util.Log;

import com.live2d.sdk.cubism.core.ICubismLogger;
import com.live2d.sdk.cubism.framework.ICubismLoadFileFunction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * 工具类 - 提供日志、文件加载、时间管理功能
 */
public class LAppPal {

    /**
     * 日志输出接口实现 - 注册到Cubism Framework
     */
    public static class PrintLogFunction implements ICubismLogger {
        @Override
        public void print(String message) {
            Log.d(TAG, message);  // 输出到Logcat
        }
    }

    /**
     * 文件加载接口实现 - 注册到Cubism Framework
     */
    public static class LoadFileFunction implements ICubismLoadFileFunction {
        @Override
        public byte[] load(String path) {
            return LAppPal.loadFileAsBytes(path);
        }
    }

    // 将应用移到后台
    public static void moveTaskToBack() {
        LAppDelegate.getInstance().getActivity().moveTaskToBack(true);
    }

    // 更新系统时间 - 计算deltaTime
    public static void updateTime() {
        s_currentFrame = getSystemNanoTime();
        _deltaNanoTime = s_currentFrame - _lastNanoTime;
        _lastNanoTime = s_currentFrame;
    }

    // 从assets加载文件为字节数组
    public static byte[] loadFileAsBytes(final String path) {
        InputStream fileData = null;
        try {
            fileData = LAppDelegate.getInstance().getActivity().getAssets().open(path);

            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);

            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();

            if (LAppDefine.DEBUG_LOG_ENABLE) {
                printLog("File open error.");
            }

            return new byte[0];
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();

                if (LAppDefine.DEBUG_LOG_ENABLE) {
                    printLog("File open error.");
                }
            }
        }
    }

    // 获取帧间隔时间（秒）
    public static float getDeltaTime() {
        // 纳秒转秒
        return (float) (_deltaNanoTime / 1000000000.0f);
    }

    /**
     * 日志输出
     * @param message 日志消息
     */
    public static void printLog(String message) {
        Log.d(TAG, message);
    }

    // 获取系统纳秒时间
    private static long getSystemNanoTime() {
        return System.nanoTime();
    }

    private static double s_currentFrame;
    private static double _lastNanoTime;
    private static double _deltaNanoTime;

    private static final String TAG = "[APP]";

    /**
     * 后台线程池用于异步文件加载
     */
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 单个文件加载回调接口
     */
    public interface FileLoadCallback {
        void onLoad(byte[] data);
        void onError(String error);
    }

    /**
     * 多个文件加载回调接口（带进度）
     */
    public interface FilesLoadCallback {
        void onProgress(int current, int total, String currentPath);
        void onComplete(List<byte[]> dataList);
        void onError(String error);
    }

    /**
     * 异步加载单个文件
     * @param path 文件路径（相对于assets）
     * @param callback 加载回调
     */
    public static void loadFileAsync(final String path, final FileLoadCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] data = loadFileAsBytes(path);
                    if (data != null && data.length > 0) {
                        callback.onLoad(data);
                    } else {
                        callback.onError("Failed to load file: " + path);
                    }
                } catch (Exception e) {
                    callback.onError("Error loading file " + path + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * 异步加载多个文件（带进度通知）
     * @param paths 文件路径列表
     * @param callback 加载回调
     */
    public static void loadFilesAsync(final List<String> paths, final FilesLoadCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<byte[]> results = new java.util.ArrayList<byte[]>();
                    int total = paths.size();

                    for (int i = 0; i < total; i++) {
                        String path = paths.get(i);
                        callback.onProgress(i + 1, total, path);

                        byte[] data = loadFileAsBytes(path);
                        if (data == null || data.length == 0) {
                            callback.onError("Failed to load file: " + path);
                            return;
                        }
                        results.add(data);
                    }

                    callback.onComplete(results);
                } catch (Exception e) {
                    callback.onError("Error loading files: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 关闭线程池（在应用退出时调用）
     */
    public static void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private LAppPal() {}  // 私有构造函数
}
