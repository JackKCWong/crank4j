package com.danielflower.crank4j.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Action {
    Logger log = LoggerFactory.getLogger(Action.class);
    void run() throws Exception;

    static void swallowException(Action action) {
        try {
            action.run();
        } catch (Exception e) {
            log.info("Ignoring exception: " + e.getMessage());
        }
    }
}
