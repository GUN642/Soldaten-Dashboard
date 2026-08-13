package de.gun.dienstcockpit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Widget für den Startbildschirm: zeigt die Termine und fälligen Aufgaben des
 * Tages.
 *
 * Die Daten kommen aus dem nativen Speicher, den die App über Capacitor
 * Preferences beschreibt (Schlüssel "widget_agenda"). Das Widget braucht daher
 * selbst keinen Zugriff auf Kalender oder Kontakte.
 */
public class AgendaWidget extends AppWidgetProvider {

    /** Capacitor legt seine Werte in dieser Datei ab. */
    private static final String PREFS = "CapacitorStorage";
    private static final String SCHLUESSEL = "widget_agenda";

    /** So viele Zeilen sind im Layout angelegt. */
    private static final int MAX_ZEILEN = 8;

    private static final int[] ZEILE_ROOT = {
        R.id.zeile1, R.id.zeile2, R.id.zeile3, R.id.zeile4,
        R.id.zeile5, R.id.zeile6, R.id.zeile7, R.id.zeile8
    };
    private static final int[] ZEILE_FARBE = {
        R.id.farbe1, R.id.farbe2, R.id.farbe3, R.id.farbe4,
        R.id.farbe5, R.id.farbe6, R.id.farbe7, R.id.farbe8
    };
    private static final int[] ZEILE_ZEIT = {
        R.id.zeit1, R.id.zeit2, R.id.zeit3, R.id.zeit4,
        R.id.zeit5, R.id.zeit6, R.id.zeit7, R.id.zeit8
    };
    private static final int[] ZEILE_TITEL = {
        R.id.titel1, R.id.titel2, R.id.titel3, R.id.titel4,
        R.id.titel5, R.id.titel6, R.id.titel7, R.id.titel8
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            zeichne(context, manager, id);
        }
    }

    /** Von der App aufgerufen, wenn sich die Daten geändert haben. */
    public static void alleAktualisieren(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName name = new ComponentName(context, AgendaWidget.class);
        int[] ids = manager.getAppWidgetIds(name);
        if (ids == null) {
            return;
        }
        for (int id : ids) {
            new AgendaWidget().zeichne(context, manager, id);
        }
    }

    private void zeichne(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews ansicht = new RemoteViews(context.getPackageName(), R.layout.agenda_widget);

        JSONObject daten = ladeDaten(context);

        // ---- Darstellung: hell, dunkel oder nach Gerät ----
        String modus = daten != null ? daten.optString("modus", "dunkel") : "dunkel";
        int deckkraft = daten != null ? daten.optInt("deckkraft", 85) : 85;
        boolean hell = "hell".equals(modus);
        if ("system".equals(modus)) {
            int nacht = context.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            hell = nacht != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        int grundfarbe = hell ? 0xFFFFFF : 0x0E1319;
        int alpha = Math.max(0, Math.min(100, deckkraft)) * 255 / 100;
        ansicht.setInt(R.id.hintergrund, "setBackgroundColor",
                (alpha << 24) | grundfarbe);

        int textFarbe = hell ? 0xFF16181D : 0xFFF2F5F9;
        int nebenFarbe = hell ? 0xFF5B6472 : 0xFFAAB4C2;

        // ---- Kopfzeile ----
        String datumText = daten != null ? daten.optString("datum", "") : "";
        String hinweis = daten != null ? daten.optString("hinweis", "") : "";
        int kw = daten != null ? daten.optInt("kw", 0) : 0;

        if (datumText.isEmpty()) {
            datumText = "Soldaten Dashboard";
        }
        ansicht.setTextViewText(R.id.datum, datumText);
        ansicht.setTextColor(R.id.datum, textFarbe);

        StringBuilder unten = new StringBuilder();
        if (kw > 0) {
            unten.append("KW ").append(kw);
        }
        if (!hinweis.isEmpty()) {
            if (unten.length() > 0) {
                unten.append(" · ");
            }
            unten.append(hinweis);
        }
        ansicht.setTextViewText(R.id.kopfZusatz, unten.toString());
        ansicht.setTextColor(R.id.kopfZusatz, nebenFarbe);

        // ---- Antippen der Kopfzeile öffnet die App ----
        ansicht.setOnClickPendingIntent(R.id.kopfBereich, appOeffnen(context, null));

        // ---- Zeilen füllen ----
        JSONArray liste = daten != null ? daten.optJSONArray("eintraege") : null;
        int anzahl = liste != null ? liste.length() : 0;

        for (int i = 0; i < MAX_ZEILEN; i++) {
            if (i < anzahl) {
                JSONObject e = liste.optJSONObject(i);
                if (e == null) {
                    ansicht.setViewVisibility(ZEILE_ROOT[i], android.view.View.GONE);
                    continue;
                }

                ansicht.setViewVisibility(ZEILE_ROOT[i], android.view.View.VISIBLE);

                // Farbmarke des Kalenders
                int farbe = farbeLesen(e.optString("farbe", ""), 0xFF8B96A5);
                ansicht.setInt(ZEILE_FARBE[i], "setBackgroundColor", farbe);

                // Zeitangabe
                String zeit = e.optString("zeit", "");
                if (zeit.isEmpty()) {
                    zeit = e.optBoolean("ganztags", false) ? "ganztg." : "—";
                }
                ansicht.setTextViewText(ZEILE_ZEIT[i], zeit);
                ansicht.setTextColor(ZEILE_ZEIT[i], nebenFarbe);

                // Titel, bei Bedarf mit Ort
                String titel = e.optString("titel", "");
                String ort = e.optString("ort", "");
                if (!ort.isEmpty()) {
                    titel = titel + "  ·  " + ort;
                }
                ansicht.setTextViewText(ZEILE_TITEL[i], titel);
                ansicht.setTextColor(ZEILE_TITEL[i], textFarbe);

                // Antippen öffnet den Termin in der App
                String kennung = e.optString("kennung", "");
                ansicht.setOnClickPendingIntent(ZEILE_ROOT[i], appOeffnen(context, kennung));
            } else {
                ansicht.setViewVisibility(ZEILE_ROOT[i], android.view.View.GONE);
            }
        }

        // ---- Hinweis, wenn nichts ansteht ----
        if (anzahl == 0) {
            ansicht.setViewVisibility(R.id.leerHinweis, android.view.View.VISIBLE);
            ansicht.setTextViewText(R.id.leerHinweis,
                    daten == null ? "App einmal öffnen" : "Heute nichts eingetragen");
            ansicht.setTextColor(R.id.leerHinweis, nebenFarbe);
        } else {
            ansicht.setViewVisibility(R.id.leerHinweis, android.view.View.GONE);
        }

        manager.updateAppWidget(widgetId, ansicht);
    }

    /** Liest die von der App abgelegte Übersicht. */
    private JSONObject ladeDaten(Context context) {
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String roh = p.getString(SCHLUESSEL, null);
            if (roh == null || roh.length() == 0) {
                return null;
            }
            return new JSONObject(roh);
        } catch (Exception fehler) {
            return null;
        }
    }

    /** Farbangabe "#rrggbb" in einen Farbwert umsetzen. */
    private int farbeLesen(String text, int ersatz) {
        try {
            if (text != null && text.startsWith("#")) {
                return Color.parseColor(text);
            }
        } catch (Exception fehler) {
            // Ersatzfarbe verwenden
        }
        return ersatz;
    }

    /**
     * Öffnet die App. Mit Kennung wird der betreffende Termin angezeigt; dazu
     * wird sie als Abfrageteil an die Adresse gehängt, den die Weboberfläche
     * beim Start ausliest.
     */
    private PendingIntent appOeffnen(Context context, String kennung) {
        Intent absicht = new Intent(context, MainActivity.class);
        absicht.setAction(Intent.ACTION_MAIN);
        absicht.addCategory(Intent.CATEGORY_LAUNCHER);
        absicht.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int kennnummer = 0;
        if (kennung != null && kennung.length() > 0) {
            absicht.putExtra("termin", kennung);
            absicht.setData(Uri.parse("dienstcockpit://termin/" + Uri.encode(kennung)));
            kennnummer = kennung.hashCode();
        }

        int flaggen = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flaggen |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, kennnummer, absicht, flaggen);
    }
}
