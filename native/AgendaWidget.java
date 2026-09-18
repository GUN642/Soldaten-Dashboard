package de.gun.dienstcockpit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * Widget für den Startbildschirm: zeigt die Termine und fälligen Aufgaben des
 * Tages in einer scrollbaren Liste.
 *
 * Die Daten kommen aus dem nativen Speicher, den die App über Capacitor
 * Preferences beschreibt (Schlüssel "widget_agenda"). Das Widget braucht daher
 * selbst keinen Zugriff auf Kalender oder Kontakte.
 */
public class AgendaWidget extends AppWidgetProvider {

    private static final String PREFS = "CapacitorStorage";
    private static final String SCHLUESSEL = "widget_agenda";
    private static final String TAGESWECHSEL = "de.gun.dienstcockpit.TAGESWECHSEL";

    /* Kurzformen ohne Punkt, wie im Rest der App (siehe WOCHENTAGE_KURZ in
       www/index.html) - Javas eigene Locale-Kurzformen ("EEE"/"MMM" mit
       Locale.GERMANY) liefern stattdessen "Fr." bzw. "Sept.", das weicht vom
       übrigen Erscheinungsbild ab. Calendar.DAY_OF_WEEK zaehlt 1=Sonntag. */
    private static final String[] WOCHENTAGE_KURZ = { "So","Mo","Di","Mi","Do","Fr","Sa" };
    private static final String[] MONATE_KURZ = { "Jan","Feb","Mär","Apr","Mai","Jun","Jul","Aug","Sep","Okt","Nov","Dez" };

    private static String kurzDatum(java.util.Calendar c){
        return c.get(java.util.Calendar.DAY_OF_MONTH) + ". " + MONATE_KURZ[c.get(java.util.Calendar.MONTH)];
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            zeichne(context, manager, id);
        }
        mitternachtEinplanen(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        mitternachtEinplanen(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // Wecker abbestellen, wenn kein Widget mehr vorhanden ist
        android.app.AlarmManager wecker = (android.app.AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        if (wecker != null) {
            wecker.cancel(mitternachtAbsicht(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent absicht) {
        super.onReceive(context, absicht);
        String aktion = absicht != null ? absicht.getAction() : null;
        if (TAGESWECHSEL.equals(aktion)
                || Intent.ACTION_DATE_CHANGED.equals(aktion)
                || Intent.ACTION_TIME_CHANGED.equals(aktion)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(aktion)) {
            alleAktualisieren(context);
            mitternachtEinplanen(context);
        }
    }

    /** Sorgt dafuer, dass das Widget zum Tageswechsel neu zeichnet. */
    private static void mitternachtEinplanen(Context context) {
        android.app.AlarmManager wecker = (android.app.AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        if (wecker == null) {
            return;
        }
        java.util.Calendar mitternacht = java.util.Calendar.getInstance();
        mitternacht.add(java.util.Calendar.DAY_OF_MONTH, 1);
        mitternacht.set(java.util.Calendar.HOUR_OF_DAY, 0);
        mitternacht.set(java.util.Calendar.MINUTE, 0);
        mitternacht.set(java.util.Calendar.SECOND, 5);
        mitternacht.set(java.util.Calendar.MILLISECOND, 0);

        try {
            wecker.set(android.app.AlarmManager.RTC, mitternacht.getTimeInMillis(),
                    mitternachtAbsicht(context));
        } catch (Exception fehler) {
            // Ohne Wecker aktualisiert Android das Widget spaetestens halbstuendlich
        }
    }

    private static PendingIntent mitternachtAbsicht(Context context) {
        Intent absicht = new Intent(context, AgendaWidget.class);
        absicht.setAction(TAGESWECHSEL);
        int flaggen = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flaggen |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 4711, absicht, flaggen);
    }

    /** Von der App aufgerufen, wenn sich die Daten geändert haben. */
    public static void alleAktualisieren(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName name = new ComponentName(context, AgendaWidget.class);
        int[] ids = manager.getAppWidgetIds(name);
        if (ids == null || ids.length == 0) {
            return;
        }
        AgendaWidget widget = new AgendaWidget();
        for (int id : ids) {
            widget.zeichne(context, manager, id);
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.liste);
    }

    private void zeichne(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews ansicht = new RemoteViews(context.getPackageName(), R.layout.agenda_widget);

        JSONObject daten = ladeDaten(context);

        // ---- Darstellung: hell, dunkel oder nach Gerät ----
        String modus = daten != null ? daten.optString("modus", "dunkel") : "dunkel";
        int deckkraft = daten != null ? daten.optInt("deckkraft", 85) : 85;
        boolean hell = "hell".equals(modus);

        int grundfarbe = hell ? 0xFFFFFF : 0x0E1319;
        int alpha = Math.max(0, Math.min(100, deckkraft)) * 255 / 100;
        ansicht.setInt(R.id.hintergrund, "setBackgroundColor", (alpha << 24) | grundfarbe);

        int textFarbe = hell ? 0xFF16181D : 0xFFF2F5F9;
        int nebenFarbe = hell ? 0xFF5B6472 : 0xFFAAB4C2;

        /* ---- Kopfzeile ----
           Das Datum wird hier gebildet und nicht aus den gespeicherten Daten
           uebernommen. Sonst zeigte das Widget nach Mitternacht weiter den
           Vortag, bis die App geoeffnet wurde. */
        int anzahlTage = daten != null ? Math.max(1, daten.optInt("tage", 1)) : 1;
        java.util.Calendar jetzt = java.util.Calendar.getInstance();

        /* Bei mehreren Tagen entfaellt die Kopfzeile mit Tag und Datum - jeder
           Tag traegt in der Liste ohnehin schon seine eigene Ueberschrift
           (siehe AgendaWidgetService), ein Datumsbereich hier waere doppelt. */
        if (anzahlTage > 1 && daten != null) {
            ansicht.setViewVisibility(R.id.datum, android.view.View.GONE);
        } else {
            ansicht.setViewVisibility(R.id.datum, android.view.View.VISIBLE);
            String datumText;
            if (daten == null) {
                datumText = "Soldaten Dashboard";
            } else {
                String wochentag = WOCHENTAGE_KURZ[jetzt.get(java.util.Calendar.DAY_OF_WEEK) - 1];
                datumText = wochentag + ", " + kurzDatum(jetzt);
            }
            ansicht.setTextViewText(R.id.datum, datumText);
            ansicht.setTextColor(R.id.datum, textFarbe);
        }

        StringBuilder zusatz = new StringBuilder();
        int kw = jetzt.get(java.util.Calendar.WEEK_OF_YEAR);
        if (kw > 0) {
            zusatz.append("KW ").append(kw);
        }
        ansicht.setTextViewText(R.id.kopfZusatz, zusatz.toString());
        ansicht.setTextColor(R.id.kopfZusatz, nebenFarbe);

        // Antippen der Kopfzeile öffnet die App
        ansicht.setOnClickPendingIntent(R.id.kopfBereich, appOeffnen(context, widgetId));

        // ---- Scrollbare Liste anbinden ----
        Intent dienst = new Intent(context, AgendaWidgetService.class);
        dienst.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        // Eindeutige Adresse, damit Android die Listen mehrerer Widgets unterscheidet
        dienst.setData(Uri.parse(dienst.toUri(Intent.URI_INTENT_SCHEME)));
        ansicht.setRemoteAdapter(R.id.liste, dienst);
        ansicht.setEmptyView(R.id.liste, R.id.leerHinweis);

        // Vorlage für das Antippen einer Zeile
        Intent vorlage = new Intent(context, MainActivity.class);
        vorlage.setAction("de.gun.dienstcockpit.WIDGET_TERMIN");
        vorlage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flaggenVorlage = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flaggenVorlage |= PendingIntent.FLAG_MUTABLE;
        }
        ansicht.setPendingIntentTemplate(R.id.liste,
                PendingIntent.getActivity(context, widgetId, vorlage, flaggenVorlage));

        // ---- Hinweis, wenn nichts vorliegt ----
        ansicht.setTextViewText(R.id.leerHinweis,
                daten == null ? "App einmal öffnen" : "Heute nichts eingetragen");
        ansicht.setTextColor(R.id.leerHinweis, nebenFarbe);

        manager.updateAppWidget(widgetId, ansicht);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.liste);
    }

    private JSONObject ladeDaten(Context context) {
        try {
            String roh = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(SCHLUESSEL, null);
            if (roh == null || roh.length() == 0) {
                return null;
            }
            return new JSONObject(roh);
        } catch (Exception fehler) {
            return null;
        }
    }

    /** Öffnet die App ohne bestimmten Termin. */
    private PendingIntent appOeffnen(Context context, int kennnummer) {
        Intent absicht = new Intent(context, MainActivity.class);
        absicht.setAction(Intent.ACTION_MAIN);
        absicht.addCategory(Intent.CATEGORY_LAUNCHER);
        absicht.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flaggen = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flaggen |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, kennnummer, absicht, flaggen);
    }
}
