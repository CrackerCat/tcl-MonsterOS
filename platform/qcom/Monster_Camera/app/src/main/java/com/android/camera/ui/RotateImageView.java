/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.android.camera.debug.Log;
import com.android.camera.test.TestUtils; // MODIFIED by wenhua.tu, 2016-08-11,BUG-2710178
import com.android.camera.util.LockUtils;

/**
 * A @{code ImageView} which can rotate it's content.
 */
public class RotateImageView extends TwoStateImageView implements Rotatable ,Lockable{

    @SuppressWarnings("unused")
    private static final Log.Tag TAG = new Log.Tag("RotateImageView");

    private static final int ANIMATION_SPEED = 270; // 270 deg/sec

    protected int mCurrentDegree = 0; // [0, 359]// MODIFIED by sichao.hu, 2016-03-22, BUG-1027573
    private int mStartDegree = 0;
    private int mTargetDegree = 0;

    private boolean mClockwise = false, mEnableAnimation = true;

    private long mAnimationStartTime = 0;
    private long mAnimationEndTime = 0;
    private LockUtils.Lock mMultiLock;
    private TestUtils.TestCallBack mTestCallBack; // MODIFIED by wenhua.tu, 2016-08-11,BUG-2710178

    protected boolean mWaitForTouchDown=false;
    protected OnTouchListener mOnTouchListener;
    protected OnUnhandledClickListener mOnUnhandledClickListener;

    public RotateImageView(Context context){
        super(context);
        mMultiLock= LockUtils.getInstance().generateMultiLock(LockUtils.LockType.MULTILOCK);
    }

    public RotateImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMultiLock= LockUtils.getInstance().generateMultiLock(LockUtils.LockType.MULTILOCK);
    }

    public RotateImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context);
        mMultiLock= LockUtils.getInstance().generateMultiLock(LockUtils.LockType.MULTILOCK);
    }

    protected int getDegree() {
        return mTargetDegree;
    }

    // Rotate the view counter-clockwise
    @Override
    public void setOrientation(int degree, boolean animation) {
        mEnableAnimation = animation;
        // make sure in the range of [0, 359]
        degree = degree >= 0 ? degree % 360 : degree % 360 + 360;
        if (degree == mTargetDegree) return;

        mTargetDegree = degree;
        if (mEnableAnimation) {
            mStartDegree = mCurrentDegree;
            mAnimationStartTime = AnimationUtils.currentAnimationTimeMillis();

            int diff = mTargetDegree - mCurrentDegree;
            diff = diff >= 0 ? diff : 360 + diff; // make it in range [0, 359]

            // Make it in range [-179, 180]. That's the shorted distance between the
            // two angles
            diff = diff > 180 ? diff - 360 : diff;

            mClockwise = diff >= 0;
            mAnimationEndTime = mAnimationStartTime
                    + Math.abs(diff) * 1000 / ANIMATION_SPEED;
        } else {
            mCurrentDegree = mTargetDegree;
        }

        invalidate();
    }

    protected boolean needSuperDrawable(){
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(!needSuperDrawable()){
            super.onDraw(canvas);
            return;
        }
        Drawable drawable = getDrawable();
        if (drawable == null) return;

        Rect bounds = drawable.getBounds();
        int w = bounds.right - bounds.left;
        int h = bounds.bottom - bounds.top;

        if (w == 0 || h == 0) return; // nothing to draw

        if (mCurrentDegree != mTargetDegree) {
            long time = AnimationUtils.currentAnimationTimeMillis();
            if (time < mAnimationEndTime) {
                int deltaTime = (int)(time - mAnimationStartTime);
                int degree = mStartDegree + ANIMATION_SPEED
                        * (mClockwise ? deltaTime : -deltaTime) / 1000;
                degree = degree >= 0 ? degree % 360 : degree % 360 + 360;
                mCurrentDegree = degree;
                invalidate();
            } else {
                mCurrentDegree = mTargetDegree;
            }
        }

        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getPaddingRight();
        int bottom = getPaddingBottom();
        int width = getWidth() - left - right;
        int height = getHeight() - top - bottom;

        int saveCount = canvas.getSaveCount();

        // Scale down the image first if required.
        if ((getScaleType() == ImageView.ScaleType.FIT_CENTER) &&
                ((width < w) || (height < h))) {
            float ratio = Math.min((float) width / w, (float) height / h);
            canvas.scale(ratio, ratio, width / 2.0f, height / 2.0f);
        }
        canvas.translate(left + width / 2, top + height / 2);
        canvas.rotate(-mCurrentDegree);
        canvas.translate(-w / 2, -h / 2);
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    private Bitmap mThumb;
    private Drawable[] mThumbs;
    private TransitionDrawable mThumbTransition;

    protected boolean needTranslation(){
        return true;
    }

    public void setBitmap(Bitmap bitmap) {
        // Make sure uri and original are consistently both null or both
        // non-null.
        if (bitmap == null) {
            mThumb = null;
            mThumbs = null;
            setImageDrawable(null);
            setVisibility(GONE);
            return;
        }

        LayoutParams param = getLayoutParams();
        final int miniThumbWidth = param.width
                - getPaddingLeft() - getPaddingRight();
        final int miniThumbHeight = param.height
                - getPaddingTop() - getPaddingBottom();
        mThumb = ThumbnailUtils.extractThumbnail(
                bitmap, miniThumbWidth, miniThumbHeight);
        Drawable drawable;
        if (mThumbs == null || !mEnableAnimation||!needTranslation()) {
            mThumbs = new Drawable[2];
            mThumbs[1] = new BitmapDrawable(getContext().getResources(), mThumb);
            setImageDrawable(mThumbs[1]);
        } else {
            mThumbs[0] = mThumbs[1];
            mThumbs[1] = new BitmapDrawable(getContext().getResources(), mThumb);
            mThumbTransition = new TransitionDrawable(mThumbs);
            setImageDrawable(mThumbTransition);
            mThumbTransition.startTransition(500);
        }
        setVisibility(VISIBLE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG,"onTouchEvent  "+isLocked()+",  "+event.getAction());
        if(isLocked()){
            MotionEvent newEvent = MotionEvent.obtain(event);
            newEvent.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(newEvent);
            newEvent.recycle();
            return true;
        }else{
            Log.e(TAG,"RotateImageView isLocked , instance is "+this.toString());
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void lockSelf() {
        mMultiLock.aquireLock(this.hashCode());
    }

    @Override
    public void unLockSelf() {
        mMultiLock.unlockWithToken(this.hashCode());
    }

    @Override
    public boolean isLocked() {
        return mMultiLock.isLocked();
    }

    @Override
    public int lock() {
        return mMultiLock.aquireLock();
    }

    @Override
    public boolean unlockWithToken(int token) {
        return mMultiLock.unlockWithToken(token);
    }

    /* MODIFIED-BEGIN by wenhua.tu, 2016-08-11,BUG-2710178*/
    public TestUtils.TestCallBack getTestCallBack() {
        if (TestUtils.IS_TEST) {
            return mTestCallBack;
        }
        return null;
    }

    public void setTestCallBack(TestUtils.TestCallBack mTestCallBack) {
        if (TestUtils.IS_TEST) {
            this.mTestCallBack = mTestCallBack;
        }
    }
    /* MODIFIED-END by wenhua.tu,BUG-2710178*/

    /*
    * Listener interface for disabled button click.
    */
    public interface OnUnhandledClickListener {
        /*
         * @param view the MultiToggleImageButton that received the touch event
         * @param state the new state the button is in
         */
        public abstract void unhandledClick();
    }

    public interface OnTouchListener {
        public void onTouchDown();
        public void onTouchUp();
    }

    public void setOnTouchListener(OnTouchListener listener) {
        mOnTouchListener =listener;
    }

    public void setOnUnhandledClickListener(OnUnhandledClickListener onUnhandledClickListener) {
        mOnUnhandledClickListener = onUnhandledClickListener;
    }
    protected boolean isPointInView(float localX, float localY) {
        return localX >= 0 && localX < getWidth()
                && localY >= 0 && localY < getHeight();
    }
}