package de.dorianscholz.openlibre.model;

import java.util.concurrent.TimeUnit;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class ReadingData extends RealmObject {
    public static final String ID = "id";
    static final String SENSOR = "sensor";
    static final String SENSOR_AGE_IN_MINUTES = "sensorAgeInMinutes";
    public static final String DATE = "date";
    static final String TREND = "trend";
    static final String HISTORY = "history";

    @PrimaryKey
    String id;
    SensorData sensor;
    int sensorAgeInMinutes = -1;
    public long date = -1;
    public RealmList<GlucoseData> trend = new RealmList<>();
    public RealmList<GlucoseData> history = new RealmList<>();

    public ReadingData() {}
    public ReadingData(RawTagData rawTagData) {
        id = rawTagData.id;
        date = rawTagData.date;
        sensor = new SensorData(rawTagData.sensor);

        sensorAgeInMinutes = getWord(rawTagData.data, 316);
        if (sensor.startDate < 0) {
            sensor.startDate = date - TimeUnit.MINUTES.toMillis(sensorAgeInMinutes);
        } else {
            // use start date of sensor to align data over multiple scans
            // (adding the magic number of 90 seconds to correct the time of the last trend value to be now)
            sensorAgeInMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(
                    date - sensor.startDate + TimeUnit.SECONDS.toMillis(90));
        }

        final int numHistoryValues = 32;
        final int historyIntervalInMinutes = 15;
        final int numTrendValues = 16;

        int indexTrend = rawTagData.data[26] & 0xFF;
        int indexHistory = rawTagData.data[27] & 0xFF;

        // discrete version of the sensor age based on the history interval length to align data over multiple scans
        // (adding the magic number of 2 minutes to align the history values with the trend values)
        int sensorAgeDiscreteInMinutes = 2 +
                (sensorAgeInMinutes / historyIntervalInMinutes) * historyIntervalInMinutes;

        // read history values from ring buffer, starting at indexHistory (bytes 124-315)
        for (int counter = 0; counter < numHistoryValues; counter++) {
            int index = (indexHistory + counter) % numHistoryValues;

            int glucoseLevelRaw = getHistoryValue(rawTagData.data, index);
            // skip zero values if the sensor has not filled the ring buffer completely
            if (glucoseLevelRaw > 0) {
                int dataAgeInMinutes = (numHistoryValues * historyIntervalInMinutes) -
                        counter * historyIntervalInMinutes;
                int ageInSensorMinutes = sensorAgeDiscreteInMinutes - dataAgeInMinutes;

                history.add(new GlucoseData(sensor, ageInSensorMinutes, glucoseLevelRaw, false));
            }
        }

        // read trend values from ring buffer, starting at indexTrend (bytes 28-123)
        for (int counter = 0; counter < numTrendValues; counter++) {
            int index = (indexTrend + counter) % numTrendValues;

            int glucoseLevelRaw = getTrendValue(rawTagData.data, index);
            // skip zero values if the sensor has not filled the ring buffer completely
            if (glucoseLevelRaw > 0) {
                int dataAgeInMinutes = numTrendValues - counter;
                int ageInSensorMinutes = sensorAgeInMinutes - dataAgeInMinutes;

                trend.add(new GlucoseData(sensor, ageInSensorMinutes, glucoseLevelRaw, true));
            }
        }
    }

    private static int makeWord(byte high, byte low) {
        return 0x100 * (high & 0xFF) + (low & 0xFF);
    }

    private static int getWord(byte[] data, int offset) {
        return makeWord(data[offset + 1], data[offset]);
    }

    // get trend value from trend table starting byte 28
    private static int getTrendValue(byte[] data, int index) {
        return getWord(data, index * 6 + 28) & 0x3FFF;
    }

    // get history value from history table starting byte 124
    private static int getHistoryValue(byte[] data, int index) {
        return getWord(data, index * 6 + 124) & 0x3FFF;
    }

}
