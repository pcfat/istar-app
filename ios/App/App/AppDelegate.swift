import UIKit
import Capacitor
import FirebaseCore
import FirebaseMessaging

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    var fcmToken: String?
    var retryCount = 0
    var retryTimer: Timer?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        // Debug alert: App started
        showAlert("App started 🚀")
        
        // Initialize Firebase (required for FCM)
        do {
            FirebaseApp.configure()
            showAlert("Firebase initialized ✅")
        } catch {
            showAlert("Firebase init failed ❌\n\(error.localizedDescription)")
            return true
        }
        
        // Get FCM token and inject to WebView (similar to Android MainActivity)
        // Use retry mechanism to ensure WebView is ready
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
            self.injectFCMToken()
        }
        
        return true
    }
    
    private func showAlert(_ message: String) {
        DispatchQueue.main.async {
            guard let topController = UIApplication.shared.windows.first?.rootViewController else { return }
            
            let alert = UIAlertController(title: "Debug", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            
            // Find the topmost view controller
            var presentingController = topController
            while let presented = presentingController.presentedViewController {
                presentingController = presented
            }
            
            presentingController.present(alert, animated: true)
        }
    }
    
    private func injectFCMToken() {
        showAlert("Getting FCM token...")
        
        Messaging.messaging().token { token, error in
            if let error = error {
                self.showAlert("FCM token error ❌\n\(error.localizedDescription)")
                return
            }
            
            guard let token = token else {
                self.showAlert("No FCM token ❌")
                return
            }
            
            self.fcmToken = token
            let shortToken = String(token.prefix(20))
            self.showAlert("FCM Token received ✅\n\(shortToken)...")
            
            // Try to inject immediately
            self.tryInjectToken()
            
            // Also setup retry timer
            self.retryTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] timer in
                guard let self = self else {
                    timer.invalidate()
                    return
                }
                
                if self.retryCount >= 10 {
                    self.showAlert("Failed to inject after 10 retries ❌")
                    timer.invalidate()
                    return
                }
                
                self.retryCount += 1
                self.tryInjectToken()
            }
        }
    }
    
    private func tryInjectToken() {
        guard let token = fcmToken else { return }
        
        DispatchQueue.main.async {
            if let bridge = (UIApplication.shared.delegate as? AppDelegate)?.window?.rootViewController as? CAPBridgeViewController,
               let webView = bridge.webView {
                
                // Check if __registerFCMToken is defined
                webView.evaluateJavaScript("typeof window.__registerFCMToken === 'function'") { result, error in
                    if let isDefined = result as? Bool, isDefined {
                        // Function exists, inject token
                        let js = "window.__registerFCMToken('\(token.replacingOccurrences(of: "'", with: "\\'"))');"
                        webView.evaluateJavaScript(js) { result, error in
                            if let error = error {
                                self.showAlert("Inject error ❌\n\(error.localizedDescription)")
                            } else {
                                self.showAlert("Token injected ✅\nRetry: \(self.retryCount)")
                                self.retryTimer?.invalidate()
                            }
                        }
                    } else {
                        if self.retryCount == 0 {
                            self.showAlert("Waiting for WebView...\nRetry \(self.retryCount)")
                        }
                    }
                }
            } else {
                if self.retryCount == 0 {
                    self.showAlert("WebView not ready...\nRetry \(self.retryCount)")
                }
            }
        }
    }

    func applicationWillResignActive(_ application: UIApplication) {
        // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
        // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
        // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        // Called when the app was launched with a url. Feel free to add additional processing here,
        // but if you want the App API to support tracking app url opens, make sure to keep this call
        return ApplicationDelegateProxy.shared.application(app, open: url, options: options)
    }

    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        // Called when the app was launched with an activity, including Universal Links.
        // Feel free to add additional processing here, but if you want the App API to support
        // tracking app url opens, make sure to keep this call
        return ApplicationDelegateProxy.shared.application(application, continue: userActivity, restorationHandler: restorationHandler)
    }

}
