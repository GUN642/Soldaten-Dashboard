package de.gun.dienstcockpit;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.TimeZone;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Liest wiederkehrende Termine direkt aus der Android-Kalenderdatenbank
 * (CalendarContract.Events), statt über die von Android selbst ausgerechnete
 * Instanzen-Tabelle zu gehen.
 *
 * Hintergrund: Zu jedem wiederkehrenden Termin legt Android einen
 * Ursprungseintrag mit einer Wiederholungsregel (RRULE) an und berechnet
 * daraus selbst eine zweite, interne Tabelle mit den einzelnen Terminen.
 * Scheitert diese Berechnung für eine bestimmte Regel — etwa bei aus anderen
 * Systemen synchronisierten Terminen (z. B. über FamilyWall eingespielte
 * Apple-Kalendertermine) —, bleibt diese Tabelle für den betroffenen Termin
 * leer, obwohl der Ursprungseintrag weiterhin existiert. Kalender-Module, die
 * nur die bereits berechnete Tabelle lesen, sehen solche Termine dann nicht.
 * Dieses Modul liest stattdessen die Ursprungseinträge direkt; die
 * Wiederholung rechnet die App selbst aus.
 */
@CapacitorPlugin(name = "Wiederholung")
public class WiederholungPlugin extends Plugin {

    /**
     * Liest alle Kalender vollständig aus CalendarContract.Calendars —
     * unabhängig davon, welche das andere Kalendermodul in seiner eigenen
     * Liste führt. Genutzt, um auch Terminen aus Kalendern, die dort nicht
     * auftauchen, einen echten Namen statt nur einer Kennung zuzuordnen.
     */
    @PluginMethod
    public void alleKalenderIds(PluginCall call) {
        Context ctx = getContext();
        JSArray ergebnis = new JSArray();

        String[] projektion = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT
        };

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI, projektion, null, null, null);

            if (c != null) {
                while (c.moveToNext()) {
                    try {
                        JSObject e = new JSObject();
                        e.put("id", c.getString(0));
                        e.put("name", c.getString(1) != null ? c.getString(1) : "");
                        e.put("accountName", c.getString(2) != null ? c.getString(2) : "");
                        e.put("accountType", c.getString(3) != null ? c.getString(3) : "");
                        e.put("owner", c.getString(4) != null ? c.getString(4) : "");
                        ergebnis.put(e);
                    } catch (Exception einzelFehler) {
                        // einzelnen fehlerhaften Datensatz überspringen
                    }
                }
            }
            JSObject antwort = new JSObject();
            antwort.put("result", ergebnis);
            call.resolve(antwort);
        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (Exception fehler) {
            call.reject("Kalenderliste konnte nicht gelesen werden: " + fehler.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @PluginMethod
    public void ursprungsTermine(PluginCall call) {
        Context ctx = getContext();
        JSArray ergebnis = new JSArray();

        String[] projektion = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events._SYNC_ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.EVENT_TIMEZONE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
        };

        // Nur Ursprungstermine mit Wiederholungsregel, keine gelöschten
        String auswahl = CalendarContract.Events.RRULE + " IS NOT NULL AND "
                + CalendarContract.Events.DELETED + " != 1 AND ("
                + CalendarContract.Events.STATUS + " IS NULL OR "
                + CalendarContract.Events.STATUS + " != "
                + CalendarContract.Events.STATUS_CANCELED + ")";

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI, projektion, auswahl, null, null);

            if (c != null) {
                int iId = c.getColumnIndex(CalendarContract.Events._ID);
                int iSync = c.getColumnIndex(CalendarContract.Events._SYNC_ID);
                int iKal = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID);
                int iTitel = c.getColumnIndex(CalendarContract.Events.TITLE);
                int iStart = c.getColumnIndex(CalendarContract.Events.DTSTART);
                int iEnde = c.getColumnIndex(CalendarContract.Events.DTEND);
                int iDauer = c.getColumnIndex(CalendarContract.Events.DURATION);
                int iGanztag = c.getColumnIndex(CalendarContract.Events.ALL_DAY);
                int iRegel = c.getColumnIndex(CalendarContract.Events.RRULE);
                int iZone = c.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE);
                int iOrt = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);
                int iBeschreibung = c.getColumnIndex(CalendarContract.Events.DESCRIPTION);

                while (c.moveToNext()) {
                    try {
                        long start = c.getLong(iStart);
                        boolean allDay = c.getInt(iGanztag) != 0;

                        long dauerMs;
                        if (!c.isNull(iEnde)) {
                            dauerMs = c.getLong(iEnde) - start;
                        } else {
                            dauerMs = dauerAusIso(c.getString(iDauer), allDay);
                        }
                        if (dauerMs <= 0) {
                            dauerMs = allDay ? 86400000L : 3600000L;
                        }

                        JSObject e = new JSObject();
                        e.put("id", c.getString(iId));
                        e.put("syncId", iSync >= 0 && c.getString(iSync) != null ? c.getString(iSync) : "");
                        e.put("calendarId", c.getString(iKal));
                        e.put("title", c.getString(iTitel) != null ? c.getString(iTitel) : "");
                        e.put("startDate", start);
                        e.put("endDate", start + dauerMs);
                        e.put("isAllDay", allDay);
                        e.put("rrule", c.getString(iRegel));
                        e.put("timezone", c.getString(iZone) != null ? c.getString(iZone) : "");
                        e.put("location", c.getString(iOrt) != null ? c.getString(iOrt) : "");
                        e.put("description", c.getString(iBeschreibung) != null ? c.getString(iBeschreibung) : "");

                        ergebnis.put(e);
                    } catch (Exception einzelFehler) {
                        // einzelnen fehlerhaften Datensatz überspringen, Rest weiterlesen
                    }
                }
            }

            JSObject antwort = new JSObject();
            antwort.put("result", ergebnis);
            call.resolve(antwort);

        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (Exception fehler) {
            call.reject("Ursprungstermine konnten nicht gelesen werden: " + fehler.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Liest die Ausnahmen wiederkehrender Termine.
     *
     * Loescht oder verschiebt man ein einzelnes Vorkommen einer Serie, bleibt
     * der Ursprungseintrag mit seiner Regel unveraendert stehen. Android legt
     * stattdessen einen eigenen Datensatz an, der auf die Serie verweist
     * (ORIGINAL_ID beziehungsweise ORIGINAL_SYNC_ID) und den urspruenglichen
     * Zeitpunkt des betroffenen Vorkommens nennt (ORIGINAL_INSTANCE_TIME).
     *
     * Wer die Serie selbst nachrechnet, muss diese Zeitpunkte auslassen. Sonst
     * taucht ein geloeschter Termin wieder auf, und ein verschobener steht
     * doppelt da: einmal am alten und einmal am neuen Platz.
     *
     * Ausgegeben werden alle Ausnahmen, geloescht wie verschoben. In beiden
     * Faellen gilt: an der urspruenglichen Stelle findet nichts mehr statt.
     */
    @PluginMethod
    public void ausnahmen(PluginCall call) {
        Context ctx = getContext();
        JSArray ergebnis = new JSArray();

        String[] projektion = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.ORIGINAL_ID,
                CalendarContract.Events.ORIGINAL_SYNC_ID,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                CalendarContract.Events.STATUS,
                CalendarContract.Events.DELETED
        };

        String auswahl = CalendarContract.Events.ORIGINAL_INSTANCE_TIME + " IS NOT NULL";

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI, projektion, auswahl, null, null);

            if (c != null) {
                int iId = c.getColumnIndex(CalendarContract.Events._ID);
                int iOrig = c.getColumnIndex(CalendarContract.Events.ORIGINAL_ID);
                int iOrigSync = c.getColumnIndex(CalendarContract.Events.ORIGINAL_SYNC_ID);
                int iZeit = c.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME);
                int iStatus = c.getColumnIndex(CalendarContract.Events.STATUS);
                int iGeloescht = c.getColumnIndex(CalendarContract.Events.DELETED);

                while (c.moveToNext()) {
                    try {
                        if (c.isNull(iZeit)) {
                            continue;
                        }
                        JSObject e = new JSObject();
                        e.put("id", iId >= 0 ? c.getString(iId) : "");
                        e.put("originalId", iOrig >= 0 && c.getString(iOrig) != null ? c.getString(iOrig) : "");
                        e.put("originalSyncId", iOrigSync >= 0 && c.getString(iOrigSync) != null ? c.getString(iOrigSync) : "");
                        e.put("originalInstanceTime", c.getLong(iZeit));
                        e.put("status", iStatus >= 0 && !c.isNull(iStatus) ? c.getInt(iStatus) : -1);
                        e.put("deleted", iGeloescht >= 0 && !c.isNull(iGeloescht) && c.getInt(iGeloescht) != 0);
                        ergebnis.put(e);
                    } catch (Exception einzelFehler) {
                        // einzelnen fehlerhaften Datensatz ueberspringen
                    }
                }
            }

            JSObject antwort = new JSObject();
            antwort.put("result", ergebnis);
            call.resolve(antwort);

        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (Exception fehler) {
            call.reject("Ausnahmen konnten nicht gelesen werden: " + fehler.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Liest die Wiederholungsregel eines Termins direkt aus der Datenbank.
     *
     * Das eingesetzte Kalendermodul liefert die Regel beim Auflisten nicht mit.
     * Ohne sie wuesste die Bearbeitungsmaske nicht, was eingestellt ist, und
     * ein Speichern wuerde eine vorhandene Wiederholung stillschweigend
     * entfernen.
     */
    @PluginMethod
    public void regelLesen(PluginCall call) {
        String eventId = call.getString("eventId");
        if (eventId == null || eventId.length() == 0) {
            call.reject("Keine Terminkennung angegeben.");
            return;
        }

        Cursor c = null;
        try {
            Uri uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, Long.parseLong(eventId));
            c = getContext().getContentResolver().query(
                    uri, new String[]{CalendarContract.Events.RRULE}, null, null, null);

            String regel = "";
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                regel = c.getString(0) != null ? c.getString(0) : "";
            }

            JSObject antwort = new JSObject();
            antwort.put("rrule", regel);
            call.resolve(antwort);

        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (Exception fehler) {
            call.reject("Wiederholungsregel konnte nicht gelesen werden: " + fehler.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Setzt oder entfernt die Wiederholungsregel eines Termins.
     *
     * Wichtig dabei: Android verlangt bei wiederkehrenden Terminen ein leeres
     * DTEND und stattdessen eine Dauer in DURATION. Wird die Regel gesetzt,
     * ohne das umzustellen, bleibt der Termin ein Einzeltermin oder die
     * Kalenderdatenbank weist ihn zurueck. Beim Entfernen der Regel gilt das
     * Umgekehrte: DURATION leeren und DTEND wieder eintragen.
     *
     * Ein leeres oder fehlendes "rrule" bedeutet: Wiederholung entfernen.
     */
    @PluginMethod
    public void regelSetzen(PluginCall call) {
        String eventId = call.getString("eventId");
        String regel = call.getString("rrule");
        if (eventId == null || eventId.length() == 0) {
            call.reject("Keine Terminkennung angegeben.");
            return;
        }
        if (regel != null) {
            regel = regel.trim();
            if (regel.toUpperCase().startsWith("RRULE:")) {
                regel = regel.substring(6);
            }
        }

        Cursor c = null;
        try {
            Uri uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, Long.parseLong(eventId));

            c = getContext().getContentResolver().query(uri, new String[]{
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.DURATION,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_TIMEZONE
            }, null, null, null);

            if (c == null || !c.moveToFirst()) {
                call.reject("Termin nicht gefunden.");
                return;
            }

            long start = c.getLong(0);
            boolean hatEnde = !c.isNull(1);
            long ende = hatEnde ? c.getLong(1) : 0;
            String dauerText = c.getString(2);
            boolean ganztag = c.getInt(3) != 0;
            String zone = c.getString(4);

            long dauerMs = hatEnde ? (ende - start) : dauerAusIso(dauerText, ganztag);
            if (dauerMs <= 0) {
                dauerMs = ganztag ? 86400000L : 3600000L;
            }

            ContentValues werte = new ContentValues();

            if (regel == null || regel.length() == 0) {
                werte.putNull(CalendarContract.Events.RRULE);
                werte.putNull(CalendarContract.Events.DURATION);
                werte.put(CalendarContract.Events.DTEND, start + dauerMs);
            } else {
                werte.put(CalendarContract.Events.RRULE, regel);
                werte.putNull(CalendarContract.Events.DTEND);
                if (ganztag) {
                    long tage = Math.round(dauerMs / 86400000.0);
                    if (tage < 1) {
                        tage = 1;
                    }
                    werte.put(CalendarContract.Events.DURATION, "P" + tage + "D");
                } else {
                    long sekunden = dauerMs / 1000L;
                    if (sekunden < 1) {
                        sekunden = 3600L;
                    }
                    werte.put(CalendarContract.Events.DURATION, "PT" + sekunden + "S");
                }
                if (zone == null || zone.length() == 0) {
                    werte.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                }
            }

            int geaendert;
            try {
                geaendert = getContext().getContentResolver().update(uri, werte, null, null);
            } catch (Exception ablehnung) {
                /* Manche Kalenderanbieter weisen die Umstellung von festem Ende
                   auf Dauer zurueck. Dann nur die Regel selbst schreiben und den
                   Termin ansonsten unangetastet lassen. Lieber eine Serie, die
                   Android nicht aufloest -- dafuer rechnet die App selbst nach --
                   als ein Termin, der gar nicht mehr auftaucht. */
                ContentValues nurRegel = new ContentValues();
                if (regel == null || regel.length() == 0) {
                    nurRegel.putNull(CalendarContract.Events.RRULE);
                } else {
                    nurRegel.put(CalendarContract.Events.RRULE, regel);
                }
                geaendert = getContext().getContentResolver().update(uri, nurRegel, null, null);
            }

            JSObject antwort = new JSObject();
            antwort.put("result", geaendert > 0);
            antwort.put("geaendert", geaendert);
            call.resolve(antwort);

        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (Exception fehler) {
            call.reject("Wiederholungsregel konnte nicht gesetzt werden: " + fehler.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Entfernt ein einzelnes Vorkommen einer Serie.
     *
     * Android sieht dafuer die Ausnahme-Adresse CONTENT_EXCEPTION_URI vor:
     * eingetragen wird der urspruengliche Zeitpunkt des Vorkommens und der
     * Status "abgesagt". Der Ursprungstermin mit seiner Regel bleibt dabei
     * unangetastet, und die Kontensynchronisation traegt die Ausnahme
     * genauso nach Google oder Outlook wie eine im Kalender selbst gesetzte.
     *
     * Der von der App gemeldete Zeitpunkt kann um Stunden danebenliegen,
     * etwa bei ganztaegigen Terminen, die intern in UTC gefuehrt werden.
     * Deshalb wird zuerst in Androids Vorkommenstabelle nachgesehen und der
     * tatsaechliche Zeitpunkt des Tages verwendet. Findet sich dort nichts --
     * bei Serien, die Android nicht aufloest --, gilt der gemeldete Wert.
     */
    @PluginMethod
    public void vorkommenLoeschen(PluginCall call) {
        String eventId = call.getString("eventId");
        Long gemeldet = call.getLong("originalInstanceTime");
        if (eventId == null || eventId.length() == 0 || gemeldet == null || gemeldet <= 0) {
            call.reject("Termin oder Zeitpunkt fehlt.");
            return;
        }

        try {
            long id = Long.parseLong(eventId);
            long zeitpunkt = gemeldet;

            // Tatsaechlichen Zeitpunkt aus der Vorkommenstabelle holen
            Cursor c = null;
            try {
                long von = gemeldet - 18L * 60L * 60L * 1000L;
                long bis = gemeldet + 18L * 60L * 60L * 1000L;
                Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(b, von);
                ContentUris.appendId(b, bis);
                c = getContext().getContentResolver().query(b.build(),
                        new String[]{CalendarContract.Instances.BEGIN},
                        CalendarContract.Instances.EVENT_ID + " = ?",
                        new String[]{String.valueOf(id)}, null);

                long besterAbstand = Long.MAX_VALUE;
                while (c != null && c.moveToNext()) {
                    long beginn = c.getLong(0);
                    long abstand = Math.abs(beginn - gemeldet);
                    if (abstand < besterAbstand) {
                        besterAbstand = abstand;
                        zeitpunkt = beginn;
                    }
                }
            } catch (Exception suchFehler) {
                // dann bleibt es beim gemeldeten Zeitpunkt
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            ContentValues werte = new ContentValues();
            werte.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, zeitpunkt);
            werte.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED);

            Uri ziel = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_EXCEPTION_URI, id);
            Uri angelegt = getContext().getContentResolver().insert(ziel, werte);

            JSObject antwort = new JSObject();
            antwort.put("result", angelegt != null);
            antwort.put("zeitpunkt", zeitpunkt);
            call.resolve(antwort);

        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (Exception fehler) {
            call.reject("Das Vorkommen konnte nicht entfernt werden: " + fehler.getMessage());
        }
    }

    /**
     * Loescht einen Termin unmittelbar in der Kalenderdatenbank.
     *
     * Das eingesetzte Kalendermodul meldet beim Loeschen auch dann Erfolg,
     * wenn gar nichts entfernt wurde. Der Termin tauchte beim naechsten
     * Einlesen dann einfach wieder auf, ohne dass die App etwas davon
     * mitbekommen haette. Hier kommt die Zahl der betroffenen Zeilen zurueck,
     * damit sich das pruefen laesst.
     *
     * Bei Kalendern, die mit einem Konto abgeglichen werden, verschwindet die
     * Zeile nicht sofort, sondern wird als geloescht markiert und beim
     * naechsten Abgleich beim Anbieter entfernt. delete() meldet trotzdem 1.
     */
    @PluginMethod
    public void terminLoeschen(PluginCall call) {
        String eventId = call.getString("eventId");
        if (eventId == null || eventId.length() == 0) {
            call.reject("Keine Terminkennung angegeben.");
            return;
        }

        try {
            Uri uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, Long.parseLong(eventId));
            int geloescht = getContext().getContentResolver().delete(uri, null, null);

            JSObject antwort = new JSObject();
            antwort.put("result", geloescht > 0);
            antwort.put("geloescht", geloescht);
            call.resolve(antwort);

        } catch (SecurityException fehler) {
            call.reject("Keine Kalenderberechtigung: " + fehler.getMessage());
        } catch (NumberFormatException fehler) {
            call.reject("Unbrauchbare Terminkennung: " + eventId);
        } catch (Exception fehler) {
            call.reject("Der Termin konnte nicht entfernt werden: " + fehler.getMessage());
        }
    }

    /**
     * Einfacher Parser für die ISO-8601-Dauer, wie Android sie in der
     * DURATION-Spalte ablegt (z. B. "P1D", "PT2H30M"). Deckt die in der
     * Praxis vorkommenden Fälle ab, keinen vollständigen ISO-8601-Umfang.
     */
    private long dauerAusIso(String iso, boolean allDay) {
        if (iso == null || iso.length() == 0) {
            return allDay ? 86400000L : 3600000L;
        }
        long ms = 0;
        try {
            boolean zeitTeil = false;
            StringBuilder zahl = new StringBuilder();
            for (int i = 0; i < iso.length(); i++) {
                char ch = iso.charAt(i);
                if (ch == 'P') {
                    continue;
                }
                if (ch == 'T') {
                    zeitTeil = true;
                    continue;
                }
                if (Character.isDigit(ch)) {
                    zahl.append(ch);
                    continue;
                }

                long wert = zahl.length() > 0 ? Long.parseLong(zahl.toString()) : 0;
                zahl.setLength(0);

                switch (ch) {
                    case 'W':
                        ms += wert * 7L * 86400000L;
                        break;
                    case 'D':
                        ms += wert * 86400000L;
                        break;
                    case 'H':
                        ms += wert * 3600000L;
                        break;
                    case 'M':
                        ms += zeitTeil ? wert * 60000L : wert * 30L * 86400000L;
                        break;
                    case 'S':
                        ms += wert * 1000L;
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception fehler) {
            return allDay ? 86400000L : 3600000L;
        }
        return ms;
    }
}
