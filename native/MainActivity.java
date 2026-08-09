package de.gun.dienstcockpit;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

/**
 * Ersetzt die vom Build erzeugte MainActivity, um das eigene Plugin
 * "KontaktDaten" anzumelden. Ohne diese Anmeldung wäre es für die App
 * nicht erreichbar.
 */
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KontaktDatenPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
