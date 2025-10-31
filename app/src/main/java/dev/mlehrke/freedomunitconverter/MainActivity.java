package dev.mlehrke.freedomunitconverter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Button button;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_main);
        button = findViewById(R.id.activationButton);

        webView = findViewById(R.id.webView);

        webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);

        setupWebView();
        if(!isPremiumActive()) {
            setupButton();
        } else {
            button.setVisibility(Button.GONE);
        }

    }

    private void setupButton() {
        button.setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setHint("Key eingeben");

            new AlertDialog.Builder(this)
                    .setTitle("Premium Key aktivieren")
                    .setView(input)
                    .setPositiveButton("OK", ((dialog, which) -> {
                        String key = input.getText().toString().trim();

                        if(!key.isEmpty()) {
                            checkLicenseKey(key);
                        } else {
                            Toast.makeText(this, "Kein Key eingegeben", Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    }))
                    .setNegativeButton("Abbrechen", ((dialog, which) -> dialog.cancel()))
                    .show();
        });
    }

    private void checkLicenseKey(String key) {

        new Thread(() -> {
            try {
                URL url = new URL("https://fuc.mlehrke.dev/license.php?key="+key);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                InputStream in = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String response = reader.readLine().trim();
                reader.close();
                connection.disconnect();

                runOnUiThread(() -> {
                    if ("true".equalsIgnoreCase(response)) {
                        Toast.makeText(this, "Key gültig! Premium aktiviert.", Toast.LENGTH_SHORT).show();

                        button.setVisibility(Button.GONE); //remove button from UI

                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("premium_key", key)
                                .apply();
                    } else {
                        Toast.makeText(this, "Key ungültig!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> webView.loadData(
                        e.toString(),
                        "text/plain",
                        "UTF-8"
                ));
            }
        }).start();

    }

    private void setupWebView() {
        File appDir = getFilesDir();
        File indexFile = new File(appDir, "index.html");
        
        boolean filesExist = indexFile.exists();
        boolean premiumActive = isPremiumActive();
        boolean networkActive = isNetworkAvailable();

        if (!filesExist) {
            if(networkActive) {
                // Wenn Internet da ist, alles runterladen
                new Thread(() -> {
                    downloadAllFiles(); // lädt Dateien herunter
                    runOnUiThread(() -> webView.loadUrl("file://" + indexFile.getAbsolutePath()));
                }).start();
            } else {
                // Kein Internet + keine lokalen Dateien
                runOnUiThread(() -> webView.loadData(
                        "Keine Internetverbindung und keine lokalen Daten verfügbar. Bitte die App initial mit dem Internet verbinden.",
                        "text/plain",
                        "UTF-8"
                ));
            }
        } else if (networkActive && premiumActive) {
            // Wenn Internet da ist, und Premium aktiv ist, alles erneut runterladen
            new Thread(() -> {
                downloadAllFiles(); // lädt Dateien herunter
                runOnUiThread(() -> webView.loadUrl("file://" + indexFile.getAbsolutePath()));
            }).start();
        } else {
            // Offline ohne Premium: lade die vorhandene index.html
            runOnUiThread(() -> webView.loadUrl("file://" + indexFile.getAbsolutePath()));
        }

    }

    private boolean isPremiumActive() {
        String key = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("premium_key", null);
        return key != null && !key.isEmpty();
    }


    private void downloadAllFiles() {
        String list = "/files.txt";
        try {
            String BASE_URL = "https://fuc.mlehrke.dev";
            List<String> files = fetchFileList(BASE_URL + list);

            File appDir = getFilesDir();
            for (String filename : files) {
                downloadFile(BASE_URL +"/"+filename, new File(appDir, filename));
            }

            //To Start WebView from local file
            runOnUiThread(() -> webView.loadUrl("file://" + new File(appDir, "index.html").getAbsolutePath()));
        } catch (Exception e) {
            runOnUiThread(() -> webView.loadData(
                    e.toString(),
                    "text/plain",
                    "UTF-8"
            ));
        }
    }

    private void downloadFile(String fileUrl, File destFile) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            if (destFile.getParentFile() != null) {
               if(!destFile.getParentFile().mkdirs()) {
                   runOnUiThread(() -> webView.loadData(
                           "Fehler beim erstellen der Ordnerstruktur oder die Struktur ist bereits vorhanden.",
                           "text/plain",
                           "UTF-8"
                   ));
               }
            }

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            runOnUiThread(() -> webView.loadData(
                    e.toString(),
                    "text/plain",
                    "UTF-8"
            ));
        }
    }

    private List<String> fetchFileList(String urlString) throws IOException {

        List<String> files = new ArrayList<>();
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) files.add(line);
            }
        } finally {
            connection.disconnect();
        }
        return files;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}