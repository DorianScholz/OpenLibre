package de.dorianscholz.openlibre.model;

import static java.lang.Math.min;

public class PredictionData {
    private static final double MAX_CONFIDENCE_INTERVAL = 2;
    double glucoseSlopeRaw = -1; // mg/dl / 10 minutes
    double confidenceInterval = -1;
    public GlucoseData glucoseData = new GlucoseData();

    public double confidence() {
        return 1.0 - min(confidenceInterval, MAX_CONFIDENCE_INTERVAL) / MAX_CONFIDENCE_INTERVAL;
    }
}
