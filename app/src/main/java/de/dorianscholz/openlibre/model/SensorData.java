package de.dorianscholz.openlibre.model;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

import static java.lang.Math.max;

public class SensorData extends RealmObject {
    public static final String ID = "id";
    public static final String START_DATE = "startDate";

    @PrimaryKey
    public
    String id;
    public long startDate = -1;

    public SensorData() {}

    public SensorData(String sensorTagId) {
        id = String.format(Locale.US, "sensor_%s", sensorTagId);
    }

    public SensorData(SensorData sensor) {
        this.id = sensor.id;
        this.startDate = sensor.startDate;
    }

    public String getTagId() {
        return id.substring(7);
    }

    public long getTimeLeft() {
        return max(0, startDate + TimeUnit.DAYS.toMillis(14) - System.currentTimeMillis());
    }
}
