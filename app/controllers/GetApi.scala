package controllers

import controllers.util._
import models.spatial._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.Logger
import models._
import scala.Some
import controllers.modelmanager.{DataLogManager}
import java.util.TimeZone

trait GetApi extends ResponseFormatter {
  this: Controller =>

  /**
   * Handle data request with query params  (ex: /api/data?data_type=temperature&from_date=20130613-150000&to_date=20130613-170000&sensor_id=4&geo_only=true)
   * @return AN HTTP response containing data (formatted as XML or JSON)
   */
  def getData = Action {
    implicit request =>
      try {
        val map = request.queryString.map { case (k,v) => k -> v.mkString }
        val format = map.get("format").getOrElse(JSON_FORMAT) // default format is Json
        //println("[GetApi] getData() - "+ map.get("data_type").get + " <"+ format +">")
        //println(map)
        assert(map.contains("data_type"), {println("Missing data_type parameter")})
        val datatype = map.get("data_type").get
        Logger.info("[GetApi] getData() - "+ datatype + " <"+ format +">")
        assert(map.contains("mission_id"), {println("Missing mission_id parameter")})
        val missionId = map.get("mission_id").map(_.toLong).get
        val mission = DataLogManager.getById[Mission](missionId)
        assert(mission.isDefined, {println("Mission does not exist")})
        val dateFormatter = DateFormatHelper.dateTimeFormatter
        dateFormatter.setTimeZone(TimeZone.getTimeZone(mission.get.timeZone))
        //println("From Date: "+map.get("from_date")+" - time zone: "+mission.get.getTimeZone())
        val startDate = map.get("from_date").map(dateFormatter.parse(_))
        val endDate = map.get("to_date").map(dateFormatter.parse(_))
        assert(map.contains("device_id"), {println("Missing device_id parameter")})
        //val sensorIdList = map.get("sensor_id").map(_.toLong).toList // TODO handle multi Ids (by parsing string)

        val maxNb = map.get("max_nb").map(_.toInt)
        val deviceIdList = map.get("device_id").map {
          case "all" => Device.getForMission(missionId, map.get("data_type"), None).map(_.id)
          case _ => List(map.get("device_id").get.toLong)
        }.get
        val logMap = DataLogManager.getDataByMission(datatype, missionId, deviceIdList, startDate, endDate, maxNb)
        formatResponse(format, logMap)
      } catch {
        case ae: AssertionError => BadRequest
        case ex: Exception => ex.printStackTrace(); BadRequest
      }
  }

  /**
   * Get the GPS data
   * @return
   */
  /*def getGpsData = Action {
    implicit request =>
      try {
        val map = request.queryString.map { case (k,v) => k -> v.mkString }
        val format = map.get("format").getOrElse(GEOJSON_FORMAT) // default format is Geo json
        val coordinateFormat = map.get("coordinate_format").getOrElse("gps")
        //println("[GetApi] getData() - "+ map.get("data_type").get + " <"+ format +">")
        Logger.info("[GetApi] getGpsData() - <"+ format +">")
        //println(map)
        assert(map.contains("from_date"), {println("Missing from_date parameter")})
        assert(map.contains("to_date"), {println("Missing to_date parameter")})
        val startDate = DateFormatHelper.dateTimeFormatter.parse(map.get("from_date").get)
        val endDate = DateFormatHelper.dateTimeFormatter.parse(map.get("to_date").get)
        val maxNb = map.get("max_nb").map(_.toInt)
        val gpsLogs = DataLogManager.getByTimeInterval[GpsLog](startDate, endDate, false, maxNb)
        //Ok(logsAsGeoJsonLinestring(Map("gps sensor" -> gpsLogs)))
        Ok(logsAsGeoJson(Map("gps sensor" -> gpsLogs)))
      } catch {
        case ae: AssertionError => BadRequest
        case ex: Exception => ex.printStackTrace(); BadRequest
      }
  }*/

  /**
   * Get the trajectory of a mission
   * @return
   */
  def getTrajectory = Action {
    implicit request =>
      try {
        val map = request.queryString.map { case (k,v) => k -> v.mkString }
        val format = map.get("format").getOrElse(GEOJSON_FORMAT) // default format is Geo json
        val mode = map.get("mode").getOrElse(GIS_LINESTRING) // default mode is linestring
        //println("[GetApi] getData() - "+ map.get("data_type").get + " <"+ format +">")
        Logger.info("[GetApi] getTrajectory() - <"+ format +">")
        //println(map)
        assert(map.contains("mission_id"), {println("Missing mission_id parameter")})
        val missionId = map.get("mission_id").map(_.toInt)
        val maxNb = map.get("max_nb").map(_.toInt)
        val resp = if (mode == GIS_LINESTRING) {
          DataLogManager.getTrajectoryLinestring(missionId.get)
        } else {
          val trajectoryPoints = DataLogManager.getTrajectoryPoints(missionId.get, None, None, maxNb)
          pointsAsGeoJson(trajectoryPoints)
        }
        Ok(resp)
      } catch {
        case ae: AssertionError => BadRequest
        case ex: Exception => ex.printStackTrace(); BadRequest
      }
  }

  /**
   * Get the devices for a mission
   * @param missionId The id of the mission
   * @return
   */
  def getDeviceForMission(missionId: String) = Action {
    val deviceList = Device.getForMission(missionId.toLong, None, None)
    val jsList = Json.toJson(deviceList.map(d => Json.parse(d.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("devices" -> jsList, "count" -> Json.toJson(deviceList.length))))
  }

  /**
   * Get the points of interest for a mission
   * @param missionId The id of the mission
   * @return
   */
  def getPointOfInterestForMission(missionId: String) = Action {
    val poiList = DataLogManager.getPointOfInterest(missionId.toLong)
    Ok(pointsAsGeoJson(poiList))
  }


  /**
   * Get details of a particular device (used mainly for tests)
   */
  def getDeviceById(dId: String) = Action {
    val deviceMap = Device.getById(List(dId.toLong), None)
    deviceMap.get(dId.toLong).map(s => Ok(Json.toJson(s.toJson))).getOrElse(NotFound) // return Not Found if no device with id 'dId' exists
  }

  /**
   * Get the active sensors in a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @param dataType The type of the sensor to get
   * @return A list of sensors (JSON)
   */
  /*def getSensorByDatetime(startTime: String, endTime: String, dataType: Option[String] = None) = Action {
    //println("Start: "+startTime +", End: "+endTime)
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val sensorList = Sensor.getByDatetime(startDate, endDate, dataType)
    val jsList = Json.toJson(sensorList.map(s => Json.parse(s.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("sensors" -> jsList, "count" -> Json.toJson(sensorList.length))))
  }*/

  /// Getters for log details (used mainly for tests)
  def getTrajectoryPointById(tId: String) = Action {
    val logOpt = DataLogManager.getById[TrajectoryPoint](tId.toLong)
    logOpt.map(gl => Ok(gl.toGeoJson)).getOrElse(NotFound) // return Not Found if no log with id 'gId' exists
  }

  def getSensorLogById(tId: String) = Action {
    val logOpt = DataLogManager.getById[SensorLog](tId.toLong)
    logOpt.map(tl => Ok(tl.toString)).getOrElse(NotFound) // return Not Found if no log with id 'tId' exists
  }
  ///

  /**
   * Get dates (year-month-day) of measures in DB
   * @return A list of dates (JSON)
   */
  /*def getLogDates = Action {
    val dateList = DataLogManager.getDates
    val jsList = Json.toJson(dateList.map(date => Json.toJson(date)))
    Ok(Json.toJson(Map("dates" -> jsList, "count" -> Json.toJson(dateList.length))))
  }*/

  /**
   * Get dates (year-month-day) of missions in DB
   * @return A list of dates (JSON)
   */
  def getMissionDates = Action {
    val dateList = DataLogManager.getMissionDates
    val jsList = Json.toJson(dateList.map { case (missionId, dateStr, vehicle) =>
      Json.toJson(Map("id" -> Json.toJson(missionId), "departuretime" -> Json.toJson(dateStr), "vehicle" -> Json.toJson(vehicle)))
    })
    Ok(jsList)
  }

  /**
   * Get missions for a specific date
   * @return A list of missions (JSON)
   */
  def getMissionsForDate(dateStr: String) = Action {
    val date = DateFormatHelper.selectYearFormatter.parse(dateStr)
    val missionList = DataLogManager.getMissionsForDate(date)
    val jsList = Json.toJson(missionList.map(m => Json.parse(m.toJson)))
    Ok(jsList)
  }

  /**
   * Get raster data for a specific mission
   * @param idStr The id of the mission
   * @return The coordinate of the raster (JSON)
   */
  def getRasterDataForMission(idStr: String) = Action {
    val resp = DataLogManager.getRasterDataForMission(idStr.toLong)
    Ok(resp)
  }

  /**
   * Get the maximum speed for a specific mission
   * @param idStr The id of the mission
   * @return The max speed (JSON)
   */
  def getMaxSpeedAndHeadingForMission(idStr: String) = Action {
    val resp = DataLogManager.getMaxSpeedAndHeadingForMission(idStr.toLong)
    Ok(resp)
  }

  /**
   * Get the device types available
   * @return a JSON array with the device types
   */
  def getDeviceTypes = Action {
    val jsDeviceTypes = DeviceType.getAll().map(Json.toJson(_)) // uses the implicit JSON conversion in companion object
    val jsList = Json.toJson(jsDeviceTypes)
    Ok(jsList)
  }

  /**
   * A ping service util
   * @return 200 OK
   */
  def ping = Action {
    Ok
  }
}
