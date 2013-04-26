package com.la.motordroid.ui;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import com.la.motordroid.App;
import com.la.motordroid.R;

/**
 * @author Amir Lazarovich
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);
        findPreference(SettingsActivity.KEY_SERVER_ADDRESS).setDefaultValue(App.sConsts.SERVER_ADDRESS);
    }
}