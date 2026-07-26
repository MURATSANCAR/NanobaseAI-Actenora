package com.nanobaseai.actenora;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;

/**
 * Spring profile helpers for production vs compose/K8s fixture acceptance.
 */
public final class ActenoraProfiles {

    public static final String PROD_FIXTURE = "prod-fixture";

    private ActenoraProfiles() {
    }

    public static boolean isProductionProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }

    public static boolean isProdFixtureProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals(PROD_FIXTURE));
    }

    /** Production profile without the documented {@code prod-fixture} acceptance override. */
    public static boolean isStrictProduction(Environment environment) {
        return isProductionProfile(environment) && !isProdFixtureProfile(environment);
    }
}
