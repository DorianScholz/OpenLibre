package de.dorianscholz.openlibre;

import android.app.Application;

import com.facebook.stetho.Stetho;
import com.uphyca.stetho_realm.RealmInspectorModulesProvider;

class StethoUtils {

   static void install(Application application){
        // debugging tool
        Stetho.initialize(
                Stetho.newInitializerBuilder(application)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(application))
                        .enableWebKitInspector(RealmInspectorModulesProvider.builder(application).build())
                        .build());
   }
}

