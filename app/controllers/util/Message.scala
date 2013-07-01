package controllers.util

import java.util.Date
import models.Sensor
import models.spatial.{TemperatureLog, GpsLog}

object Message {
  // Insertion
  case class InsertTemperatureLog (ts: Date, sensor: Sensor, tempVal: Double)
  case class InsertCompassLog (ts: Date, sensor: Sensor, compassVal: Double)
  case class InsertWindLog (ts: Date, sensor: Sensor, windVal: Double)
  case class InsertGpsLog (ts: Date, sensor: Sensor, latitude: Double, longitude: Double)
  case class InsertRadiometerLog (ts: Date, sensor: Sensor, radiometerVal: Double)
  case class InsertSensor (sensor: Sensor)
  // Spatialization
  case class SpatializeTemperatureLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeWindLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeRadiometerLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class NoCloseLog (batchId: String)
  case class Work (batchId: String, dataType: String)
  //case class Test (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class GetSpatializationProgress (batchId: String)
  case class SetSpatializationBatch (batchId: String, gpsLogs: List[GpsLog], sensors: List[Sensor],sensorLogs: List[SensorLog])
}
