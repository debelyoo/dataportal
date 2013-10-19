package controllers.util

import java.util.{TimeZone, Date}
import models.{Mission, Device}
import models.spatial.TrajectoryPoint

object Message {
  // Insertion
  case class InsertTemperatureLog (batchId: String, missionId: Long, ts: Date, device: Device, tempVal: Double)
  case class InsertCompassLog (batchId: String, trajectoryPoints: List[TrajectoryPoint], ts: Date, compassVal: Double)
  case class InsertWindLog (batchId: String, missionId: Long, ts: Date, device: Device, windVal: Double)
  case class InsertGpsLog (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double, heading: Option[Double])
  case class InsertPointOfInterest (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double)
  case class InsertUlmTrajectory (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double)
  case class InsertRadiometerLog (batchId: String, missionId: Long, ts: Date, device: Device, radiometerVal: Double)
  case class InsertDevice (device: Device)
  case class InsertMission (departureTime: Date, timezone: String, vehicleName: String)
  case class SkipLog (batchId: String)
  // Spatialization
  /*case class SpatializeTemperatureLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeWindLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeRadiometerLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeCompassLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  */
  case class NoCloseLog (batchId: String)

  case class Work (batchId: String, dataType: String, missionId: Long)
  //case class GetSpatializationProgress (batchId: String)
  //case class SetSpatializationBatch (batchId: String, gpsLogs: List[GpsLog], devices: List[Device],sensorLogs: List[SensorLog])
  case class GetInsertionProgress (batchId: String)
  case class SetInsertionBatch (batchId: String, filename: String, dataType: String, lines: Array[String], devices: Map[String, Device], missionId: Long)
  case class SetInsertionBatchJson (batchId: String, dataType: String, nbOfItems: Int)
}
