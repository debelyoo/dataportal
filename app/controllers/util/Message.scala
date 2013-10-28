package controllers.util

import java.util.{TimeZone, Date}
import models.{Mission, Device}
import models.spatial.TrajectoryPoint
import play.api.libs.json.JsArray

object Message {
  // Insertion
  //case class InsertTemperatureLog (batchId: String, missionId: Long, ts: Date, device: Device, tempVal: Double)
  case class InsertCompassLog (batchId: String, trajectoryPoints: List[TrajectoryPoint], ts: Date, compassVal: Double)
  //case class InsertWindLog (batchId: String, missionId: Long, ts: Date, device: Device, windVal: Double)
  case class InsertGpsLog (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double, heading: Option[Double])
  case class InsertPointOfInterest (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double)
  case class InsertUlmTrajectory (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double)
  //case class InsertRadiometerLog (batchId: String, missionId: Long, ts: Date, device: Device, radiometerVal: Double)
  case class InsertDevice (device: Device, missionId: Long)
  case class InsertMission (departureTime: Date, timezone: String, vehicleName: String, devices: JsArray)
  case class InsertSensorLog (batchId: String, missionId: Long, ts: Date, tempVal: Double, deviceAddress: String)
  case class SkipLog (batchId: String)

  case class Work (batchId: String, dataType: String, missionId: Long)
  case class GetInsertionProgress (batchId: String)
  case class SetInsertionBatch (batchId: String, filename: String, dataType: String, lines: Array[String], devices: Map[String, Device], missionId: Long)
  case class SetInsertionBatchJson (batchId: String, dataType: String, nbOfItems: Int)
}
