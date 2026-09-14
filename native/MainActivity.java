package de.gun.dienstcockpit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

/**
 * Ersetzt die vom Build erzeugte MainActivity:
 *  - meldet die eigenen Module an
 *  - löscht bei einem erkannten App-Update den WebView-Cache und lädt neu
 *    (siehe cacheBeiUpdateLeeren)
 *  - nimmt die Kennung aus dem Widget entgegen und gibt sie an die
 *    Weboberfläche weiter, damit dort der Termin geöffnet wird
 */
public class MainActivity extends BridgeActivity {

    private static final String PREFS = "dienstcockpit_build";
    private static final String SCHLUESSEL_VERSIONSCODE = "letzter_versionscode";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KontaktDatenPlugin.class);
        registerPlugin(WidgetAnstossPlugin.class);
        registerPlugin(DruckPlugin.class);
        registerPlugin(WiederholungPlugin.class);
        super.onCreate(savedInstanceState);

        cacheBeiUpdateLeeren();
        terminUebergeben(getIntent());
    }

    /**
     * Android liefert nach einem App-Update dieselbe Installation weiter aus,
     * die WebView kann ältere Fassungen von www/index.html aber im eigenen
     * HTTP-Cache vorhalten und diese statt der neuen Version zeigen —
     * spürbar etwa daran, dass Korrekturen nach einem Update ausbleiben.
     *
     * WebView.clearCache(true) leert ausdrücklich nur den Ressourcen-Cache;
     * localStorage, in dem die eigentlichen Daten der App liegen, bleibt
     * davon unberührt. Das ist Androids dokumentiertes Verhalten dieser
     * Methode, nicht etwas, das hier selbst nachgebildet wird.
     */
    private void cacheBeiUpdateLeeren() {
        try {
            int aktuellerCode = aktuellenVersionscodeLesen();
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            int letzterCode = prefs.getInt(SCHLUESSEL_VERSIONSCODE, -1);

            if (aktuellerCode != letzterCode) {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    final WebView webView = getBridge().getWebView();
                    webView.clearCache(true);
                    webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.reload();
                        }
                    });
                }
                prefs.edit().putInt(SCHLUESSEL_VERSIONSCODE, aktuellerCode).apply();
            }
        } catch (Exception fehler) {
            // Bestenfalls leeren — schlägt es fehl, läuft die App normal weiter
        }
    }

    @SuppressWarnings("deprecation")
    private int aktuellenVersionscodeLesen() throws PackageManager.NameNotFoundException {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return (int) info.getLongVersionCode();
        }
        return info.versionCode;
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
        // Kennung kommt entweder direkt oder aus der Listenvorlage des Widgets
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
