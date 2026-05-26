# Sivar Offline Android App

This Android project wraps the existing `public` web app in an offline WebView.

The app loads from:

```text
app/src/main/assets/www/index.html
```

Data is saved locally on the phone using WebView local storage. It does not need Firebase or internet access.

## Build APK

Open this folder in Android Studio:

```text
C:\Users\ASUS\Desktop\sivar\android-offline
```

Then use:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

The debug APK will be created under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Offline Notes

- Firebase Hosting scripts were removed from the packaged copy.
- `html2canvas` and `jsPDF` were copied into `assets/www/vendor`.
- No Android internet permission is requested.
