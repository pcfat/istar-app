import UIKit
import Capacitor
import FirebaseCore
import FirebaseMessaging
import UserNotifications

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    var window: UIWindow?
    var fcmToken: String?
    var retryCount = 0
    var retryTimer: Timer?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        // Initialize Firebase (required for FCM)
        do {
            FirebaseApp.configure()
            print("Firebase initialized")
        } catch {
            print("Firebase init failed: \(error.localizedDescription)")
            return true
        }
        
        // Set FCM delegate
        Messaging.messaging().delegate = self
        
        // Request push notification permission (required for APNs token)
        UNUserNotificationCenter.current().delegate = self
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(options: authOptions) { granted, error in
            DispatchQueue.main.async {
                if granted {
                    print("Push permission granted")
                    // Register for remote notifications (get APNs token)
                    application.registerForRemoteNotifications()
                } else {
                    print("Push permission denied: \(error?.localizedDescription ?? "User declined")")
                }
            }
        }
        
        return true
    }
    
    // APNs token received
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let tokenParts = deviceToken.map { data in String(format: "%02.2hhx", data) }
        let token = tokenParts.joined()
        print("APNs token received: \(String(token.prefix(20)))...")
        
        // Pass APNs token to Firebase
        Messaging.messaging().apnsToken = deviceToken
        
        // Now get FCM token
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            self.injectFCMToken()
        }
    }
    
    // APNs registration failed
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNs registration failed: \(error.localizedDescription)")
    }
    
    // FCM token refresh callback
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        print("FCM token refreshed: \(String(token.prefix(20)))...")
        self.fcmToken = token
    }
    
    private func injectFCMToken() {
        print("Getting FCM token...")
        
        Messaging.messaging().token { token, error in
            if let error = error {
                print("FCM token error: \(error.localizedDescription)")
                return
            }
            
            guard let token = token else {
                print("No FCM token")
                return
            }
            
            self.fcmToken = token
            let shortToken = String(token.prefix(20))
            print("FCM Token received: \(shortToken)...")
            
            // Directly POST token to server (skip WebView injection)
            self.registerTokenWithServer(token: token)
        }
    }
    
    private func registerTokenWithServer(token: String) {
        print("Registering token with server...")
        
        guard let url = URL(string: "https://s.istars.hk/app/api/register_fcm_token.php") else {
            print("Invalid server URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "fcm_token": token,
            "device_type": "ios"
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        } catch {
            print("Failed to encode request: \(error.localizedDescription)")
            return
        }
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("Server request failed: \(error.localizedDescription)")
                    return
                }
                
                guard let httpResponse = response as? HTTPURLResponse else {
                    print("Invalid server response")
                    return
                }
                
                if httpResponse.statusCode == 200 {
                    print("Token registered. Server returned 200 OK")
                } else {
                    print("Server error. Status code: \(httpResponse.statusCode)")
                }
            }
        }
        
        task.resume()
    }
    
    // Show alert helper (only for foreground notifications)
    private func showAlert(_ message: String) {
        DispatchQueue.main.async {
            guard let topController = UIApplication.shared.windows.first?.rootViewController else { return }
            
            let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            
            // Find the topmost view controller
            var presentingController = topController
            while let presented = presentingController.presentedViewController {
                presentingController = presented
            }
            
            presentingController.present(alert, animated: true)
        }
    }
    
    // Handle foreground notifications
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let title = notification.request.content.title
        let body = notification.request.content.body
        showAlert("📬 \(title)\n\(body)")
        completionHandler([[.banner, .sound]])
    }
    
    // Handle notification tap
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        print("Notification tapped:", userInfo)
        completionHandler()
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
