package com.financetracker.notifications.model;

/**
 * How a device receives push (ADR-021 §3). Web Push covers web and Electron desktop first; FCM/APNs are
 * additive transports for react-native mobile behind the same {@code push_tokens} table and publish call.
 */
public enum PushTransport {
    WEB_PUSH,
    FCM,
    APNS
}
