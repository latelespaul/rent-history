package com.rentrix.rentrixserver.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a write endpoint that honours an optional {@code Idempotency-Key} header (§12.1):
 * a retry with the same key and the same request replays the first successful response
 * instead of running the write again. Used on {@code POST /tenancies}, {@code POST .../reviews}
 * and {@code POST /reports}. See {@link IdempotencyFilter}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
