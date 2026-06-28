// push-init.js - Firebase Cloud Messaging initialization for Capacitor
// This file runs on app startup to register FCM token

(async function initFCM() {
    try {
        console.log('FCM: Initialization started');
        
        // Check if running in Capacitor (native app)
        if (!window.Capacitor || !window.Capacitor.isNativePlatform()) {
            console.log('FCM: Not running in native Capacitor app');
            return;
        }

        // More accurate platform detection
        let platform = 'unknown';
        const capacitorPlatform = window.Capacitor.getPlatform();
        const userAgent = navigator.userAgent.toLowerCase();
        
        // Check user agent as backup
        if (capacitorPlatform === 'ios' || userAgent.includes('iphone') || userAgent.includes('ipad')) {
            platform = 'ios';
        } else if (capacitorPlatform === 'android' || userAgent.includes('android')) {
            platform = 'android';
        } else {
            platform = capacitorPlatform; // fallback to Capacitor detection
        }
        
        console.log('FCM: Platform detected =', platform, '(Capacitor=' + capacitorPlatform + ', UA=' + (userAgent.includes('android') ? 'android' : userAgent.includes('iphone') ? 'ios' : 'other') + ')');

        const { FirebaseMessaging } = await import('@capacitor-firebase/messaging');
        console.log('FCM: FirebaseMessaging imported');

        // Request permission
        console.log('FCM: Requesting permissions...');
        const { receive } = await FirebaseMessaging.requestPermissions();
        console.log('FCM: Permission result =', receive);
        
        if (receive !== 'granted') {
            console.log('FCM: Permission denied');
            return;
        }

        console.log('FCM: Permission granted');

        // Get FCM token
        const { token } = await FirebaseMessaging.getToken();
        if (!token) {
            console.log('FCM: No token received');
            return;
        }

        console.log('FCM: Token received:', token.substring(0, 20) + '...');

        // Store token locally
        localStorage.setItem('fcm_token', token);

        // Register token with server
        console.log('FCM: Registering token with server... device_type=' + platform);
        const response = await fetch('/app/api/register_fcm_token.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fcm_token: token,
                device_type: platform
            }),
            credentials: 'include'
        });

        if (response.ok) {
            console.log('FCM: Token registered with server');
        } else {
            console.error('FCM: Failed to register token:', response.status);
        }

        // Listen for foreground messages
        FirebaseMessaging.addListener('notificationReceived', (notification) => {
            console.log('FCM: Foreground notification:', notification);
            // Show alert for foreground notifications
            if (notification.notification) {
                const { title, body } = notification.notification;
                if (title || body) {
                    alert((title || '') + '\n' + (body || ''));
                }
            }
        });

        // Handle notification tap
        FirebaseMessaging.addListener('notificationActionPerformed', (action) => {
            console.log('FCM: Notification tapped:', action);
            // Navigate to URL if provided
            if (action.notification && action.notification.data && action.notification.data.url) {
                window.location.href = action.notification.data.url;
            }
        });

        console.log('FCM: Initialization completed successfully');

    } catch (e) {
        console.error('FCM init error:', e);
    }
})();
