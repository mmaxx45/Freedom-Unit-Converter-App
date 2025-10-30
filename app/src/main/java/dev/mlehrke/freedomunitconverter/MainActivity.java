package dev.mlehrke.freedomunitconverter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);

        setupWebView();
    }

    private void setupWebView() {
        File appDir = getFilesDir();
        File indexFile = new File(appDir, "index.html");

        if (isNetworkAvailable()) {
            // Wenn Internet da ist, alles runterladen
            new Thread(() -> {
                downloadAllFiles(); // lädt Dateien herunter
                runOnUiThread(() -> webView.loadUrl("file://" + indexFile.getAbsolutePath()));
            }).start();
        } else if (indexFile.exists()) {
            // Offline: lade die vorhandene index.html
            webView.loadUrl("file://" + indexFile.getAbsolutePath());
        } else {
            // Kein Internet + keine lokalen Dateien
            webView.loadData(
                    "Keine Internetverbindung und keine lokalen Daten verfügbar. Bitte die App initial mit dem Internet verbinden.",
                    "text/plain",
                    "UTF-8"
            );
        }
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
            e.printStackTrace();
        }
    }

    private void downloadFile(String fileUrl, File destFile) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            if (destFile.getParentFile() != null) {
                destFile.getParentFile().mkdirs();
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
            e.printStackTrace();
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
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }
}