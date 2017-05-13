package de.dorianscholz.openlibre.model;

import android.support.annotation.NonNull;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;

public class GlucoseData extends RealmObject implements Comparable<GlucoseData> {
    public static final String ID = "id";
    static final String SENSOR = "sensor";
    static final String AGE_IN_SENSOR_MINUTES = "ageInSensorMinutes";
    static final String GLUCOSE_LEVEL_RAW = "glucoseLevelRaw";
    public static final String IS_TREND_DATA = "isTrendData";
    public static final String DATE = "date";
    public static final String TIMEZONE_OFFSET_IN_MINUTES = "timezoneOffsetInMinutes";

    @PrimaryKey
    String id;
    public SensorData sensor;
    public boolean isTrendData = false;
    public int ageInSensorMinutes = -1;
    int glucoseLevelRaw = -1; // in mg/l = 0.1 mg/dl
    public long date;
    public int timezoneOffsetInMinutes;

    public GlucoseData() {}
    public GlucoseData(SensorData sensor, int ageInSensorMinutes, int timezoneOffsetInMinutes, int glucoseLevelRaw, boolean isTrendData) {
        this.sensor = sensor;
        this.ageInSensorMinutes = ageInSensorMinutes;
        this.timezoneOffsetInMinutes = timezoneOffsetInMinutes;
        this.glucoseLevelRaw = glucoseLevelRaw;
        this.isTrendData = isTrendData;
        if (isTrendData)
            id = String.format(Locale.US, "trend_%s_%05d", sensor.id, ageInSensorMinutes);
        else
            id = String.format(Locale.US, "history_%s_%05d", sensor.id, ageInSensorMinutes);
        date = sensor.startDate + TimeUnit.MINUTES.toMillis(ageInSensorMinutes);
    }

/*
    static float convertGlucoseMMOLToMGDL(float mmol) {
        return mmol * 18f;
    }
*/

    private static float convertGlucoseMGDLToMMOL(float mgdl) {
        return mgdl / 18f;
    }

    private static float convertGlucoseRawToMGDL(float raw) {
        return raw / 10f;
    }

    private static float convertGlucoseRawToMMOL(float raw) {
        return convertGlucoseMGDLToMMOL(raw / 10f);
    }

    public static float convertGlucoseMGDLToDisplayUnit(float mgdl) {
        return GLUCOSE_UNIT_IS_MMOL ? convertGlucoseMGDLToMMOL(mgdl) : mgdl;
    }

    public static float convertGlucoseRawToDisplayUnit(float raw) {
        return GLUCOSE_UNIT_IS_MMOL ? convertGlucoseRawToMMOL(raw) : convertGlucoseRawToMGDL(raw);
    }

    public static String getDisplayUnit() {
        return GLUCOSE_UNIT_IS_MMOL ? "mmol/l" : "mg/dl";
    }

    public float glucose(boolean as_mmol) {
        return as_mmol ? convertGlucoseRawToMMOL(glucoseLevelRaw) : convertGlucoseRawToMGDL(glucoseLevelRaw);
    }

    public float glucose() {
        return convertGlucoseRawToDisplayUnit(glucoseLevelRaw);
    }

    public static String formatValue(float value) {
        return GLUCOSE_UNIT_IS_MMOL ?
                new DecimalFormat("##.0").format(value) :
                new DecimalFormat("###").format(value);
    }
    public String glucoseString() {
        return formatValue(glucose());
    }

    @Override
    public int compareTo(@NonNull GlucoseData another) {
        return (int) (date - another.date);
    }
}
