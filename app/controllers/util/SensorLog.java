package controllers.util;

import models.Sensor;
import models.spatial.GpsLog;

import java.util.Date;

/**
 * Common getters for all sensor logs
 */
public interface SensorLog {
    Long getId();
    Sensor getSensor();
    Date getTimestamp();
}
