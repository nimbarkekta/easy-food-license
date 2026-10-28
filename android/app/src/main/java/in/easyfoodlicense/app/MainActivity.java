package in.easyfoodlicense.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {

    // The app shows the website bundled inside the APK (assets/www/index.html).
    // To show your hosted website instead, put its address here, e.g. "https://your-site.web.app/".
    private static final String START_URL = "file:///android_asset/www/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;

    // Opens the phone camera for the "Take photo" buttons
    private final ActivityResultLauncher<Uri> takePhoto =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (fileCallback == null) return;
            fileCallback.onReceiveValue(ok && cameraUri != null ? new Uri[]{ cameraUri } : null);
            fileCallback = null;
        });

    // Opens the phone's file / gallery picker for the "Upload" buttons
    private final ActivityResultLauncher<Intent> pickFiles =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileCallback == null) return;
            Uri[] uris = null;
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    uris = new Uri[]{ data.getData() };
                }
            }
            fileCallback.onReceiveValue(uris);
            fileCallback = null;
        });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;

                if (params.isCaptureEnabled()) {
                    try {
                        File dir = new File(getCacheDir(), "camera");
                        if (!dir.exists()) dir.mkdirs();
                        File photo = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
                        cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", photo);
                        takePhoto.launch(cameraUri);
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Camera not available, choose a file instead", Toast.LENGTH_SHORT).show();
                    }
                }

                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    pickFiles.launch(Intent.createChooser(pick, "Choose document"));
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No file app found", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String url = uri.toString();
                // Pages of the app itself stay inside the app
                if (url.startsWith("file:///android_asset/") || url.startsWith(START_URL)) return false;
                // WhatsApp, Google Drive and outside websites open in their own apps
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No app found to open this link", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
