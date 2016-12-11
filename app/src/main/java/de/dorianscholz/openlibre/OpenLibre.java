package de.dorianscholz.openlibre;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import de.dorianscholz.openlibre.model.ProcessedDataModule;
import de.dorianscholz.openlibre.model.RawDataModule;
import de.dorianscholz.openlibre.model.RawTagData;
import de.dorianscholz.openlibre.model.ReadingData;
import de.dorianscholz.openlibre.model.UserDataModule;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.Sort;
import io.realm.exceptions.RealmError;

import static de.dorianscholz.openlibre.model.GlucoseData.convertGlucoseMGDLToDisplayUnit;

@ReportsCrashes(
        formUri = "http://www-stud.informatik.uni-frankfurt.de/~scholz/openlibre/report.php",
        reportType = HttpSender.Type.FORM,
        mode = ReportingInteractionMode.DIALOG,
        resToastText = R.string.crash_toast_text,
        resDialogTitle = R.string.app_name,
        resDialogText = R.string.crash_dialog_text,
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt,
        resDialogOkToast = R.string.crash_dialog_ok_toast
)
public class OpenLibre extends Application {

    private static final String LOG_ID = "GLUCOSE::" + OpenLibre.class.getSimpleName();

    // TODO: convert these to settings
    public static final boolean GLUCOSE_UNIT_IS_MMOL = false;
    public static final boolean FLAG_READ_MULTIPLE_BLOCKS = true;
    public static final float GLUCOSE_TARGET_MIN = convertGlucoseMGDLToDisplayUnit(80);
    public static final float GLUCOSE_TARGET_MAX = convertGlucoseMGDLToDisplayUnit(140);

    public static RealmConfiguration realmConfigRawData;
    public static RealmConfiguration realmConfigProcessedData;
    public static RealmConfiguration realmConfigUserData;
    public static File openLibreDataPath;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // if it is the onCreate for ARCA, skip own init tasks
        if (ACRA.isACRASenderServiceProcess()) {
            return;
        }

        Realm.init(this);

        // find a storage path that we can actually create a realm in
        if ((openLibreDataPath =
                tryRealmStorage(new File(Environment.getExternalStorageDirectory().getPath(), "openlibre")))
                    == null) {
            if ((openLibreDataPath =
                    tryRealmStorage(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)))
                        == null) {
                openLibreDataPath = getApplicationContext().getFilesDir();
            }
        }
        Log.i(LOG_ID, "Using data path: '" + openLibreDataPath.toString() + "'");

        realmConfigUserData = new RealmConfiguration.Builder()
                .modules(new UserDataModule())
                .directory(openLibreDataPath)
                .name("data_user.realm")
                .schemaVersion(1)
                .build();

        realmConfigRawData = new RealmConfiguration.Builder()
                .modules(new RawDataModule())
                .directory(openLibreDataPath)
                .name("data_raw.realm")
                .schemaVersion(1)
                .build();

        Realm realmRawData = Realm.getInstance(realmConfigRawData);

        migrateDefaultRealm(realmRawData);

        realmConfigProcessedData = new RealmConfiguration.Builder()
                .modules(new ProcessedDataModule())
                .directory(openLibreDataPath)
                .name("data_processed.realm")
                .schemaVersion(1)
                // delete processed data realm, if data structure changed
                // it will just be parsed again from the raw data
                .deleteRealmIfMigrationNeeded()
                .build();

        parseRawData(realmRawData);

        realmRawData.close();

        StethoUtils.install(this);
    }

    private void parseRawData(Realm realmRawData) {
        Realm realmProcessedData = Realm.getInstance(realmConfigProcessedData);
        if (realmProcessedData.isEmpty() && !realmRawData.isEmpty()) {
            // parse data from raw realm into processed data realm
            Log.i(LOG_ID, "Parsing data raw_data realm to processed_data realm.");
            realmProcessedData.beginTransaction();
            for (RawTagData rawTagData : realmRawData.where(RawTagData.class)
                            .findAllSorted(RawTagData.DATE, Sort.ASCENDING)) {
                realmProcessedData.copyToRealmOrUpdate(new ReadingData(rawTagData));
            }
            realmProcessedData.commitTransaction();
        }
        realmProcessedData.close();
    }

    private void migrateDefaultRealm(Realm realmRawData) {
        if (!realmRawData.isEmpty()) {
            return;
        }

        // look for old format default realm in default location
        RealmConfiguration defaultConfig = new RealmConfiguration.Builder()
                .schemaVersion(2)
                .migration(new DefaultRealmMigration())
                .build();

        Realm realmOldDefault = Realm.getInstance(defaultConfig);

        if (realmOldDefault.isEmpty()) {
            realmOldDefault.close();

            // look for old format default realm in custom location
            defaultConfig = new RealmConfiguration.Builder()
                    .directory(openLibreDataPath)
                    .schemaVersion(2)
                    .migration(new DefaultRealmMigration())
                    .build();

            realmOldDefault = Realm.getInstance(defaultConfig);

            if (realmOldDefault.isEmpty()) {
                realmOldDefault.close();
                return;
            }
        }

        Log.i(LOG_ID, "Migrating data from default realm to raw_data realm.");
        realmRawData.beginTransaction();
        // copy raw data from old default realm into new raw data realm
        for (RawTagData rawTagData : realmOldDefault.where(RawTagData.class).findAll()) {
            realmRawData.copyToRealmOrUpdate(realmOldDefault.copyFromRealm(rawTagData));
        }
        realmRawData.commitTransaction();

        realmOldDefault.close();

        Log.i(LOG_ID, "Making backup of default realm.");
        // backup and delete old default realm config since the data has been migrated
        File source = new File(defaultConfig.getRealmDirectory(), defaultConfig.getRealmFileName());
        File destination = new File(defaultConfig.getRealmDirectory(), "default.realm.backup");
        try {
            FileUtils.copyFile(source, destination);
            Realm.deleteRealm(defaultConfig);
        } catch (IOException e) {
            Log.e(LOG_ID, "Could not backup default realm: " + e.toString());
        }
    }

    private File tryRealmStorage(File path) {
        // check where we can actually store the databases on this device
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder()
                .directory(path)
                .name("test_storage.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        try {
            Realm testInstance = Realm.getInstance(realmConfiguration);
            testInstance.close();
        } catch (RealmError e) {
            Realm.deleteRealm(realmConfiguration);
            Log.e(LOG_ID, "Create realm failed for: '" + path.toString() + "': " + e.toString());
            return null;
        }
        return path;
    }

}
