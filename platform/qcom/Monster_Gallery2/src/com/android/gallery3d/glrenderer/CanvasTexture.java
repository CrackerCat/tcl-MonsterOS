/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.glrenderer;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;

// CanvasTexture is a texture whose content is the drawing on a Canvas.
// The subclasses should override onDraw() to draw on the bitmap.
// By default CanvasTexture is not opaque.
public abstract class CanvasTexture extends UploadedTexture {
    protected Canvas mCanvas;
    private final Config mConfig;

    public CanvasTexture(int width, int height) {
        mConfig = Config.ARGB_8888;
        setSize(width, height);
        setOpaque(false);
    }

    @SuppressLint("WrongCall") //SQF add this on 2016.08.08
    @Override
    protected Bitmap onGetBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, mConfig);
        mCanvas = new Canvas(bitmap);
        onDraw(mCanvas, bitmap);
        return bitmap;
    }

    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
        if (!inFinalizer()) {
            bitmap.recycle();
        }
    }

    // TCL ShenQianfeng Begin on 2016.08.08
    @SuppressLint("WrongCall")
    @Override
    protected void refreshBitmap(Bitmap bitmap) {
        onDraw(mCanvas, bitmap);
    }
    // TCL ShenQianfeng End on 2016.08.08
    
    abstract protected void onDraw(Canvas canvas, Bitmap backing);
}
