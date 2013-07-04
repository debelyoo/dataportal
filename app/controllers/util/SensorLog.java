package controllers.util;

import models.Sensor;
import models.spatial.GpsLog;

import java.util.Date;

public interface SensorLog {
    Long getId();
    Sensor getSensor();
    Date getTimestamp();
}
