package de.gun.dienstcockpit;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

/**
 * Ersetzt die vom Build erzeugte MainActivity:
 *  - meldet die eigenen Module an
 *  - nimmt die Kennung aus dem Widget entgegen und gibt sie an die
 *    Weboberfläche weiter, damit dort der Termin geöffnet wird
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KontaktDatenPlugin.class);
        registerPlugin(WidgetAnstossPlugin.class);
        super.onCreate(savedInstanceState);
        terminUebergeben(getIntent());
    }

    @Override
    public void onNewIntent(Intent absicht) {
        super.onNewIntent(absicht);
        setIntent(absicht);
        terminUebergeben(absicht);
    }

    /**
     * Hängt die Kennung als Abfrageteil an die Adresse der Weboberfläche.
     * Die App liest sie beim Start aus und zeigt den Termin an.
     */
    private void terminUebergeben(Intent absicht) {
        if (absicht == null) {
            return;
        }
        String kennung = absicht.getStringExtra("termin");
        if (kennung == null || kennung.length() == 0) {
            return;
        }
        // Erst nach dem Laden der Oberfläche aufrufen
        final String ziel = kennung;
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        String js = "window.history.replaceState(null,'','?termin="
                                + ziel.replace("'", "") + "');"
                                + "if(window.widgetTerminOeffnen)window.widgetTerminOeffnen('"
                                + ziel.replace("'", "") + "');";
                        getBridge().getWebView().evaluateJavascript(js, null);
                    } catch (Exception fehler) {
                        // ohne Übergabe weiterarbeiten
                    }
                }
            });
        }
    }
}
