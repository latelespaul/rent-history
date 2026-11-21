package com.rentrix.rentrixserver.common.idempotency;

public enum IdempotencyState {
	/** The first request with this key is still running. */
	IN_PROGRESS,
	/** The first request succeeded; its response is stored for replay. */
	COMPLETED
}
