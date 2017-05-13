package de.dorianscholz.openlibre.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import io.tidepool.api.APIClient;

public class SettingsActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
        // when username or server changes update the key for the sync progress
        if (key.equals("pref_tidepool_username") || key.equals("pref_tidepool_server")) {
            // trim spaces off of email address
            String tidepoolUsername = settings.getString("pref_tidepool_username", "").trim();
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("pref_tidepool_username", tidepoolUsername);

            String tidepoolServer = settings.getString("pref_tidepool_server", APIClient.PRODUCTION);

            SharedPreferences.Editor preferencesEditor = getApplicationContext().getSharedPreferences("tidepool", MODE_PRIVATE).edit();
            preferencesEditor.putString("upload_timestamp_key", "upload_timestamp_for_" + tidepoolUsername.toLowerCase() + "_at_" + tidepoolServer);
            preferencesEditor.apply();

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
