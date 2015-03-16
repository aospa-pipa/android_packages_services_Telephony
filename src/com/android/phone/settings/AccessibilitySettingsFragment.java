/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.phone.settings;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;
import com.android.phone.settings.TtyModeListPreference;

public class AccessibilitySettingsFragment extends PreferenceFragment {
    private static final String LOG_TAG = AccessibilitySettingsFragment.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String BUTTON_TTY_KEY = "button_tty_mode_key";
    private static final String BUTTON_HAC_KEY = "button_hac_key";

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /**
         * Enable/disable the TTY setting when in/out of a call (and if carrier doesn't
         * support VoLTE with TTY).
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DBG) Log.d(LOG_TAG, "PhoneStateListener.onCallStateChanged: state=" + state);
            Preference pref = getPreferenceScreen().findPreference(BUTTON_TTY_KEY);
            if (pref != null) {
                pref.setEnabled(state == TelephonyManager.CALL_STATE_IDLE);
            }
        }
    };

    private Context mContext;
    private AudioManager mAudioManager;

    private TtyModeListPreference mButtonTTY;
    private CheckBoxPreference mButtonHAC;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity().getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        addPreferencesFromResource(R.xml.accessibility_settings);

        mButtonTTY = (TtyModeListPreference) findPreference(
                getResources().getString(R.string.tty_mode_key));
        mButtonHAC = (CheckBoxPreference) findPreference(BUTTON_HAC_KEY);

        TelecomManager telecomManager = TelecomManager.from(mContext);
        TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        if (!telephonyManager.isMultiSimEnabled() && telecomManager.isTtySupported()) {
            mButtonTTY.init();
        } else {
            getPreferenceScreen().removePreference(mButtonTTY);
            mButtonTTY = null;
        }

        if (getResources().getBoolean(R.bool.hac_enabled)) {
            int hac = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.HEARING_AID, SettingsConstants.HAC_DISABLED);
            mButtonHAC.setChecked(hac == SettingsConstants.HAC_ENABLED);
        } else {
            getPreferenceScreen().removePreference(mButtonHAC);
            mButtonHAC = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ImsManager.isVolteEnabledByPlatform(mContext) &&
                mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_carrier_volte_tty_supported)) {
            TelephonyManager tm =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (ImsManager.isVolteEnabledByPlatform(mContext) &&
                !mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_carrier_volte_tty_supported)) {
            TelephonyManager tm =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonTTY) {
            return true;
        } else if (preference == mButtonHAC) {
            int hac = mButtonHAC.isChecked()
                    ? SettingsConstants.HAC_ENABLED : SettingsConstants.HAC_DISABLED;
            // Update HAC value in Settings database.
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.HEARING_AID, hac);

            // Update HAC Value in AudioManager.
            mAudioManager.setParameter(SettingsConstants.HAC_KEY,
                    hac == SettingsConstants.HAC_ENABLED
                            ? SettingsConstants.HAC_VAL_ON : SettingsConstants.HAC_VAL_OFF);
            return true;
        }
        return false;
    }
}