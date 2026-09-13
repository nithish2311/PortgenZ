package com.portgenz.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String LOCAL_APP_URL = "file:///android_asset/index.html";

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private ImageButton btnTopRefresh;

    private View navBuilder;
    private View navPreview;
    private View navPrint;
    private View navShare;

    private long backPressedTime = 0;
    private ValueCallback<Uri[]> fileUploadCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupWindowInsets();
        initViews();
        setupStatusBar();
        setupFileChooser();
        setupWebView();
        setupBackNavigation();

        // Load standalone PortGenZ studio immediately
        webView.loadUrl(LOCAL_APP_URL);
    }

    private void setupWindowInsets() {
        View rootLayout = findViewById(R.id.rootLayout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        btnTopRefresh = findViewById(R.id.btnTopRefresh);

        navBuilder = findViewById(R.id.navBuilder);
        navPreview = findViewById(R.id.navPreview);
        navPrint = findViewById(R.id.navPrint);
        navShare = findViewById(R.id.navShare);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.card_background));

        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
        btnTopRefresh.setOnClickListener(v -> webView.reload());

        // Bottom Navigation Bar tabs
        navBuilder.setOnClickListener(v -> {
            webView.evaluateJavascript("if (typeof switchToBuilder === 'function') { switchToBuilder(); }", null);
        });

        navPreview.setOnClickListener(v -> {
            webView.evaluateJavascript("if (typeof renderLivePortfolio === 'function') { renderLivePortfolio(); }", null);
        });

        navPrint.setOnClickListener(v -> createWebPrintJob(webView));
        navShare.setOnClickListener(v -> sharePortfolio());
    }

    private void setupStatusBar() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }
    }

    private void setupFileChooser() {
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (fileUploadCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        } else if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = result.getData().getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                    fileUploadCallback.onReceiveValue(results);
                    fileUploadCallback = null;
                }
            }
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "PortGenZApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                // Override window.print() to trigger Android's native print service
                view.evaluateJavascript(
                    "if (typeof window.PortGenZApp !== 'undefined') { " +
                    "   window.print = function() { window.PortGenZApp.printPage(); }; " +
                    "}", null
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("whatsapp:") || url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "No application found to open link", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    fileUploadCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If currently viewing portfolio, return to builder view
                webView.evaluateJavascript(
                    "(() => { " +
                    "  const pView = document.getElementById('view-portfolio'); " +
                    "  if (pView && pView.style.display !== 'none') { " +
                    "    switchToBuilder(); " +
                    "    return true; " +
                    "  } " +
                    "  return false; " +
                    "})()",
                    result -> {
                        if (!"true".equals(result)) {
                            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                                finish();
                            } else {
                                Toast.makeText(MainActivity.this, "Press back again to exit PortGenZ", Toast.LENGTH_SHORT).show();
                                backPressedTime = System.currentTimeMillis();
                            }
                        }
                    }
                );
            }
        });
    }

    private void createWebPrintJob(WebView webView) {
        runOnUiThread(() -> {
            PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (printManager != null) {
                String jobName = "PortGenZ Resume Document";
                PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
                printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
            } else {
                Toast.makeText(this, "Print service unavailable on this device", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sharePortfolio() {
        webView.evaluateJavascript(
            "(() => { " +
            "  const name = document.getElementById('inputName')?.value || 'My Portfolio'; " +
            "  const tagline = document.getElementById('inputTagline')?.value || ''; " +
            "  const email = document.getElementById('inputEmail')?.value || ''; " +
            "  const github = document.getElementById('inputGithub')?.value || ''; " +
            "  return `${name} | ${tagline}\\nContact: ${email}\\nGitHub: ${github}`; " +
            "})()",
            shareContent -> {
                String clean = (shareContent != null) ? shareContent.replace("\"", "").replace("\\n", "\n") : "Check out my portfolio built on PortGenZ Studio!";
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, clean);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, "Share Portfolio Profile"));
            }
        );
    }

    public static class WebAppInterface {
        private final MainActivity activity;

        WebAppInterface(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void printPage() {
            activity.createWebPrintJob(activity.webView);
        }
    }
}
