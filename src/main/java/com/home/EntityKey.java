package com.home;

public record EntityKey<T>(Class<T> type, Object id) {
}
