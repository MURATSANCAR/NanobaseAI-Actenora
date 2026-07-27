package com.nanobaseai.actenora.notification.api;

import java.util.List;

public record UserNotificationListView(
        List<UserNotificationView> items,
        int unreadCount
) {
}
