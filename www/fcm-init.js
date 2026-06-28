// fcm-init.js - Firebase Cloud Messaging initialization for Capacitor
// This file runs on app startup to register FCM token

(async function initFCM() {
    try {
        // Check if running in Capacitor (native app)
        if (!window.Capacitor || !window.Capacitor.isNativePlatform()) {
            alert('DEBUG: Not running in Capacitor native app');
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
        
        alert('DEBUG: Platform detected = ' + platform + ' (Capacitor=' + capacitorPlatform + ', UA=' + (userAgent.includes('android') ? 'android' : userAgent.includes('iphone') ? 'ios' : 'other') + ')');

        const { FirebaseMessaging } = await import('@capacitor-firebase/messaging');
        alert('DEBUG: FirebaseMessaging imported successfully');

        // Request permission
        alert('DEBUG: Requesting FCM permissions...');
        const { receive } = await FirebaseMessaging.requestPermissions();
        alert('DEBUG: Permission result = ' + receive);
        
        if (receive !== 'granted') {
            alert('DEBUG: FCM permission denied! receive=' + receive);
            console.log('FCM permission denied');
            return;
        }

        // Get FCM token
        alert('DEBUG: Getting FCM token...');
        const { token } = await FirebaseMessaging.getToken();
        
        if (!token) {
            alert('DEBUG: Failed to get FCM token! token is empty');
            return;
        }

        alert('DEBUG: FCM Token received! First 30 chars: ' + token.substring(0, 30));
        console.log('FCM Token:', token.substring(0, 20) + '...');

        // Register token with server
        alert('DEBUG: Sending token to server... device_type=' + platform);
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
        alert('DEBUG: Server response status=' + response.status + ' body=' + responseText.substring(0, 100));

        // Listen for foreground messages
        FirebaseMessaging.addListener('notificationReceived', (notification) => {
            alert('DEBUG: FCM foreground notification received!');
            console.log('FCM foreground notification:', notification);
        });

        // Handle notification tap
        FirebaseMessaging.addListener('notificationActionPerformed', (action) => {
            alert('DEBUG: FCM notification tapped!');
            console.log('FCM notification tapped:', action);
        });

        alert('DEBUG: FCM initialization completed successfully!');

    } catch (e) {
        alert('DEBUG: FCM init error! ' + e.message);
        console.warn('FCM init error:', e);
    }
})();
