package controllers.util

import java.util.Date
import models.Device
import models.spatial.{GpsLog}

object Message {
  // Insertion
  case class InsertTemperatureLog (batchId: String, ts: Date, device: Device, tempVal: Double)
  case class InsertCompassLog (batchId: String, ts: Date, device: Device, compassVal: Double)
  case class InsertWindLog (batchId: String, ts: Date, device: Device, windVal: Double)
  case class InsertGpsLog (batchId: String, ts: Date, setNumber: Int, device: Device, latitude: Double, longitude: Double)
  case class InsertRadiometerLog (batchId: String, ts: Date, device: Device, radiometerVal: Double)
  case class InsertDevice (device: Device)
  case class SkipLog (batchId: String)
  // TEST
  case class InsertGpsLogElemo (batchId: String, ts: Date, setNumber: Int, device: Device, latitude: Double, longitude: Double)
  case class InsertTemperatureLogElemo (batchId: String, ts: Date, device: Device, tempVal: Double)
  // Spatialization
  case class SpatializeTemperatureLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeWindLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeRadiometerLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class SpatializeCompassLog (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class NoCloseLog (batchId: String)
  case class Work (batchId: String, dataType: String)
  //case class Test (batchId: String, gpsLog: GpsLog, sensorLog: SensorLog)
  case class GetSpatializationProgress (batchId: String)
  case class SetSpatializationBatch (batchId: String, gpsLogs: List[GpsLog], devices: List[Device],sensorLogs: List[SensorLog])
  case class GetInsertionProgress (batchId: String)
  case class SetInsertionBatch (batchId: String, filename: String, dataType: String, lines: Array[String], devices: Map[String, Device])
}
