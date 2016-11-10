package de.dorianscholz.openlibre.model;

import android.support.annotation.NonNull;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dorianscholz.openlibre.R;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;

public class AlgorithmUtil {

    private static final int PREDICTION_TIME = 15; // in minutes
    private static final double TREND_UP_DOWN_LIMIT = 10; // mg/dl / 10 minutes
    private static final double TREND_SLIGHT_UP_DOWN_LIMIT = TREND_UP_DOWN_LIMIT / 2;

    private enum TrendArrow {
        DOWN,
        SLIGHTLY_DOWN,
        FLAT,
        SLIGHTLY_UP,
        UP
    }

    public static final Map<TrendArrow, Integer> trendArrowMap = new HashMap<>();
    static {
        trendArrowMap.put(AlgorithmUtil.TrendArrow.DOWN, R.drawable.arrow_down);
        trendArrowMap.put(AlgorithmUtil.TrendArrow.SLIGHTLY_DOWN, R.drawable.arrow_slightly_down);
        trendArrowMap.put(AlgorithmUtil.TrendArrow.FLAT, R.drawable.arrow_flat);
        trendArrowMap.put(AlgorithmUtil.TrendArrow.SLIGHTLY_UP, R.drawable.arrow_slightly_up);
        trendArrowMap.put(AlgorithmUtil.TrendArrow.UP, R.drawable.arrow_up);
    }

    public static final SimpleDateFormat mFormatTime = new SimpleDateFormat("HH:mm");
    public static final SimpleDateFormat mFormatDayTime = new SimpleDateFormat("dd. HH:mm");
    public static final SimpleDateFormat mFormatDate = new SimpleDateFormat("dd.MM.yyyy");
    public static final SimpleDateFormat mFormatDateShort = new SimpleDateFormat("dd.MM.");
    public static final SimpleDateFormat mFormatDateTime = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    static final SimpleDateFormat mFormatDateTimeSec = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    static float convertGlucoseMMOLToMGDL(float mmol) {
        return mmol * 18f;
    }

    static float convertGlucoseMGDLToMMOL(float mgdl) {
        return mgdl / 18f;
    }

    static float convertGlucoseRawToMGDL(float raw) {
        return raw / 10f;
    }

    static float convertGlucoseRawToMMOL(float raw) {
        return convertGlucoseMGDLToMMOL(raw / 10f);
    }

    public static float convertGlucoseMGDLToDisplayUnit(float mgdl) {
        return GLUCOSE_UNIT_IS_MMOL ? convertGlucoseMGDLToMMOL(mgdl) : mgdl;
    }

    public static float convertGlucoseRawToDisplayUnit(float raw) {
        return GLUCOSE_UNIT_IS_MMOL ? convertGlucoseRawToMMOL(raw) : convertGlucoseRawToMGDL(raw);
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder builder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return "";
        }

        char[] buffer = new char[2];
        for (byte b : src) {
            buffer[0] = Character.forDigit((b >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(b & 0x0F, 16);
            builder.append(buffer);
        }

        return builder.toString();
    }

    public static TrendArrow getTrendArrow(PredictionData predictionData) {
        if (predictionData.glucoseSlopeRaw > TREND_UP_DOWN_LIMIT) {
            return TrendArrow.UP;
        } else if (predictionData.glucoseSlopeRaw < -TREND_UP_DOWN_LIMIT) {
            return TrendArrow.DOWN;
        } else if (predictionData.glucoseSlopeRaw > TREND_SLIGHT_UP_DOWN_LIMIT) {
            return TrendArrow.SLIGHTLY_UP;
        } else if (predictionData.glucoseSlopeRaw < -TREND_SLIGHT_UP_DOWN_LIMIT) {
            return TrendArrow.SLIGHTLY_DOWN;
        } else {
            return TrendArrow.FLAT;
        }
    }

    @NonNull
    public static PredictionData getPredictionData(List<GlucoseData> trendList) {
        PredictionData predictedGlucose = new PredictionData();
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < trendList.size(); i++) {
            regression.addData(i, (trendList.get(i)).glucoseLevelRaw);
        }
        predictedGlucose.glucoseData.glucoseLevelRaw =
                (int) regression.predict(trendList.size() - 1 + PREDICTION_TIME);
        predictedGlucose.glucoseSlopeRaw = regression.getSlope();
        predictedGlucose.confidenceInterval = regression.getSlopeConfidenceInterval();
        predictedGlucose.glucoseData.ageInSensorMinutes =
                trendList.get(trendList.size() - 1).ageInSensorMinutes + PREDICTION_TIME;
        return predictedGlucose;
    }
}
