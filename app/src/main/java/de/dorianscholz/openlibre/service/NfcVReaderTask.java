package de.dorianscholz.openlibre.service;

import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import java.io.IOException;
import java.util.Arrays;

import de.dorianscholz.openlibre.OpenLibre;
import de.dorianscholz.openlibre.R;
import de.dorianscholz.openlibre.model.RawTagData;
import de.dorianscholz.openlibre.model.ReadingData;
import de.dorianscholz.openlibre.model.SensorData;
import de.dorianscholz.openlibre.ui.MainActivity;
import io.realm.Realm;
import io.realm.RealmResults;

import static android.content.Context.VIBRATOR_SERVICE;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.bytesToHexString;
import static de.dorianscholz.openlibre.OpenLibre.realmConfigProcessedData;
import static de.dorianscholz.openlibre.OpenLibre.realmConfigRawData;


public class NfcVReaderTask extends AsyncTask<Tag, Void, Boolean> {
    private static final String LOG_ID = "GLUCOSE::" + NfcVReaderTask.class.getSimpleName();

    private MainActivity mainActivity;
    private String sensorId;
    private byte[] data;

    public NfcVReaderTask(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        data = new byte[360];
    }

    @Override
    protected void onPostExecute(Boolean success) {
        ProgressBar pb = (ProgressBar) mainActivity.findViewById(R.id.pb_scan_spinning);
        pb.setVisibility(View.INVISIBLE);

        long[] vibrationSuccessPattern = {0, 200, 100, 200};
        Vibrator vibrator = (Vibrator) mainActivity.getSystemService(VIBRATOR_SERVICE);

        if (success) {
            vibrator.vibrate(vibrationSuccessPattern, -1);
        } else {
            vibrator.vibrate(200);
            return;
        }

        // FIXME: the new data should be propagated transparently through the database backend
        mainActivity.onShowScanData(processRawData(sensorId, data));
    }

    @Override
    protected Boolean doInBackground(Tag... params) {
        Tag tag = params[0];
        sensorId = bytesToHexString(tag.getId());
        return readNfcTag(tag);
    }

    private boolean readNfcTag(Tag tag) {
        NfcV nfcvTag = NfcV.get(tag);
        Log.d(NfcVReaderTask.LOG_ID, "Attempting to read tag data");
        try {
            nfcvTag.connect();
            final byte[] uid = tag.getId();
            final int step = OpenLibre.FLAG_READ_MULTIPLE_BLOCKS ? 3 : 1;
            final int blockSize = 8;

            for (int blockIndex = 0; blockIndex <= 40; blockIndex += step) {
                byte[] cmd;
                if (OpenLibre.FLAG_READ_MULTIPLE_BLOCKS) {
                    cmd = new byte[]{0x02, 0x23, (byte) blockIndex, 0x02}; // multi-block read 3 blocks
                } else {
                    cmd = new byte[]{0x60, 0x20, 0, 0, 0, 0, 0, 0, 0, 0, (byte) blockIndex, 0};
                    System.arraycopy(uid, 0, cmd, 2, 8);
                }

                byte[] readData;
                Long startReadingTime = System.currentTimeMillis();
                while (true) {
                    try {
                        readData = nfcvTag.transceive(cmd);
                        break;
                    } catch (IOException e) {
                        if ((System.currentTimeMillis() > startReadingTime + 2000)) {
                            Log.e(NfcVReaderTask.LOG_ID, "tag read timeout");
                            return false;
                        }
                    }
                }

                if (OpenLibre.FLAG_READ_MULTIPLE_BLOCKS) {
                    System.arraycopy(readData, 1, data, blockIndex * blockSize, readData.length - 1);
                } else {
                    readData = Arrays.copyOfRange(readData, 2, readData.length);
                    System.arraycopy(readData, 0, data, blockIndex * blockSize, blockSize);
                }

                updateProgressBar(blockIndex);
            }
            Log.d(NfcVReaderTask.LOG_ID, "GOT LOG_ID DATA!");

        } catch (Exception e) {

            Log.i(NfcVReaderTask.LOG_ID, e.toString());
            return false;

        } finally {
            try {
                nfcvTag.close();
            } catch (Exception e) {
                Log.e(NfcVReaderTask.LOG_ID, "Error closing tag!");
            }
        }
        Log.d(NfcVReaderTask.LOG_ID, "Tag data reader exiting");
        return true;
    }

    private void updateProgressBar(int blockIndex) {
        final int progress = blockIndex;
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                ProgressBar pb_reading;
                pb_reading = (ProgressBar) mainActivity.findViewById(R.id.pb_scan_spinning);
                pb_reading.setProgress(progress);
            }
        });
    }

    // to be able to use the returned ReadingData object, this has to be called from the GUI thread
    public static ReadingData processRawData(String sensorId, byte[] data) {
        // copy data to database
        Realm realmProcessedData = Realm.getInstance(realmConfigProcessedData);
        Realm realmRawData = Realm.getInstance(realmConfigRawData);

        SensorData sensor;
        RealmResults<SensorData> sensorResults = realmProcessedData.where(SensorData.class).contains(SensorData.ID, sensorId).findAll();
        if (sensorResults.size() > 0) {
            sensor = sensorResults.first();
        } else {
            sensor = new SensorData(sensorId);
        }

        // commit raw data into realm for debugging
        realmRawData.beginTransaction();
        RawTagData rawTagData = realmRawData.copyToRealmOrUpdate(new RawTagData(sensor, data));
        realmRawData.commitTransaction();

        // commit processed data into realm
        realmProcessedData.beginTransaction();
        ReadingData readingData = realmProcessedData.copyToRealmOrUpdate(new ReadingData(rawTagData));
        realmProcessedData.commitTransaction();

        realmProcessedData.close();
        realmRawData.close();

        return readingData;
    }
}
