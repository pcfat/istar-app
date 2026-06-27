// fcm-init.js - Firebase Cloud Messaging initialization for Capacitor
// This file runs on app startup to register FCM token

(async function initFCM() {
    try {
        alert('FCM init started');
        
        // Check if running in Capacitor (native app)
        if (!window.Capacitor || !window.Capacitor.isNativePlatform()) {
            alert('Not running in native Capacitor app');
            console.log('Not running in native Capacitor app');
            return;
        }

        alert('Capacitor detected, platform: ' + Capacitor.getPlatform());
        console.log('FCM initialization started');

        const { FirebaseMessaging } = await import('@capacitor-firebase/messaging');
        alert('FirebaseMessaging imported');

        // Request permission
        console.log('Requesting FCM permissions...');
        alert('Requesting FCM permissions...');
        const { receive } = await FirebaseMessaging.requestPermissions();
        alert('Permission result: ' + receive);
        
        if (receive !== 'granted') {
            alert('FCM permission denied: ' + receive);
            console.log('FCM permission denied');
            return;
        }

        console.log('FCM permission granted');
        alert('FCM permission granted');

        // Get FCM token
        const { token } = await FirebaseMessaging.getToken();
        if (!token) {
            alert('No FCM token received');
            console.log('No FCM token received');
            return;
        }

        alert('FCM Token received: ' + token.substring(0, 20) + '...');
        console.log('FCM Token received:', token.substring(0, 20) + '...');

        // Store token locally
        localStorage.setItem('fcm_token', token);

        // Register token with server
        const response = await fetch('/app/api/register_fcm_token.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fcm_token: token,
                device_type: Capacitor.getPlatform() // 'ios' or 'android'
            }),
            credentials: 'include'
        });

        if (response.ok) {
            alert('Token registered with server');
            console.log('FCM token registered with server');
        } else {
            alert('Failed to register token: ' + response.status);
            console.error('Failed to register FCM token with server:', response.status);
        }

        // Listen for foreground messages
        FirebaseMessaging.addListener('notificationReceived', (notification) => {
            console.log('FCM foreground notification:', notification);
            // Show in-app notification
            if (notification.notification) {
                const { title, body } = notification.notification;
                if (title || body) {
                    alert((title || '') + '\n' + (body || ''));
                }
            }
        });

        // Handle notification tap
        FirebaseMessaging.addListener('notificationActionPerformed', (action) => {
            console.log('FCM notification tapped:', action);
            // Navigate to URL if provided
            if (action.notification && action.notification.data && action.notification.data.url) {
                window.location.href = action.notification.data.url;
            }
        });

    } catch (e) {
        alert('FCM init error: ' + e.message);
        console.error('FCM init error:', e);
    }
})();
