package de.dorianscholz.openlibre.model;

import android.support.annotation.NonNull;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDateTime;
import static de.dorianscholz.openlibre.model.GlucoseData.formatValue;

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

    public float glucose() {
        return glucoseLevel;
    }

    public String glucoseString() {
        return formatValue(glucose());
    }

    @Override
    public int compareTo(@NonNull BloodGlucoseData another) {
        return (int) (date - another.date);
    }
}
