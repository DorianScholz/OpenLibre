package de.dorianscholz.openlibre.model;

import android.support.annotation.NonNull;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class GlucoseData extends RealmObject implements Comparable<GlucoseData> {
    public static final String ID = "id";
    static final String SENSOR = "sensor";
    static final String AGE_IN_SENSOR_MINUTES = "ageInSensorMinutes";
    static final String GLUCOSE_LEVEL_RAW = "glucoseLevelRaw";
    public static final String IS_TREND_DATA = "isTrendData";
    public static final String DATE = "date";

    @PrimaryKey
    String id;
    SensorData sensor;
    boolean isTrendData = false;
    int ageInSensorMinutes = -1;
    public int glucoseLevelRaw = -1; // in mg/l = 0.1 mg/dl
    public long date;

    public GlucoseData() {}
    public GlucoseData(SensorData sensor, int ageInSensorMinutes, int glucoseLevelRaw, boolean isTrendData) {
        this.sensor = sensor;
        this.ageInSensorMinutes = ageInSensorMinutes;
        this.glucoseLevelRaw = glucoseLevelRaw;
        this.isTrendData = isTrendData;
        if (isTrendData)
            id = String.format(Locale.US, "trend_%s_%05d", sensor.id, ageInSensorMinutes);
        else
            id = String.format(Locale.US, "history_%s_%05d", sensor.id, ageInSensorMinutes);
        date = sensor.startDate + TimeUnit.MINUTES.toMillis(ageInSensorMinutes);
    }

    double glucose(boolean mmol) {
        return mmol ?
                AlgorithmUtil.convertGlucoseRawToMMOL(glucoseLevelRaw) :
                AlgorithmUtil.convertGlucoseRawToMGDL(glucoseLevelRaw);
    }

    public String glucoseString(boolean mmol) {
        return mmol ?
                new DecimalFormat("##.0").format(glucose(mmol)) :
                new DecimalFormat("###").format(glucose(mmol));
    }

    @Override
    public int compareTo(@NonNull GlucoseData another) {
        return (int) (date - another.date);
    }
}
