package de.dorianscholz.openlibre.model;

import io.realm.annotations.RealmModule;

@RealmModule(classes = { ReadingData.class, SensorData.class, GlucoseData.class })
public class ProcessedDataModule {
}
