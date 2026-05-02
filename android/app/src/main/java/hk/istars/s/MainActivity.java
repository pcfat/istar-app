package hk.istars.s;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends BridgeActivity {

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private WebView webView;
    private float touchStartY = 0;
    private long lastPullTime = 0;
    private boolean isPulling = false;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {}
        );

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

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

            checkBatteryOptimization();

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    injectLSToken(view, url);
                    injectFcmToken(view);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        view.loadUrl("file:///android_asset/public/error.html");
                    }
                }
            });
        }

        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                final String token = task.getResult().replace("'", "\\'");
                Handler h = new Handler(getMainLooper());
                Runnable inject = new Runnable() {
                    int attempts = 0;
                    @Override
                    public void run() {
                        attempts++;
                        if (webView != null) {
                            String js = "if(typeof window.__registerFCMToken==='function'){window.__registerFCMToken('" + token + "');}";
                            webView.evaluateJavascript(js, result -> {
                                if ((result == null || result.equals("null") || result.equals("undefined")) && attempts < 15) {
                                    h.postDelayed(this, 2000);
                                }
                            });
                        }
                    }
                };
                h.postDelayed(inject, 5000);
            });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        float y = ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            touchStartY = y;
            isPulling = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float deltaY = y - touchStartY;
            if (deltaY > 150) {
                long now = System.currentTimeMillis();
                if (now - lastPullTime > 2000) {
                    isPulling = true;
                    lastPullTime = now;
                    Toast.makeText(this, "正在重新加載頁面...", Toast.LENGTH_SHORT).show();
                    if (webView != null) {
                        webView.reload();
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void injectLSToken(WebView view, String url) {
        String js = "(function(){" +
            "if(location.pathname.indexOf('logout')!==-1)return;" +
            "var keys=['remember_token','student_remember_token','parent_remember_token'];" +
            "var tok=null;" +
            "keys.forEach(function(k){var v=localStorage.getItem(k);if(v&&!tok)tok=v;});" +
            "if(tok&&!location.search.includes('ls_token=')){" +
            "var newUrl=location.pathname+location.search+(location.search?'&':'?')+'ls_token='+tok;" +
            "window.location.replace(newUrl);return;}" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                showBatteryOptimizationDialog();
            }
        }
        detectAndPromptAutoStart();
    }

    private String getDeviceBrand() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND.toLowerCase();
        if (brand.contains("xiaomi") || brand.contains("redmi") || manufacturer.contains("xiaomi")) return "xiaomi";
        if (brand.contains("huawei") || brand.contains("honor") || manufacturer.contains("huawei")) return "huawei";
        if (brand.contains("oppo") || brand.contains("realme") || manufacturer.contains("oppo")) return "oppo";
        if (brand.contains("vivo") || manufacturer.contains("vivo")) return "vivo";
        if (brand.contains("samsung") || manufacturer.contains("samsung")) return "samsung";
        if (brand.contains("oneplus") || manufacturer.contains("oneplus")) return "oneplus";
        if (brand.contains("sony") || manufacturer.contains("sony")) return "sony";
        if (brand.contains("asus") || manufacturer.contains("asus")) return "asus";
        if (brand.contains("google") || manufacturer.contains("google")) return "google";
        return "generic";
    }

    private void detectAndPromptAutoStart() {
        String brand = getDeviceBrand();
        if (brand.equals("google")) return; // Pixel 唔需要
        showAutoStartDialog(brand);
    }

    private void showAutoStartDialog(String brand) {
        String title = "開啟自啟動權限";
        String message = "";
        if (brand.equals("xiaomi")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【應用設置】\n2. 找到【星進教育 i-STAR】\n3. 點擊【自啟動】，開啟開關\n4. 返回 App";
        } else if (brand.equals("huawei") || brand.equals("honor")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池】>【啟動管理】\n2. 找到【星進教育 i-STAR】\n3. 設為【允許自動運行】\n4. 返回 App";
        } else if (brand.equals("oppo") || brand.equals("realme")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池】\n2. 點擊【耗電優化】\n3. 找到【星進教育 i-STAR】，設為【允許後台】\n4. 返回 App";
        } else if (brand.equals("vivo")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池】>【高耗電提醒】\n2. 找到【星進教育 i-STAR】\n3. 開啟【允許後台運行】\n4. 返回 App";
        } else if (brand.equals("samsung")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池】>【後台應用程式】\n2. 找到【星進教育 i-STAR】\n3. 設為【永不關閉】\n4. 返回 App";
        } else if (brand.equals("oneplus")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池】>【電池優化】\n2. 找到【星進教育 i-STAR】\n3. 設為【不優化】\n4. 返回 App";
        } else if (brand.equals("sony")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池】>【後台應用程式】\n2. 找到【星進教育 i-STAR】\n3. 開啟【允許後台運行】\n4. 返回 App";
        } else if (brand.equals("asus")) {
            message = "為確保推送通知正常，請開啟自啟動權限：\n\n1. 進入【設定】>【電池管理】>【後台應用程式】\n2. 找到【星進教育 i-STAR】\n3. 開啟允許\n4. 返回 App";
        } else {
            message = "為確保推送通知正常，請允許 App 在後台運行：\n\n1. 進入【設定】>【應用】\n2. 找到【星進教育 i-STAR】\n3. 設為【允許後台活動】或【不自優化】\n4. 返回 App";
        }

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .setCancelable(true)
            .show();
    }

    private void showBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要關閉電池優化")
            .setMessage("為確保 App 能夠正常接收推送通知，請進行以下設定：\n\n步驟：\n1. 點擊【確定】打開設定\n2. 找到【星進教育 i-STAR】\n3. 設定【無限制】或【不優化】\n4. 返回 App")
            .setPositiveButton("確定", (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            })
            .setNegativeButton("稍後再說", null)
            .setCancelable(false)
            .show();
    }

    private void injectFcmToken(WebView view) {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) return;
                String token = task.getResult().replace("'", "\\'");
                String js = "if(typeof window.__registerFCMToken==='function'){window.__registerFCMToken('" + token + "');}";
                view.post(() -> view.evaluateJavascript(js, null));
            });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "istar_notifications", "星進教育通知", NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("星進教育 App 通知");
            channel.enableLights(true);
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}