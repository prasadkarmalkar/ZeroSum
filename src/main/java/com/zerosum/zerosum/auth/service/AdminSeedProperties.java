package com.zerosum.zerosum.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for seeding the admin user on startup, bound from {@code zerosum.admin.*}.
 *
 * @param enabled  whether to seed the admin at all; off unless configured (on for dev and test)
 * @param email    the admin's email; required when enabled
 * @param password the admin's raw password, hashed before saving; required when enabled
 */
@ConfigurationProperties(prefix = "zerosum.admin")
public record AdminSeedProperties(boolean enabled, String email, String password) {

	/** Never print the password, e.g. in startup logs or actuator output. */
	@Override
	public String toString() {
		return "AdminSeedProperties[enabled=" + enabled + ", email=" + email + ", password=****]";
	}

}
