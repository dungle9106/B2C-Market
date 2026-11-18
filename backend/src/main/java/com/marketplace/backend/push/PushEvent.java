package com.marketplace.backend.push;

import java.util.Collection;

/**
 * A notification that should also reach the users' devices as a push notification. Published together with the
 * in-app notification; PushService sends it after the transaction has committed.
 *
 * @param link frontend route opened when the push is tapped (may be null)
 */
public record PushEvent(Collection<Long> userIds, String type, String title, String body, String link, String imageUrl) {
}
