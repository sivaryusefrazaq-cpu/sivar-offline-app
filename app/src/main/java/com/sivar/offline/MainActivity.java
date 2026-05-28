package com.sivar.offline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private String pendingPdfFileName;
    private StringBuilder pendingPdfBase64;
    private static final int STORAGE_PERMISSION_REQUEST = 101;
    private static final int LOCATION_PERMISSION_REQUEST = 102;
    private static final String PDF_FOLDER_NAME = "Sivar Invoices";
    private static final String TAG = "SivarPdfSave";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.addJavascriptInterface(new PdfBridge(), "AndroidPdf");
        webView.addJavascriptInterface(new AssetBridge(), "AndroidAssets");
        requestStoragePermissionIfNeeded();
        requestLocationPermissionIfNeeded();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if(url.startsWith("http://") || url.startsWith("https://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }

                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setOnCancelListener(dialog -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                boolean hasLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                callback.invoke(origin, hasLocationPermission, false);
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    public class AssetBridge {
        @JavascriptInterface
        public String getDataUrl(String fileName) {
            try {
                String safeFileName = fileName == null ? "" : fileName.replace("\\", "/");
                int slashIndex = safeFileName.lastIndexOf("/");

                if(slashIndex >= 0) {
                    safeFileName = safeFileName.substring(slashIndex + 1);
                }

                if(!"barcode.jpg".equals(safeFileName) && !"logo.jpg".equals(safeFileName) && !"logo.png".equals(safeFileName)) {
                    return "";
                }

                try(InputStream inputStream = getAssets().open("www/" + safeFileName);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    String base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
                    String mimeType = safeFileName.endsWith(".png") ? "image/png" : "image/jpeg";
                    return "data:" + mimeType + ";base64," + base64Data;
                }
            } catch(Exception error) {
                Log.e(TAG, "Could not read asset image", error);
                return "";
            }
        }
    }

    private void requestStoragePermissionIfNeeded() {
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
        }
    }

    private void requestLocationPermissionIfNeeded() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST
            );
        }
    }

    public class PdfBridge {
        @JavascriptInterface
        public void startPdfSave(String fileName) {
            pendingPdfFileName = fileName;
            pendingPdfBase64 = new StringBuilder();
        }

        @JavascriptInterface
        public void appendPdfChunk(String chunk) {
            if(pendingPdfBase64 == null) {
                pendingPdfBase64 = new StringBuilder();
            }

            pendingPdfBase64.append(chunk);
        }

        @JavascriptInterface
        public void finishPdfSave() {
            String fileName = pendingPdfFileName == null ? "sivar-invoice.pdf" : pendingPdfFileName;
            String base64Data = pendingPdfBase64 == null ? "" : pendingPdfBase64.toString();

            pendingPdfFileName = null;
            pendingPdfBase64 = null;

            savePdfBytes(fileName, base64Data);
        }

        @JavascriptInterface
        public void saveBase64(String dataUrl, String fileName) {
            String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
            savePdfBytes(fileName, base64Data);
        }

        private void savePdfBytes(String fileName, String base64Data) {
            runOnUiThread(() -> {
                try {
                    byte[] pdfBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    String safeFileName = sanitizePdfFileName(fileName);

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + PDF_FOLDER_NAME);
                        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                        if(uri == null) {
                            throw new IllegalStateException("Could not create PDF file");
                        }

                        try(OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                            if(outputStream == null) {
                                throw new IllegalStateException("Could not open PDF file");
                            }

                            outputStream.write(pdfBytes);
                        }

                        ContentValues finishedValues = new ContentValues();
                        finishedValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        getContentResolver().update(uri, finishedValues, null, null);
                    } else {
                        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PDF_FOLDER_NAME);

                        if(!directory.exists() && !directory.mkdirs()) {
                            throw new IllegalStateException("Download folder is unavailable");
                        }

                        File file = new File(directory, safeFileName);

                        try(FileOutputStream outputStream = new FileOutputStream(file)) {
                            outputStream.write(pdfBytes);
                        }

                        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        scanIntent.setData(Uri.fromFile(file));
                        sendBroadcast(scanIntent);
                    }

                    Toast.makeText(MainActivity.this, "PDF saved to Download/" + PDF_FOLDER_NAME, Toast.LENGTH_LONG).show();
                } catch(Exception error) {
                    Log.e(TAG, "PDF save failed", error);
                    Toast.makeText(MainActivity.this, "PDF save failed", Toast.LENGTH_LONG).show();
                }
            });
        }

        private String sanitizePdfFileName(String fileName) {
            String cleanName = fileName == null ? "sivar-invoice.pdf" : fileName.trim();
            cleanName = cleanName.replaceAll("[\\\\/:*?\"<>|]", "-");

            if(cleanName.length() == 0) {
                cleanName = "sivar-invoice.pdf";
            }

            if(!cleanName.toLowerCase().endsWith(".pdf")) {
                cleanName += ".pdf";
            }

            return cleanName;
        }
    }

    @Override
    public void onBackPressed() {
        if(webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }
}
