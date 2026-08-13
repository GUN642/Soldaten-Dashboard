package de.gun.dienstcockpit;

import android.content.Context;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Übergibt eine HTML-Seite an das Druckmodul von Android.
 *
 * Hintergrund: In der eingebetteten Web-Ansicht gibt es keinen Druckdialog.
 * Über das Druckmodul erscheint dagegen die gewohnte Auswahl mit „Als PDF
 * speichern", ohne Umweg über das Teilen-Menü.
 */
@CapacitorPlugin(name = "Druck")
public class DruckPlugin extends Plugin {

    @PluginMethod
    public void drucken(final PluginCall call) {
        final String html = call.getString("html");
        final String titel = call.getString("titel", "Dokument");

        if (html == null || html.length() == 0) {
            call.reject("Es wurde kein Inhalt übergeben.");
            return;
        }

        // Der Druck muss im Haupt-Thread angestoßen werden
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final WebView ansicht = new WebView(getActivity());
                    ansicht.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            try {
                                druckenAnstossen(view, titel);
                                JSObject ergebnis = new JSObject();
                                ergebnis.put("erledigt", true);
                                call.resolve(ergebnis);
                            } catch (Exception fehler) {
                                call.reject("Druck nicht möglich: " + fehler.getMessage());
                            }
                        }
                    });
                    ansicht.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null);
                } catch (Exception fehler) {
                    call.reject("Die Seite konnte nicht aufbereitet werden: " + fehler.getMessage());
                }
            }
        });
    }

    private void druckenAnstossen(WebView ansicht, String titel) {
        Context ctx = getActivity();
        PrintManager verwalter = (PrintManager) ctx.getSystemService(Context.PRINT_SERVICE);
        if (verwalter == null) {
            throw new IllegalStateException("Das Druckmodul ist nicht verfügbar.");
        }

        String auftrag = titel;
        PrintDocumentAdapter adapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            adapter = ansicht.createPrintDocumentAdapter(auftrag);
        } else {
            adapter = ansicht.createPrintDocumentAdapter();
        }

        PrintAttributes.Builder aufbau = new PrintAttributes.Builder();
        aufbau.setMediaSize(PrintAttributes.MediaSize.ISO_A4);
        aufbau.setMinMargins(PrintAttributes.Margins.NO_MARGINS);

        verwalter.print(auftrag, adapter, aufbau.build());
    }
}
