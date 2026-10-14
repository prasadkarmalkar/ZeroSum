package com.zerosum.zerosum.common.event;

import java.util.Objects;

/**
 * Published by the auth module after a new user has been registered.
 * <p>
 * Lives in common so other modules (e.g. account) can listen for it without depending on auth.
 * It carries only the id, not the {@code User} entity, for the same reason.
 */
public record UserRegisteredEvent(Long userId) {

	public UserRegisteredEvent {
		Objects.requireNonNull(userId, "userId must not be null");
	}

}
