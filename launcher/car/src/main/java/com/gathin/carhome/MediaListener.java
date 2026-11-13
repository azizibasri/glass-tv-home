package com.gathin.carhome;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose. Android shares which app is playing music (title, artist, cover, play/pause) only with
 * apps that have notification access; declaring this listener is how Glass Car gets it. It reads no
 * notifications. Granted at setup with:
 * cmd notification allow_listener com.gathin.carhome/com.gathin.carhome.MediaListener
 */
public final class MediaListener extends NotificationListenerService {
}
