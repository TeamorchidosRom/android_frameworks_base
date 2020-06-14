/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import static com.android.systemui.tuner.LockscreenFragment.LOCKSCREEN_LEFT_BUTTON;
import static com.android.systemui.tuner.LockscreenFragment.LOCKSCREEN_SHORTCUT_NONE;
import static com.android.systemui.tuner.LockscreenFragment.LOCKSCREEN_SHORTCUT_CAMERA;
import static com.android.systemui.tuner.LockscreenFragment.LOCKSCREEN_SHORTCUT_TORCH;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.tuner.ShortcutParser.Shortcut;
import com.android.systemui.tuner.TunerService.Tunable;
import com.android.systemui.statusbar.policy.FlashlightController;

import java.util.ArrayList;
import java.util.List;

public class ShortcutPicker extends PreferenceFragment implements Tunable {

    final static String TAG = "Tuner/ShortcutPicker";
    
    private final ArrayList<SelectablePreference> mSelectablePreferences = new ArrayList<>();
    private CustomPreference mDefaultPreferenceLeft;
    private CustomPreference mDefaultPreferenceRight;
    private CustomPreference mNonePreference;
    private String mKey;
    private TunerService mTunerService;
    private FlashlightController mFlashlightController;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.setOrderingAsAdded(true);
        PreferenceCategory otherApps = new PreferenceCategory(context);
        otherApps.setTitle(R.string.tuner_other_apps);
		
		mFlashlightController = Dependency.get(FlashlightController.class);
		
        // True "none" preference
        mNonePreference = new CustomPreference(context, LOCKSCREEN_SHORTCUT_NONE);
        mSelectablePreferences.add(mNonePreference);
        mNonePreference.setTitle(R.string.lockscreen_none);
        mNonePreference.setIcon(R.drawable.ic_remove_circle);
        screen.addPreference(mNonePreference);

         // Default shortcuts (torch and camera)
        mKey = getArguments().getString(ARG_PREFERENCE_ROOT);
        if (LOCKSCREEN_LEFT_BUTTON.equals(mKey)) {
            if (hasCameraFlash()) {
                // True "torch" preferenc
                mDefaultPreferenceLeft = new CustomPreference(context, LOCKSCREEN_SHORTCUT_TORCH);
                mSelectablePreferences.add(mDefaultPreferenceLeft);
                mDefaultPreferenceLeft.setTitle(R.string.lockscreen_default);
                //mDefaultPreferenceLeft.setIcon(R.drawable.ic_add_circle);
                mDefaultPreferenceLeft.setChecked(getResources().getBoolean(R.bool.config_keyguardShowFlashlightAffordance));
                screen.addPreference(mDefaultPreferenceLeft);
            }
        } else {
            // True "camera" preference
            mDefaultPreferenceRight = new CustomPreference(context, LOCKSCREEN_SHORTCUT_CAMERA);
            mSelectablePreferences.add(mDefaultPreferenceRight);
            mDefaultPreferenceRight.setTitle(R.string.lockscreen_default);
            //mDefaultPreferenceRight.setIcon(R.drawable.ic_add_circle);
            mDefaultPreferenceRight.setChecked(getResources().getBoolean(R.bool.config_keyguardShowCameraAffordance));
            screen.addPreference(mDefaultPreferenceRight);
        }

        LauncherApps apps = getContext().getSystemService(LauncherApps.class);
        List<LauncherActivityInfo> activities = apps.getActivityList(null,
                Process.myUserHandle());

        screen.addPreference(otherApps);
        activities.forEach(info -> {
            try {
                List<Shortcut> shortcuts = new ShortcutParser(getContext(),
                        info.getComponentName()).getShortcuts();
                AppPreference appPreference = new AppPreference(context, info);
                mSelectablePreferences.add(appPreference);
                if (shortcuts.size() != 0) {
                    screen.addPreference(appPreference);
                    shortcuts.forEach(shortcut -> {
                        ShortcutPreference shortcutPref = new ShortcutPreference(context, shortcut,
                                info.getLabel());
                        mSelectablePreferences.add(shortcutPref);
                        screen.addPreference(shortcutPref);
                    });
                    return;
                }
                otherApps.addPreference(appPreference);
            } catch (NameNotFoundException e) {
            }
        });
        // Move other apps to the bottom.
        screen.removePreference(otherApps);
        for (int i = 0; i < otherApps.getPreferenceCount(); i++) {
            Preference p = otherApps.getPreference(0);
            otherApps.removePreference(p);
            p.setOrder(Preference.DEFAULT_ORDER);
            screen.addPreference(p);
        }
        setPreferenceScreen(screen);
        mTunerService = Dependency.get(TunerService.class);
        mTunerService.addTunable(this, mKey);
    }
    
    private boolean hasCameraFlash() {
		if (mFlashlightController != null) {
		    mFlashlightController.initFlashLight();
		    if (mFlashlightController.hasFlashlight() && mFlashlightController.isAvailable()) {
				Log.d(TAG, "Device has camera flashlight");
		        return true;
		    }
		}
		Log.d(TAG, "Device does not have a camera flashlight");
		return false;
	}

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        mTunerService.setValue(mKey, preference.toString());
        getActivity().onBackPressed();
        return true;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (LOCKSCREEN_LEFT_BUTTON.equals(mKey)) {
            getActivity().setTitle(R.string.lockscreen_shortcut_left);
        } else {
            getActivity().setTitle(R.string.lockscreen_shortcut_right);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTunerService.removeTunable(this);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        String v = newValue != null ? newValue : "";
        mSelectablePreferences.forEach(p -> p.setChecked(v.equals(p.toString())));
    }

    private static class AppPreference extends SelectablePreference {
        private final LauncherActivityInfo mInfo;
        private boolean mBinding;

        public AppPreference(Context context, LauncherActivityInfo info) {
            super(context);
            mInfo = info;
            setTitle(context.getString(R.string.tuner_launch_app, info.getLabel()));
            setSummary(context.getString(R.string.tuner_app, info.getLabel()));
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            mBinding = true;
            if (getIcon() == null) {
                setIcon(mInfo.getBadgedIcon(
                        getContext().getResources().getConfiguration().densityDpi));
            }
            mBinding = false;
            super.onBindViewHolder(holder);
        }

        @Override
        protected void notifyChanged() {
            if (mBinding) return;
            super.notifyChanged();
        }

        @Override
        public String toString() {
            return mInfo.getComponentName().flattenToString();
        }
    }

    private static class ShortcutPreference extends SelectablePreference {
        private final Shortcut mShortcut;
        private boolean mBinding;

        public ShortcutPreference(Context context, Shortcut shortcut, CharSequence appLabel) {
            super(context);
            mShortcut = shortcut;
            setTitle(shortcut.label);
            setSummary(context.getString(R.string.tuner_app, appLabel));
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            mBinding = true;
            if (getIcon() == null) {
                setIcon(mShortcut.icon.loadDrawable(getContext()));
            }
            mBinding = false;
            super.onBindViewHolder(holder);
        }

        @Override
        protected void notifyChanged() {
            if (mBinding) return;
            super.notifyChanged();
        }

        @Override
        public String toString() {
            return mShortcut.toString();
        }
    }

    private static class CustomPreference extends SelectablePreference {
        private String mIdentifier;

        public CustomPreference(Context context, String id) {
            super(context);
            mIdentifier = id;
        }

        @Override
        public String toString() {
            return mIdentifier;
        }
    }
}
