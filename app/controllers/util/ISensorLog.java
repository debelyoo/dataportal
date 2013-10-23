package controllers.util;

import models.Device;

import java.util.Date;

/**
 * Common getters for all sensor logs
 */
public interface ISensorLog {
    Long getId();
    Device getDevice();
    Date getTimestamp();
}
