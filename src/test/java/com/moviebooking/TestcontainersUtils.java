package com.moviebooking;

import org.testcontainers.DockerClientFactory;

public final class TestcontainersUtils {

    private TestcontainersUtils() {}

    public static boolean dockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
