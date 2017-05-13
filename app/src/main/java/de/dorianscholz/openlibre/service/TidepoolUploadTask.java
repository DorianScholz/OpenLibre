package de.dorianscholz.openlibre.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.jayway.awaitility.core.ConditionTimeoutException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import de.dorianscholz.openlibre.R;
import de.dorianscholz.openlibre.model.GlucoseData;
import io.realm.Realm;
import io.realm.Sort;
import io.tidepool.api.APIClient;
import io.tidepool.api.data.DeviceDataCBG;
import io.tidepool.api.data.User;

import static android.content.Context.MODE_PRIVATE;
import static com.jayway.awaitility.Awaitility.await;
import static de.dorianscholz.openlibre.OpenLibre.realmConfigProcessedData;


class TidepoolUploadTask extends AsyncTask<Void, Void, Boolean> {

    private static final String LOG_ID = "OpenLibre::" + TidepoolUploadTask.class.getSimpleName();

    private Context context;
    private TidepoolSynchronization tidepoolSynchronization;

    private AtomicBoolean awaitDone;
    private Exception awaitException;

    TidepoolUploadTask(Context context, TidepoolSynchronization tidepoolSynchronization) {
        this.context = context;
        this.tidepoolSynchronization = tidepoolSynchronization;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            Toast.makeText(context,
                    context.getString(R.string.tidepool_sync_success),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(context,
                    context.getString(R.string.tidepool_sync_error),
                    Toast.LENGTH_SHORT
            ).show();
        }
        tidepoolSynchronization.finished();
    }

    @Override
    protected void onCancelled() {
        tidepoolSynchronization.finished();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return uploadData();
    }

    private boolean uploadData() {
        try {

            Log.d(TidepoolUploadTask.LOG_ID, "Before login");

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String tidepoolUsername = settings.getString("pref_tidepool_username", "");
            String tidepoolPassword = settings.getString("pref_tidepool_password", "");
            String tidepoolServer = settings.getString("pref_tidepool_server", APIClient.PRODUCTION);

            APIClient tidepoolAPIClient = new APIClient(context, tidepoolServer);

            awaitDone = new AtomicBoolean(false);
            awaitException = null;
            tidepoolAPIClient.signIn(tidepoolUsername, tidepoolPassword, new APIClient.SignInListener() {
                @Override
                public void signInComplete(User user, Exception exception) {
                    awaitException = exception;
                    awaitDone.set(true);
                }
            });
            try {
                await().atMost(10, TimeUnit.SECONDS).untilTrue(awaitDone);
            } catch (ConditionTimeoutException exception) {
                awaitException = exception;
            }

            if (awaitException != null) {
                Log.e(TidepoolUploadTask.LOG_ID, "Login failed: " + awaitException.toString());
                return false;
            }

            Log.d(TidepoolUploadTask.LOG_ID, "Before upload");

            Realm realmProcessedData = Realm.getInstance(realmConfigProcessedData);

            SharedPreferences preferences = context.getSharedPreferences("tidepool", MODE_PRIVATE);
            String tidepoolUploadTimestampKey = preferences.getString("upload_timestamp_key", "upload_timestamp");
            long tidepoolUploadTimestamp = preferences.getLong(tidepoolUploadTimestampKey, 0);

            tidepoolSynchronization.updateProgress(0, new Date(tidepoolUploadTimestamp));

            // find data that has not be uploaded yet
            List<GlucoseData> newGlucoseData = realmProcessedData.where(GlucoseData.class).
                    equalTo(GlucoseData.IS_TREND_DATA, false).
                    greaterThan(GlucoseData.DATE, tidepoolUploadTimestamp).
                    findAllSorted(GlucoseData.DATE, Sort.ASCENDING);

            int countAllNewGlucoseData = newGlucoseData.size();

            while (newGlucoseData.size() > 0 && !isCancelled()) {

                // create tidepool compatible data (100 per upload to keep POST request small)
                ArrayList<DeviceDataCBG> deviceDataCBGs = new ArrayList<>();
                for (int i = 0; i < Math.min(newGlucoseData.size(), 100); i++) {
                    DeviceDataCBG deviceDataCBG = new DeviceDataCBG();
                    GlucoseData glucoseData = newGlucoseData.get(i);

                    deviceDataCBG.setDeviceId(glucoseData.sensor.id);
                    deviceDataCBG.setUploadId("");

                    deviceDataCBG.setUnits("mmol/L");
                    deviceDataCBG.setValue(glucoseData.glucose(true));

                    deviceDataCBG.setTime(new Date(glucoseData.date));
                    deviceDataCBG.setTimezoneOffset(glucoseData.timezoneOffsetInMinutes);

                    deviceDataCBGs.add(deviceDataCBG);
                }

                awaitDone = new AtomicBoolean(false);
                awaitException = null;
                tidepoolAPIClient.uploadDeviceData(deviceDataCBGs, new APIClient.UploadDeviceDataListener() {
                    @Override
                    public void dataUploaded(List data, Exception exception) {
                        awaitException = exception;
                        awaitDone.set(true);
                    }
                });
                try {
                    await().atMost(30, TimeUnit.SECONDS).untilTrue(awaitDone);
                } catch (ConditionTimeoutException exception) {
                    awaitException = exception;
                }

                if (awaitException != null) {
                    Log.e(TidepoolUploadTask.LOG_ID, "Upload failed: " + awaitException.toString());
                    return false;
                }

                // update last sync date
                tidepoolUploadTimestamp = deviceDataCBGs.get(deviceDataCBGs.size() - 1).getTime().getTime();

                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putLong(tidepoolUploadTimestampKey, tidepoolUploadTimestamp);
                preferencesEditor.commit();

                // find data that has not be uploaded yet
                newGlucoseData = realmProcessedData.where(GlucoseData.class).
                        equalTo(GlucoseData.IS_TREND_DATA, false).
                        greaterThan(GlucoseData.DATE, tidepoolUploadTimestamp).
                        findAllSorted(GlucoseData.DATE, Sort.ASCENDING);

                float progress = (countAllNewGlucoseData - newGlucoseData.size()) / (float) countAllNewGlucoseData;
                Log.i(TidepoolUploadTask.LOG_ID, "Uploaded until: " + new Date(tidepoolUploadTimestamp) + ", progress: " + progress);
                tidepoolSynchronization.updateProgress(progress, new Date(tidepoolUploadTimestamp));

            }

        } catch (Exception e) {

            Log.e(TidepoolUploadTask.LOG_ID, "Error: " + e.toString());
            return false;

        }
        Log.d(TidepoolUploadTask.LOG_ID, "After upload");
        return true;
    }

}
