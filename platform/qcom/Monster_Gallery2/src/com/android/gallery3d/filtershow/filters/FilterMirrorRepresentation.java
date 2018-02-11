/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.filters;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorMirror;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;
import com.android.gallery3d.filtershow.imageshow.GeometryMathUtils;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.util.LogUtil;

import java.io.IOException;
import java.util.ArrayList;

public class FilterMirrorRepresentation extends FilterRepresentation {
    public static final String SERIALIZATION_NAME = "MIRROR";
    private static final String SERIALIZATION_MIRROR_VALUE = "value";
    // TCL ShenQianfeng Begin on 2016.08.25
    private static final String SERIALIZATION_IS_HORIZONTAL_VALUE = "is_horizontal";
    // TCL ShenQianfeng End on 2016.08.25
    private static final String TAG = FilterMirrorRepresentation.class.getSimpleName();

    private Mirror mMirror; //original not static, ShenQianfeng modify this to static on 2016.08.26
    
    // TCL ShenQianfeng Begin on 2016.08.25
    // if true mMirror should be HORIZONTAL, otherwise mMirror should be VERTICAL
    private boolean mIsHorizontal; 
    // TCL ShenQianfeng End on 2016.08.25

    public enum Mirror {
        NONE('N'), /*ORIGINAL('O'),*/ VERTICAL('V'), HORIZONTAL('H'), /*BOTH('B')*/;
        char mValue;

        private Mirror(char value) {
            mValue = value;
        }

        public char value() {
            return mValue;
        }

        public static Mirror fromValue(char value) {
            switch (value) {
                case 'N':
                    return NONE;
                case 'V':
                    return VERTICAL;
                case 'H':
                    return HORIZONTAL;
                    /*
                case 'B':
                    return BOTH;*/
                default:
                    return null;
            }
        }
    }

    private FilterMirrorRepresentation(Mirror mirror) {
        super(SERIALIZATION_NAME);
        setSerializationName(SERIALIZATION_NAME);
        setShowParameterValue(false);
        setFilterClass(FilterMirrorRepresentation.class);
        setFilterType(FilterRepresentation.TYPE_GEOMETRY);
        setSupportsPartialRendering(true);
        setTextId(R.string.mirror);
        setEditorId(ImageOnlyEditor.ID);
        setMirror(mirror);
    }

    public FilterMirrorRepresentation(FilterMirrorRepresentation m) {
        this(m.getMirror());
        setName(m.getName());
        // TCL ShenQianfeng Begin on 2016.08.25
        mIsHorizontal = m.getIsHorizontal();
        // TCL ShenQianfeng End on 2016.08.25
    }

    public FilterMirrorRepresentation() {
        this(getNil());
    }
    
    // TCL ShenQianfeng Begin on 2016.08.25
    public FilterMirrorRepresentation(boolean isHorizontal) {
        this();
        mIsHorizontal = isHorizontal;
    }
    
    /**
     * getIsHorizontal
     */
    public boolean getIsHorizontal() {
        return mIsHorizontal;
    }
    
    // TCL ShenQianfeng End on 2016.08.25

    @Override
    public boolean equals(FilterRepresentation rep) {
        if (!(rep instanceof FilterMirrorRepresentation)) {
            return false;
        }
        FilterMirrorRepresentation mirror = (FilterMirrorRepresentation) rep;
        if (mMirror != mirror.mMirror) {
            return false;
        }
        // TCL ShenQianfeng Begin on 2016.08.25
        if(mIsHorizontal != mirror.getIsHorizontal()) {
            return false;
        }
        // TCL ShenQianfeng End on 2016.08.25
        return true;
    }

    public Mirror getMirror() {
        return mMirror;
    }

    public void set(FilterMirrorRepresentation r) {
        mMirror = r.mMirror;
    }

    public void setMirror(Mirror mirror) {
        if (mirror == null) {
            throw new IllegalArgumentException("Argument to setMirror is null");
        }
        mMirror = mirror;
    }

    public boolean isHorizontal() {
        // TCL ShenQianfeng Begin on 2016.08.25
        // Original:
        /*
         if (mMirror == Mirror.BOTH
                || mMirror == Mirror.HORIZONTAL) {
            return true;
        }
        return false;
        */
        // Modify To:
        return mIsHorizontal;
        // TCL ShenQianfeng End on 2016.08.25
        
    }

    public boolean isVertical() {
        // TCL ShenQianfeng Begin on 2016.08.25
        // Original:
        /*
        if (mMirror == Mirror.BOTH
                || mMirror == Mirror.VERTICAL) {
            return true;
        }
        return false;
        */
        // Modify To:
        return ! mIsHorizontal;
        // TCL ShenQianfeng End on 2016.08.25
    }
    
    // TCL ShenQianfeng Begin on 2016.08.25
    
    public void setIsHorizontal(boolean isHorizontal) {
        mIsHorizontal = isHorizontal;
    }
    
    public void transform() {
        if (mIsHorizontal) {
                mMirror = Mirror.HORIZONTAL;
        } else {
               mMirror = Mirror.VERTICAL;
        }
    }
    
    // TCL ShenQianfeng End on 2016.08.25
    
    /*
    public void cycle() {
        switch (mMirror) {
            case NONE:
                mMirror = Mirror.HORIZONTAL;
                break;
            case HORIZONTAL:
                mMirror = Mirror.BOTH;
                break;
            case BOTH:
                mMirror = Mirror.VERTICAL;
                break;
            case VERTICAL:
                mMirror = Mirror.NONE;
                break;
        }
    }
    */

    @Override
    public boolean allowsSingleInstanceOnly() {
        // TCL ShenQianfeng Begin on 2016.08.25
        // Original:
        // return true;
        // Modify To:
        return false;
        // TCL ShenQianfeng End on 2016.08.25
        
    }

    @Override
    public FilterRepresentation copy() {
        return new FilterMirrorRepresentation(this);
    }

    @Override
    protected void copyAllParameters(FilterRepresentation representation) {
        if (!(representation instanceof FilterMirrorRepresentation)) {
            throw new IllegalArgumentException("calling copyAllParameters with incompatible types!");
        }
        super.copyAllParameters(representation);
        representation.useParametersFrom(this);
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        if (!(a instanceof FilterMirrorRepresentation)) {
            throw new IllegalArgumentException("calling useParametersFrom with incompatible types!");
        }
        setMirror(((FilterMirrorRepresentation) a).getMirror());
        // TCL ShenQianfeng Begin on 2016.08.25
        setIsHorizontal(((FilterMirrorRepresentation) a).getIsHorizontal());
        // TCL ShenQianfeng End on 2016.08.25
        
    }

    @Override
    public boolean isNil() {
        return mMirror == getNil();
    }

    public static Mirror getNil() {
        return Mirror.NONE;
    }

    @Override
    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name(SERIALIZATION_MIRROR_VALUE).value(mMirror.value());
        // TCL ShenQianfeng Begin on 2016.08.25
        writer.name(SERIALIZATION_IS_HORIZONTAL_VALUE).value(mIsHorizontal ? 1 : 0);
        // TCL ShenQianfeng End on 2016.08.25
        writer.endObject();
    }

    @Override
    public void deSerializeRepresentation(JsonReader reader) throws IOException {
        boolean unset = true;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (SERIALIZATION_MIRROR_VALUE.equals(name)) {
                Mirror r = Mirror.fromValue((char) reader.nextInt());
                if (r != null) {
                    setMirror(r);
                    unset = false;
                }
            } 
            // TCL ShenQianfeng Begin on 2016.08.25
            else if(SERIALIZATION_IS_HORIZONTAL_VALUE.equals(name)) {
                mIsHorizontal = 1 == reader.nextInt();
            }
            // TCL ShenQianfeng End on 2016.08.25
            else {
                reader.skipValue();
            }
            
            
        }
        if (unset) {
            Log.w(TAG, "WARNING: bad value when deserializing " + SERIALIZATION_NAME);
        }
        reader.endObject();
    }
}
