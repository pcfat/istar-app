package hk.istars.s;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.animation.ObjectAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private WebView webView;
    private float touchStartY = 0;
    private boolean isPulling = false;
    private ProgressBar pullRefreshIndicator;
    private FrameLayout pullRefreshContainer;
    private static final int PULL_THRESHOLD = 300;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = getBridge().getWebView();
        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setAllowFileAccess(true);

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptFileSchemeCookies(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.flush();
            }

            // Create pull-to-refresh indicator
            webView.post(() -> {
                ViewGroup rootView = (ViewGroup) webView.getRootView();

                // Create container with background
                pullRefreshContainer = new FrameLayout(this);
                FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(140, 140);
                containerParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                containerParams.topMargin = 100;
                pullRefreshContainer.setLayoutParams(containerParams);
                pullRefreshContainer.setAlpha(0f);

                // Set white circular background
                android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
                background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                background.setColor(0xFFFFFFFF);
                background.setStroke(1, 0xFFE0E0E0);
                pullRefreshContainer.setBackground(background);
                pullRefreshContainer.setElevation(8);

                // Create progress indicator
                pullRefreshIndicator = new ProgressBar(this);
                pullRefreshIndicator.setIndeterminate(true);
                pullRefreshIndicator.getIndeterminateDrawable().setColorFilter(0xFF1AABE0, android.graphics.PorterDuff.Mode.SRC_IN);
                FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(100, 100);
                progressParams.gravity = Gravity.CENTER;
                pullRefreshIndicator.setLayoutParams(progressParams);

                pullRefreshContainer.addView(pullRefreshIndicator);

                if (rootView instanceof FrameLayout) {
                    ((FrameLayout) rootView).addView(pullRefreshContainer);
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        view.loadUrl("file:///android_asset/public/error.html");
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    // 如果係 same origin (s.istars.hk)，喺 app 入面打開
                    if (url.startsWith("https://s.istars.hk/") || url.startsWith("http://localhost")) {
                        view.loadUrl(url);
                        return true;
                    }
                    // 外部 URL：打開瀏覽器
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
            });
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        float y = ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            touchStartY = y;
            isPulling = false;
        } else if (action == MotionEvent.ACTION_MOVE && webView != null) {
            float deltaY = y - touchStartY;
            int scrollY = webView.getScrollY();

            if (scrollY <= 0 && deltaY > 0) {
                float progress = Math.min(deltaY / PULL_THRESHOLD, 1.0f);
                if (pullRefreshContainer != null) {
                    pullRefreshContainer.setAlpha(progress * 0.9f);
                    pullRefreshContainer.setTranslationY(deltaY * 0.4f);
                    pullRefreshContainer.setScaleX(0.7f + progress * 0.3f);
                    pullRefreshContainer.setScaleY(0.7f + progress * 0.3f);
                }

                if (deltaY > PULL_THRESHOLD && !isPulling) {
                    isPulling = true;
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isPulling && webView != null && webView.getScrollY() <= 0) {
                if (pullRefreshContainer != null) {
                    // Smooth transition to final position
                    ObjectAnimator.ofFloat(pullRefreshContainer, "translationY", pullRefreshContainer.getTranslationY(), 50f).setDuration(200).start();
                    ObjectAnimator.ofFloat(pullRefreshContainer, "scaleX", pullRefreshContainer.getScaleX(), 1.0f).setDuration(200).start();
                    ObjectAnimator.ofFloat(pullRefreshContainer, "scaleY", pullRefreshContainer.getScaleY(), 1.0f).setDuration(200).start();
                    pullRefreshContainer.setAlpha(1.0f);
                }
                webView.reload();

                webView.postDelayed(() -> {
                    if (pullRefreshContainer != null) {
                        ObjectAnimator.ofFloat(pullRefreshContainer, "alpha", 1.0f, 0f).setDuration(300).start();
                        ObjectAnimator.ofFloat(pullRefreshContainer, "translationY", pullRefreshContainer.getTranslationY(), 0f).setDuration(300).start();
                    }
                }, 1200);
            } else {
                if (pullRefreshContainer != null) {
                    ObjectAnimator.ofFloat(pullRefreshContainer, "alpha", pullRefreshContainer.getAlpha(), 0f).setDuration(200).start();
                    ObjectAnimator.ofFloat(pullRefreshContainer, "translationY", pullRefreshContainer.getTranslationY(), 0f).setDuration(200).start();
                    ObjectAnimator.ofFloat(pullRefreshContainer, "scaleX", pullRefreshContainer.getScaleX(), 0.7f).setDuration(200).start();
                    ObjectAnimator.ofFloat(pullRefreshContainer, "scaleY", pullRefreshContainer.getScaleY(), 0.7f).setDuration(200).start();
                }
            }
            isPulling = false;
        }
        return super.dispatchTouchEvent(ev);
    }
}