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

package com.android.camera;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ComboPreferences implements
        SharedPreferences,
        OnSharedPreferenceChangeListener {
    // TODO: Remove this WeakHashMap in the camera code refactoring
    private static final WeakHashMap<Context, ComboPreferences> sMap =
            new WeakHashMap<>();
    private SharedPreferences mPrefGlobal;  // global preferences
    private SharedPreferences mPrefLocal;  // per-camera preferences
    private String mPackageName;
    private CopyOnWriteArrayList<OnSharedPreferenceChangeListener> mListeners;

    public ComboPreferences(Context context) {
        mPackageName = context.getPackageName();
        mPrefGlobal = context.getSharedPreferences(
                getGlobalSharedPreferencesName(context), Context.MODE_PRIVATE);
        mPrefGlobal.registerOnSharedPreferenceChangeListener(this);

        synchronized (sMap) {
            sMap.put(context, this);
        }
        mListeners = new CopyOnWriteArrayList<>();

        // The global preferences was previously stored in the default
        // shared preferences file. They should be stored in the camera-specific
        // shared preferences file so we can backup them solely.
        SharedPreferences oldprefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (!mPrefGlobal.contains(CameraSettings.KEY_VERSION)
                && oldprefs.contains(CameraSettings.KEY_VERSION)) {
            moveGlobalPrefsFrom(oldprefs);
        }
    }

    public static ComboPreferences get(Context context) {
        synchronized (sMap) {
            return sMap.get(context);
        }
    }

    public static String getLocalSharedPreferencesName(
            Context context, int cameraId) {
        return context.getPackageName() + "_preferences_" + cameraId;
    }

    public static String getGlobalSharedPreferencesName(Context context) {
        return context.getPackageName() + "_preferences_camera";
    }

    public static String[] getSharedPreferencesNames(Context context) {
        int numOfCameras = CameraHolder.instance().getNumberOfCameras();
        String prefNames[] = new String[numOfCameras + 1];
        prefNames[0] = getGlobalSharedPreferencesName(context);
        for (int i = 0; i < numOfCameras; i++) {
            prefNames[i + 1] = getLocalSharedPreferencesName(context, i);
        }
        return prefNames;
    }

    private static boolean isGlobal(String key) {
        return key.equals(CameraSettings.KEY_CAMERA_ID)
                || key.equals(CameraSettings.KEY_RECORD_LOCATION)
                || key.equals(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN)
                || key.equals(CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN)
                || key.equals(CameraSettings.KEY_VIDEO_EFFECT)
                || key.equals(CameraSettings.KEY_TIMER)
                || key.equals(CameraSettings.KEY_TIMER_SOUND_EFFECTS)
                || key.equals(CameraSettings.KEY_PHOTOSPHERE_PICTURESIZE)
                || key.equals(CameraSettings.KEY_CAMERA_SAVEPATH)
                || key.equals(CameraSettings.KEY_GRID)
                || key.equals(SettingsManager.KEY_CAMERA_ID)
                || key.equals(SettingsManager.KEY_MONO_ONLY)
                || key.equals(SettingsManager.KEY_MONO_PREVIEW)
                || key.equals(SettingsManager.KEY_CLEARSIGHT);
    }

    private void movePrefFrom(
            Map<String, ?> m, String key, SharedPreferences src) {
        if (m.containsKey(key)) {
            Object v = m.get(key);
            if (v instanceof String) {
                mPrefGlobal.edit().putString(key, (String) v).apply();
            } else if (v instanceof Integer) {
                mPrefGlobal.edit().putInt(key, (Integer) v).apply();
            } else if (v instanceof Long) {
                mPrefGlobal.edit().putLong(key, (Long) v).apply();
            } else if (v instanceof Float) {
                mPrefGlobal.edit().putFloat(key, (Float) v).apply();
            } else if (v instanceof Boolean) {
                mPrefGlobal.edit().putBoolean(key, (Boolean) v).apply();
            }
            src.edit().remove(key).apply();
        }
    }

    private void moveGlobalPrefsFrom(SharedPreferences src) {
        Map<String, ?> prefMap = src.getAll();
        movePrefFrom(prefMap, CameraSettings.KEY_VERSION, src);
        movePrefFrom(prefMap, CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL, src);
        movePrefFrom(prefMap, CameraSettings.KEY_CAMERA_ID, src);
        movePrefFrom(prefMap, CameraSettings.KEY_RECORD_LOCATION, src);
        movePrefFrom(prefMap, CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, src);
        movePrefFrom(prefMap, CameraSettings.KEY_VIDEO_FIRST_USE_HINT_SHOWN, src);
        movePrefFrom(prefMap, CameraSettings.KEY_VIDEO_EFFECT, src);
        movePrefFrom(prefMap, CameraSettings.KEY_CAMERA_SAVEPATH, src);
    }

    // Sets the camera id and reads its preferences. Each camera has its own
    // preferences.
    public void setLocalId(Context context, int cameraId) {
        String prefName = getLocalSharedPreferencesName(context, cameraId);
        if (mPrefLocal != null) {
            mPrefLocal.unregisterOnSharedPreferenceChangeListener(this);
        }
        mPrefLocal = context.getSharedPreferences(
                prefName, Context.MODE_PRIVATE);
        mPrefLocal.registerOnSharedPreferenceChangeListener(this);
    }

    public SharedPreferences getGlobal() {
        return mPrefGlobal;
    }

    public SharedPreferences getLocal() {
        return mPrefLocal;
    }

    @Override
    public Map<String, ?> getAll() {
        throw new UnsupportedOperationException(); // Can be implemented if needed.
    }

    @Override
    public String getString(String key, String defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getString(key, defValue);
        } else {
            return mPrefLocal.getString(key, defValue);
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getInt(key, defValue);
        } else {
            return mPrefLocal.getInt(key, defValue);
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getLong(key, defValue);
        } else {
            return mPrefLocal.getLong(key, defValue);
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getFloat(key, defValue);
        } else {
            return mPrefLocal.getFloat(key, defValue);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (isGlobal(key) || !mPrefLocal.contains(key)) {
            return mPrefGlobal.getBoolean(key, defValue);
        } else {
            return mPrefLocal.getBoolean(key, defValue);
        }
    }

    // This method is not used.
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(String key) {
        return mPrefLocal.contains(key) || mPrefGlobal.contains(key);
    }

    // Note the remove() and clear() of the returned Editor may not work as
    // expected because it doesn't touch the global preferences at all.
    @Override
    public Editor edit() {
        return new MyEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        for (OnSharedPreferenceChangeListener listener : mListeners) {
            listener.onSharedPreferenceChanged(this, key);
        }
        BackupManager.dataChanged(mPackageName);
    }

    private class MyEditor implements Editor {
        private Editor mEditorGlobal;
        private Editor mEditorLocal;

        MyEditor() {
            mEditorGlobal = mPrefGlobal.edit();
            mEditorLocal = mPrefLocal.edit();
        }

        @Override
        public boolean commit() {
            boolean result1 = mEditorGlobal.commit();
            boolean result2 = mEditorLocal.commit();
            return result1 && result2;
        }

        @Override
        public void apply() {
            mEditorGlobal.apply();
            mEditorLocal.apply();
        }

        // Note: clear() and remove() affects both local and global preferences.
        @Override
        public Editor clear() {
            mEditorGlobal.clear();
            mEditorLocal.clear();
            return this;
        }

        @Override
        public Editor remove(String key) {
            mEditorGlobal.remove(key);
            mEditorLocal.remove(key);
            return this;
        }

        @Override
        public Editor putString(String key, String value) {
            if (isGlobal(key)) {
                mEditorGlobal.putString(key, value);
            } else {
                mEditorLocal.putString(key, value);
            }
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            if (isGlobal(key)) {
                mEditorGlobal.putInt(key, value);
            } else {
                mEditorLocal.putInt(key, value);
            }
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            if (isGlobal(key)) {
                mEditorGlobal.putLong(key, value);
            } else {
                mEditorLocal.putLong(key, value);
            }
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            if (isGlobal(key)) {
                mEditorGlobal.putFloat(key, value);
            } else {
                mEditorLocal.putFloat(key, value);
            }
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            if (isGlobal(key)) {
                mEditorGlobal.putBoolean(key, value);
            } else {
                mEditorLocal.putBoolean(key, value);
            }
            return this;
        }

        // This method is not used.
        @Override
        public Editor putStringSet(String key, Set<String> values) {
            throw new UnsupportedOperationException();
        }
    }
}
