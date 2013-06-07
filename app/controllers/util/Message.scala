package controllers.util

import java.util.Date
import models.Sensor

object Message {
  case class InsertTemperatureLog (ts: Date, sensor: Sensor, tempVal: Double)
  case class InsertCompassLog (ts: Date, sensor: Sensor, compassVal: Double)
  case class InsertWindLog (ts: Date, sensor: Sensor, windVal: Double)
  case class InsertGpsLog (ts: Date, sensor: Sensor, latitude: Double, longitude: Double)
  case class InsertRadiometerLog (ts: Date, sensor: Sensor, radiometerVal: Double)
  case class InsertSensor (sensor: Sensor)
}
