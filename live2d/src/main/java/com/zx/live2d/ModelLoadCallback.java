/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.zx.live2d;

/**
 * 模型加载状态回调接口
 * 用于通知加载进度、完成和错误状态
 */
public interface ModelLoadCallback {

    /**
     * 加载进度回调
     * @param current 当前已加载文件数
     * @param total 总文件数
     * @param currentFile 当前正在加载的文件名
     */
    void onLoadProgress(int current, int total, String currentFile);

    /**
     * 加载完成回调
     */
    void onLoadComplete();

    /**
     * 加载错误回调
     * @param error 错误信息
     */
    void onLoadError(String error);
}