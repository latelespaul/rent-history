package com.rentrix.rentrixserver.admin.queue;

import java.util.List;
import java.util.Map;

/** {@code GET /admin/queue}: the cards, newest first, and how many wait per type under the same city filter. */
public record QueueView(List<QueueItem> items, Map<QueueType, Long> counts) {
}
