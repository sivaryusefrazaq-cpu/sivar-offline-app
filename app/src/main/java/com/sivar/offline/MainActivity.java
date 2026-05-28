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
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private String pendingPdfFileName;
    private StringBuilder pendingPdfBase64;
    private static final int STORAGE_PERMISSION_REQUEST = 101;
    private static final String PDF_FOLDER_NAME = "Sivar Invoices";

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
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.addJavascriptInterface(new PdfBridge(), "AndroidPdf");
        requestStoragePermissionIfNeeded();

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
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void requestStoragePermissionIfNeeded() {
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
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
                        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, safeFileName);
                        values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
                        values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + PDF_FOLDER_NAME);
                        values.put(MediaStore.Files.FileColumns.IS_PENDING, 1);

                        Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);

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
                        finishedValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
                        getContentResolver().update(uri, finishedValues, null, null);
                    } else {
                        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), PDF_FOLDER_NAME);

                        if(!directory.exists() && !directory.mkdirs()) {
                            throw new IllegalStateException("Documents folder is unavailable");
                        }

                        File file = new File(directory, safeFileName);

                        try(FileOutputStream outputStream = new FileOutputStream(file)) {
                            outputStream.write(pdfBytes);
                        }

                        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        scanIntent.setData(Uri.fromFile(file));
                        sendBroadcast(scanIntent);
                    }

                    Toast.makeText(MainActivity.this, "PDF saved to Documents/" + PDF_FOLDER_NAME, Toast.LENGTH_LONG).show();
                } catch(Exception error) {
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
