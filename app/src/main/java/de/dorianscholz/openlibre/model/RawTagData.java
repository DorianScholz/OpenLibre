package de.dorianscholz.openlibre.model;

import java.util.Locale;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RawTagData extends RealmObject {
    public static final String ID = "id";
    public static final String DATE = "date";
    static final String SENSOR = "sensor";
    static final String DATA = "data";

    @PrimaryKey
    String id;
    long date = -1;
    SensorData sensor;
    byte[] data;

    public RawTagData() {}

    public RawTagData(SensorData sensorData, byte[] data) {
        date = System.currentTimeMillis();
        sensor = sensorData;
        id = String.format(Locale.US, "%s_%d", sensor.id, date);
        this.data = data.clone();
    }

    RawTagData(RawTagData rawTagData) {
        date = rawTagData.date;
        sensor = new SensorData(rawTagData.sensor);
        id = rawTagData.id;
        data = rawTagData.data.clone();
    }
}
