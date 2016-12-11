package de.dorianscholz.openlibre.model;

import android.support.annotation.NonNull;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDateTime;

public class BloodGlucoseData extends RealmObject implements Comparable<BloodGlucoseData> {
    public static final String ID = "id";
    static final String GLUCOSE_LEVEL = "glucoseLevel";
    public static final String DATE = "date";

    @PrimaryKey
    String id;
    float glucoseLevel = -1; // in mg/dl
    public long date;

    public BloodGlucoseData() {}
    public BloodGlucoseData(long date, float glucoseLevel) {
        this.date = date;
        this.glucoseLevel = glucoseLevel;
        id = String.format(Locale.US, "blood_%s", mFormatDateTime.format(new Date(date)));
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

    public float glucose() {
        return glucoseLevel;
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
    public int compareTo(@NonNull BloodGlucoseData another) {
        return (int) (date - another.date);
    }
}
