package com.entry.common.web;

import java.util.List;

/** One page of a newest-first list. Pass {@code nextCursor} back as {@code ?cursor=} for the next page; null means the end. */
public record CursorPage<T>(List<T> items, String nextCursor) {
}
