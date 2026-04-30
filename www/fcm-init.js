// fcm-init.js - Firebase Cloud Messaging initialization for Capacitor
// This file runs on app startup to register FCM token

(async function initFCM() {
    try {
        // Check if running in Capacitor (native app)
        if (!window.Capacitor || !window.Capacitor.isNativePlatform()) return;

        const { FirebaseMessaging } = await import('@capacitor-firebase/messaging');

        // Request permission
        const { receive } = await FirebaseMessaging.requestPermissions();
        if (receive !== 'granted') {
            console.log('FCM permission denied');
            return;
        }

        // Get FCM token
        const { token } = await FirebaseMessaging.getToken();
        if (!token) return;

        console.log('FCM Token:', token.substring(0, 20) + '...');

        // Register token with server
        await fetch('/app/api/register_fcm_token.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fcm_token: token,
                device_type: 'android'
            }),
            credentials: 'include'
        });

        // Listen for foreground messages
        FirebaseMessaging.addListener('notificationReceived', (notification) => {
            console.log('FCM foreground notification:', notification);
            // Could show in-app notification banner here
        });

        // Handle notification tap
        FirebaseMessaging.addListener('notificationActionPerformed', (action) => {
            console.log('FCM notification tapped:', action);
        });

    } catch (e) {
        console.warn('FCM init error:', e);
    }
})();
