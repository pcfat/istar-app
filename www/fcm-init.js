// fcm-init.js - Firebase Cloud Messaging initialization for Capacitor
// This file runs on app startup to register FCM token

(async function initFCM() {
    try {
        // Check if running in Capacitor (native app)
        if (!window.Capacitor || !window.Capacitor.isNativePlatform()) {
            console.log('FCM: Not running in Capacitor native app');
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
        console.log('FCM: FirebaseMessaging imported successfully');

        // Request permission
        console.log('FCM: Requesting permissions...');
        const { receive } = await FirebaseMessaging.requestPermissions();
        console.log('FCM: Permission result =', receive);
        
        if (receive !== 'granted') {
            console.log('FCM: Permission denied');
            return;
        }

        // Get FCM token
        console.log('FCM: Getting token...');
        const { token } = await FirebaseMessaging.getToken();
        
        if (!token) {
            console.log('FCM: Failed to get token');
            return;
        }

        console.log('FCM: Token received! First 30 chars:', token.substring(0, 30));

        // Register token with server
        console.log('FCM: Sending token to server... device_type=' + platform);
        const response = await fetch('/app/api/register_fcm_token.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fcm_token: token,
                device_type: platform
            }),
            credentials: 'include'
        });

        const responseText = await response.text();
        console.log('FCM: Server response status=' + response.status, 'body=' + responseText.substring(0, 100));

        // Listen for foreground messages
        FirebaseMessaging.addListener('notificationReceived', (notification) => {
            console.log('FCM: Foreground notification received!', notification);
        });

        // Handle notification tap
        FirebaseMessaging.addListener('notificationActionPerformed', (action) => {
            console.log('FCM: Notification tapped!', action);
        });

        console.log('FCM: Initialization completed successfully!');

    } catch (e) {
        console.error('FCM init error:', e);
    }
})();
