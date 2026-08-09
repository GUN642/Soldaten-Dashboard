package de.gun.dienstcockpit;

import android.Manifest;
import android.database.Cursor;
import android.provider.ContactsContract;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Liest wichtige Daten aus den Kontakten: Geburtstage, Jahrestage und eigene
 * Anlässe.
 *
 * Hintergrund: Android zeigt diese Daten zwar als Kalender "Wichtige Daten der
 * Kontakte" an, gespeichert sind sie aber in der Kontakte-Datenbank. Über die
 * Kalender-Schnittstelle liefert dieser Kalender deshalb keine Einträge.
 */
@CapacitorPlugin(
    name = "KontaktDaten",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_CONTACTS }, alias = "kontakte")
    }
)
public class KontaktDatenPlugin extends Plugin {

    /** Fragt die Berechtigung ab bzw. an. */
    @PluginMethod
    public void berechtigungAnfragen(PluginCall call) {
        if (getPermissionState("kontakte") == PermissionState.GRANTED) {
            JSObject ergebnis = new JSObject();
            ergebnis.put("erlaubt", true);
            call.resolve(ergebnis);
        } else {
            requestPermissionForAlias("kontakte", call, "nachAnfrage");
        }
    }

    @PermissionCallback
    private void nachAnfrage(PluginCall call) {
        JSObject ergebnis = new JSObject();
        ergebnis.put("erlaubt", getPermissionState("kontakte") == PermissionState.GRANTED);
        call.resolve(ergebnis);
    }

    /** Liefert alle hinterlegten Anlässe der Kontakte. */
    @PluginMethod
    public void ereignisseLesen(PluginCall call) {
        if (getPermissionState("kontakte") != PermissionState.GRANTED) {
            call.reject("Keine Berechtigung für die Kontakte.");
            return;
        }

        JSArray liste = new JSArray();
        Cursor zeiger = null;

        try {
            String[] felder = new String[] {
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.LABEL
            };

            String bedingung = ContactsContract.Data.MIMETYPE + " = ?";
            String[] werte = new String[] {
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
            };

            zeiger = getContext().getContentResolver().query(
                ContactsContract.Data.CONTENT_URI, felder, bedingung, werte, null);

            if (zeiger != null) {
                int spalteId    = zeiger.getColumnIndex(ContactsContract.Data.CONTACT_ID);
                int spalteName  = zeiger.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                int spalteDatum = zeiger.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE);
                int spalteTyp   = zeiger.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE);
                int spalteLabel = zeiger.getColumnIndex(ContactsContract.CommonDataKinds.Event.LABEL);

                while (zeiger.moveToNext()) {
                    String datum = spalteDatum >= 0 ? zeiger.getString(spalteDatum) : null;
                    if (datum == null || datum.length() == 0) {
                        continue;
                    }

                    int typ = spalteTyp >= 0 ? zeiger.getInt(spalteTyp) : 0;
                    String bezeichnung;
                    switch (typ) {
                        case ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY:
                            bezeichnung = "Geburtstag";
                            break;
                        case ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY:
                            bezeichnung = "Jahrestag";
                            break;
                        case ContactsContract.CommonDataKinds.Event.TYPE_OTHER:
                            bezeichnung = "Anlass";
                            break;
                        default:
                            String eigenes = spalteLabel >= 0 ? zeiger.getString(spalteLabel) : null;
                            bezeichnung = (eigenes != null && eigenes.length() > 0) ? eigenes : "Anlass";
                            break;
                    }

                    JSObject eintrag = new JSObject();
                    eintrag.put("kontaktId", spalteId >= 0 ? zeiger.getString(spalteId) : "");
                    eintrag.put("name", spalteName >= 0 ? zeiger.getString(spalteName) : "");
                    eintrag.put("datum", datum);
                    eintrag.put("typ", typ);
                    eintrag.put("bezeichnung", bezeichnung);
                    liste.put(eintrag);
                }
            }
        } catch (Exception fehler) {
            call.reject("Kontaktdaten konnten nicht gelesen werden: " + fehler.getMessage());
            return;
        } finally {
            if (zeiger != null) {
                zeiger.close();
            }
        }

        JSObject ergebnis = new JSObject();
        ergebnis.put("ereignisse", liste);
        call.resolve(ergebnis);
    }
}
