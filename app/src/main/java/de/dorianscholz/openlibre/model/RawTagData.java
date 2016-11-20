package de.dorianscholz.openlibre.model;

import java.util.Locale;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class RawTagData extends RealmObject {
    public static final String ID = "id";
    public static final String DATE = "date";
    static final String SENSOR = "sensor";
    static final String DATA = "data";

    private static final int offsetTrendTable = 28;
    private static final int offsetHistoryTable = 124;
    private static final int offsetTrendIndex = 26;
    private static final int offsetHistoryIndex = 27;
    private static final int offsetSensorAge = 316;
    private static final int tableEntrySize = 6;

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

    int getTrendValue(int index) {
        return getWord(offsetTrendTable + index * tableEntrySize) & 0x3FFF;
    }

    int getHistoryValue(int index) {
        return getWord(offsetHistoryTable + index * tableEntrySize) & 0x3FFF;
    }

    private static int makeWord(byte high, byte low) {
        return 0x100 * (high & 0xFF) + (low & 0xFF);
    }

    private int getWord(int offset) {
        return makeWord(data[offset + 1], data[offset]);
    }

    private int getByte(int offset) {
        return data[offset] & 0xFF;
    }

    int getIndexTrend() {
        return getByte(offsetTrendIndex);
    }

    int getIndexHistory() {
        return getByte(offsetHistoryIndex);
    }

    int getSensorAgeInMinutes() {
        return getWord(offsetSensorAge);
    }
}
