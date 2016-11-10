package de.dorianscholz.openlibre.model;

import java.util.Locale;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class SensorData extends RealmObject {
    public static final String ID = "id";
    static final String START_DATE = "startDate";

    @PrimaryKey
    String id;
    long startDate = -1;

    public SensorData() {}

    public SensorData(String sensorId) {
        id = String.format(Locale.US, "sensor_%s", sensorId);
    }

    public SensorData(SensorData sensor) {
        this.id = sensor.id;
        this.startDate = sensor.startDate;
    }
}
