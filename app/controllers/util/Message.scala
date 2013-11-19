package controllers.util

import java.util.{TimeZone, Date}
import models.{Mission, Device}
import models.spatial.TrajectoryPoint
import play.api.libs.json.JsArray

/**
 * An object that defines the messages that are passed between the actors handling the insertion process
 */
object Message {

  /**
   * Insert a compass log in database.
   * This message is ONLY used when compass logs are inserted via text file import.
   * @param batchId The id of the insertion batch
   * @param trajectoryPoints The list of trajectory points
   * @param ts The timestamp of the log
   * @param compassVal The value of the compass
   */
  case class InsertCompassLog (batchId: String, trajectoryPoints: List[TrajectoryPoint], ts: Date, compassVal: Double)

  /**
   * Insert a GPS log in database
   * @param batchId The if of the insertion batch
   * @param missionId The id of the mission
   * @param ts The timestamp of the log
   * @param latitude The latitude value
   * @param longitude The longitude value
   * @param altitude The altitude value
   * @param heading The heading value (optional)
   */
  case class InsertGpsLog (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double, heading: Option[Double])

  /**
   * Insert a point of interest in database
   * @param batchId The id of the insertion batch
   * @param missionId The id of the mission
   * @param ts The timestamp of the log
   * @param latitude The latitude value
   * @param longitude The longitude value
   * @param altitude The altitude value
   */
  case class InsertPointOfInterest (batchId: String, missionId: Long, ts: Date, latitude: Double, longitude: Double, altitude: Double)

  /**
   * Insert a linestring (in DB) for a trajectory
   * @param missionId The id of the mission
   */
  case class InsertTrajectoryLinestring (missionId: Long)

  /**
   * Insert a device in database
   * @param device The device to insert
   * @param missionId The id of the mission the device is linked to
   */
  case class InsertDevice (device: Device, missionId: Long)

  /**
   * Insert a mission in database
   * @param departureTime The departure time (datetime)
   * @param timezone The timezone of the mission
   * @param vehicleName The name of the vehicle of this mission
   * @param devices The devices that are linked to this mission
   */
  case class InsertMission (departureTime: Date, timezone: String, vehicleName: String, devices: JsArray)

  /**
   * Insert a device log (data from device) in database
   * This is ONLY used for devices that return a single value
   * @param batchId The id of the insertion batch
   * @param missionId The id of the mission
   * @param ts The timestamp of the device log
   * @param value The value of the device
   * @param deviceAddress The address of the device
   */
  case class InsertDeviceLog (batchId: String, missionId: Long, ts: Date, value: Double, deviceAddress: String)

  /**
   * Skip log we are not interested in.
   * This message is actually used to make the insertion progress bar move correctly if a file
   * contains logs we are not interested in (e.g. GPS errors in GPS log file)
   * @param batchId The id of the insertion batch
   */
  case class SkipLog (batchId: String)

  /**
   * Start to work on an insertion batch.
   * This message is ONLY used when data is inserted via text file import
   * @param batchId The id of the insertion batch to process
   * @param dataType The type of data to process
   * @param missionId The id of the mission
   */
  case class Work (batchId: String, dataType: String, missionId: Long)

  /**
   * Get the progress of an insertion batch
   * @param batchId The id of the insertion batch
   */
  case class GetInsertionProgress (batchId: String)

  /**
   * Set an insertion batch (when importing text file)
   * @param batchId The id of the insertion batch
   * @param filename The name of the imported file
   * @param dataType The type of data to process
   * @param lines The text lines contained in the file
   * @param devices The devices defined in the configuration file (also imported)
   * @param missionId The id of the mission
   */
  case class SetInsertionBatch (batchId: String, filename: String, dataType: String, lines: Array[String], devices: Map[String, Device], missionId: Long)

  /**
   * Set an insertion batch (when receiving data via POST request)
   * @param batchId The id of the insertion process
   * @param dataType The type of data to process
   * @param nbOfItems The nb of items to process
   */
  case class SetInsertionBatchJson (batchId: String, dataType: String, nbOfItems: Int)
}
