package com.entry.push;

/** What the user sees in the OS notification; {@code url} is the app route opened on click. */
public record PushMessage(String title, String body, String url, String tag) {
}
