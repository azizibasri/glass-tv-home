package com.gathin.carhome;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Android shares which app is playing music (title, artist, cover, play/pause) only with apps that have
 * notification access; declaring this listener is how Glass Car gets it. Granted at setup with:
 * cmd notification allow_listener com.gathin.carhome/com.gathin.carhome.MediaListener
 * It also reads the notification of the unit's own radio app, and only that one, to show the station
 * (frequency and channel number) in the music widget: head-unit radio apps often have no media session.
 */
public final class MediaListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) for (StatusBarNotification n : all) onNotificationPosted(n);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !RadioInfo.isRadioPackage(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        Bundle e = n == null ? null : n.extras;
        if (e == null) return;
        RadioState.notification(sbn.getPackageName(),
                e.getCharSequence(Notification.EXTRA_TITLE),
                e.getCharSequence(Notification.EXTRA_TEXT),
                e.getCharSequence(Notification.EXTRA_SUB_TEXT),
                e.getCharSequence(Notification.EXTRA_INFO_TEXT),
                e.getCharSequence(Notification.EXTRA_BIG_TEXT));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && RadioInfo.isRadioPackage(sbn.getPackageName())) RadioState.removed(sbn.getPackageName());
    }
}
