package de.dorianscholz.openlibre.model;

import io.realm.annotations.RealmModule;

/**
 * Created by dorian on 7/11/16.
 */
@RealmModule(classes = { ReadingData.class, SensorData.class, GlucoseData.class })
public class ProcessedDataModule {
}
