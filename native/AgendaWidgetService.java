package de.gun.dienstcockpit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Füllt die Liste des Widgets. Wird von Android aufgerufen, sobald das Widget
 * gezeichnet oder gescrollt wird.
 */
public class AgendaWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent absicht) {
        return new AgendaFactory(getApplicationContext());
    }

    static class AgendaFactory implements RemoteViewsService.RemoteViewsFactory {

        private static final String PREFS = "CapacitorStorage";
        private static final String SCHLUESSEL = "widget_agenda";

        private final Context ctx;
        private JSONArray eintraege = new JSONArray();
        private boolean hell = false;
        private boolean einTag = true;
        /* Endzeit in der Zeitspalte. Wird von der App mitgeliefert und ist
           dort standardmaessig aus, damit jede Zeile einzeilig bleibt. */
        private boolean mitEndzeit = false;

        AgendaFactory(Context context) {
            this.ctx = context;
        }

        @Override
        public void onCreate() {
            laden();
        }

        @Override
        public void onDataSetChanged() {
            laden();
        }

        /** Liest die von der App abgelegte Übersicht. */
        private void laden() {
            try {
                String roh = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(SCHLUESSEL, null);
                if (roh == null || roh.length() == 0) {
                    eintraege = new JSONArray();
                    return;
                }
                JSONObject daten = new JSONObject(roh);
                JSONArray alle = daten.optJSONArray("eintraege");
                if (alle == null) {
                    alle = new JSONArray();
                }

                /* Die App legt 15 Tage im Voraus ab. Hier wird ausgewaehlt,
                   was zum heutigen Datum passt -- so stimmt die Anzeige auch
                   nach Mitternacht, ohne dass die App geoeffnet wurde. */
                int anzahlTage = Math.max(1, daten.optInt("tage", 1));
                einTag = anzahlTage == 1;
                boolean mitAufgaben = daten.optBoolean("aufgaben", false);
                mitEndzeit = daten.optBoolean("endzeit", false);

                java.util.Set<String> erlaubteTage = new java.util.HashSet<String>();
                java.util.Calendar kal = java.util.Calendar.getInstance();
                java.text.SimpleDateFormat form =
                        new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY);
                for (int i = 0; i < anzahlTage; i++) {
                    erlaubteTage.add(form.format(kal.getTime()));
                    kal.add(java.util.Calendar.DAY_OF_MONTH, 1);
                }

                JSONArray gefiltert = new JSONArray();
                for (int i = 0; i < alle.length(); i++) {
                    JSONObject e = alle.optJSONObject(i);
                    if (e == null) {
                        continue;
                    }
                    if (!erlaubteTage.contains(e.optString("tag", ""))) {
                        continue;
                    }
                    String art = e.optString("art", "");
                    if ("aufgabe".equals(art) && !mitAufgaben) {
                        continue;
                    }
                    // Ueberschrift nur behalten, wenn der Tag auch Eintraege hat
                    if ("tag".equals(art)) {
                        if (einTag) {
                            continue;           // bei einem Tag keine Ueberschrift
                        }
                        boolean hatInhalt = false;
                        for (int j = i + 1; j < alle.length(); j++) {
                            JSONObject n = alle.optJSONObject(j);
                            if (n == null) {
                                continue;
                            }
                            if ("tag".equals(n.optString("art", ""))) {
                                break;
                            }
                            if (!"aufgabe".equals(n.optString("art", "")) || mitAufgaben) {
                                hatInhalt = true;
                                break;
                            }
                        }
                        if (!hatInhalt) {
                            continue;
                        }
                    }
                    gefiltert.put(e);
                }
                eintraege = gefiltert;

                String modus = daten.optString("modus", "dunkel");
                if ("hell".equals(modus)) {
                    hell = true;
                } else {
                    hell = false;
                }
            } catch (Exception fehler) {
                eintraege = new JSONArray();
            }
        }

        @Override
        public void onDestroy() {
            eintraege = new JSONArray();
        }

        @Override
        public int getCount() {
            return eintraege.length();
        }

        @Override
        public RemoteViews getViewAt(int stelle) {
            JSONObject e = eintraege.optJSONObject(stelle);
            int textFarbe = hell ? 0xFF16181D : 0xFFF2F5F9;
            int nebenFarbe = hell ? 0xFF5B6472 : 0xFFAAB4C2;

            if (e == null) {
                return new RemoteViews(ctx.getPackageName(), R.layout.agenda_zeile);
            }

            // Ueberschrift eines Tages, wenn mehrere Tage gezeigt werden
            if ("tag".equals(e.optString("art", ""))) {
                RemoteViews kopf = new RemoteViews(ctx.getPackageName(), R.layout.agenda_tag);
                kopf.setTextViewText(R.id.tagTitel, e.optString("titel", ""));
                kopf.setTextViewText(R.id.tagZusatz, e.optString("ort", ""));
                kopf.setTextColor(R.id.tagZusatz, nebenFarbe);
                // Antippen oeffnet lediglich die App
                kopf.setOnClickFillInIntent(R.id.tagWurzel, new Intent());
                return kopf;
            }

            RemoteViews zeile = new RemoteViews(ctx.getPackageName(), R.layout.agenda_zeile);

            // Farbmarke des Kalenders
            int farbe = 0xFF8B96A5;
            try {
                String f = e.optString("farbe", "");
                if (f.startsWith("#")) {
                    farbe = Color.parseColor(f);
                }
            } catch (Exception fehler) {
                // Ersatzfarbe verwenden
            }
            zeile.setInt(R.id.zeileFarbe, "setBackgroundColor", farbe);

            // Zeitangabe: Beginn und, sofern vorhanden, Ende in zweiter Zeile
            String zeitVon = e.optString("zeit", "");
            String zeitBis = e.optString("zeitBis", "");
            String zeit;
            if (zeitVon.length() == 0) {
                zeit = e.optBoolean("ganztags", false) ? "ganztg." : "—";
            } else if (mitEndzeit && zeitBis.length() > 0) {
                zeit = zeitVon + "\n" + zeitBis;
            } else {
                zeit = zeitVon;
            }
            zeile.setTextViewText(R.id.zeileZeit, zeit);
            zeile.setTextColor(R.id.zeileZeit, nebenFarbe);

            // Titel, bei Bedarf mit Ort
            String titel = e.optString("titel", "");
            String ort = e.optString("ort", "");
            if (ort.length() > 0) {
                titel = titel + "  ·  " + ort;
            }
            zeile.setTextViewText(R.id.zeileTitel, titel);
            zeile.setTextColor(R.id.zeileTitel, textFarbe);

            // Antippen: Kennung an das Widget zurückgeben
            Intent fuellung = new Intent();
            fuellung.putExtra("termin", e.optString("kennung", ""));
            zeile.setOnClickFillInIntent(R.id.zeileWurzel, fuellung);

            return zeile;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;   // Termin und Tagesueberschrift
        }

        @Override
        public long getItemId(int stelle) {
            return stelle;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
