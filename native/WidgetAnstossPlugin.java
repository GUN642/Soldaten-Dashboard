package de.gun.dienstcockpit;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Kleine Brücke, mit der die App das Widget zum Neuzeichnen anstößt.
 * Nötig, weil das Widget sonst nur im halbstündigen Rhythmus aktualisiert.
 */
@CapacitorPlugin(name = "WidgetAnstoss")
public class WidgetAnstossPlugin extends Plugin {

    @PluginMethod
    public void aktualisieren(PluginCall call) {
        try {
            AgendaWidget.alleAktualisieren(getContext());
            JSObject ergebnis = new JSObject();
            ergebnis.put("erledigt", true);
            call.resolve(ergebnis);
        } catch (Exception fehler) {
            call.reject("Widget konnte nicht aktualisiert werden: " + fehler.getMessage());
        }
    }
}
