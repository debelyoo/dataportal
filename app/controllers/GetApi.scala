package controllers

import controllers.util._
import models.spatial._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.Logger
import models._
import scala.Some
import controllers.modelmanager.{DeviceManager, DataLogManager}

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
        val missionId = map.get("mission_id").map(_.toLong)
        val startDate = map.get("from_date").map(DateFormatHelper.dateTimeFormatter.parse(_))
        val endDate = map.get("to_date").map(DateFormatHelper.dateTimeFormatter.parse(_))
        assert(map.contains("device_id"), {println("Missing device_id parameter")})
        //val geoOnly = map.get("geo_only").getOrElse("true").toBoolean
        //val sensorIdList = map.get("sensor_id").map(_.toLong).toList // TODO handle multi Ids (by parsing string)

        val maxNb = map.get("max_nb").map(_.toInt)
        val deviceIdList = map.get("device_id").map {
          case "all" => DeviceManager.getForMission(missionId.get, map.get("data_type")).map(_.getId.toLong)
          case _ => List(map.get("device_id").get.toLong)
        }.get
        val logMap = DataLogManager.getDataByMission(datatype, missionId.get, deviceIdList, maxNb)
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
          val trajectoryPoints = DataLogManager.getTrajectoryPoints(missionId.get, maxNb)
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
    val deviceList = DeviceManager.getForMission(missionId.toLong, None)
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
    //val jsList = Json.toJson(poiList.map(d => Json.parse(d.toGeoJson)))
    // build return JSON obj with array and count
    //Ok(Json.toJson(Map("points_of_interest" -> jsList, "count" -> Json.toJson(poiList.length))))
  }


  /**
   * Get details of a particular device (used mainly for tests)
   */
  def getDeviceById(dId: String) = Action {
    val deviceMap = DeviceManager.getById(List(dId.toLong), None)
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
  /*def getGpsLogById(gId: String) = Action {
    val logOpt = DataLogManager.getById[GpsLog](gId.toLong)
    logOpt.map(gl => Ok(gl.toString)).getOrElse(NotFound) // return Not Found if no log with id 'gId' exists
  }*/
  def getCompassLogById(cId: String) = Action {
    val logOpt = DataLogManager.getById[CompassLog](cId.toLong)
    logOpt.map(cl => Ok(cl.toString)).getOrElse(NotFound) // return Not Found if no log with id 'cId' exists
  }

  def getTemperatureLogById(tId: String) = Action {
    val logOpt = DataLogManager.getById[TemperatureLog](tId.toLong)
    logOpt.map(tl => Ok(tl.toString)).getOrElse(NotFound) // return Not Found if no log with id 'tId' exists
  }

  def getRadiometerLogById(rId: String) = Action {
    val logOpt = DataLogManager.getById[RadiometerLog](rId.toLong)
    logOpt.map(rl => Ok(rl.toString)).getOrElse(NotFound) // return Not Found if no log with id 'rId' exists
  }

  def getWindLogById(wId: String) = Action {
    val logOpt = DataLogManager.getById[WindLog](wId.toLong)
    logOpt.map(wl => Ok(Json.toJson(Json.parse(wl.toJson)))).getOrElse(NotFound) // return Not Found if no log with id 'wId' exists
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
  def getMaxSpeedForMission(idStr: String) = Action {
    val resp = DataLogManager.getMaxSpeedForMission(idStr.toLong)
    Ok(resp)
  }

  /**
   * Get the first and last log time for a specific date and set
   * @param dateStr The date to look for
   * @return The first and last log time (JSON)
   */
  /*def getLogTimesForDateAndSet(dateStr: String, setNumber: String) = Action {
    val date = DateFormatHelper.selectYearFormatter.parse(dateStr)
    val setNumberOpt = setNumber match {
      case "all" => None
      case _ => Some(setNumber.toInt)
    }
    val (firstTime, lastTime) = DataLogManager.getTimesForDateAndSet(date, setNumberOpt)
    Ok(Json.toJson(Map("first_time" -> Json.toJson(firstTime), "last_time" -> Json.toJson(lastTime))))
  }

  def getLogSetsForDate(dateStr: String) = Action {
    val date = DateFormatHelper.selectYearFormatter.parse(dateStr)
    val setList = DataLogManager.getLogSetsForDate(date)
    //Logger.warn("sets: "+setList)
    val jsList = Json.toJson(setList.map(sn => Json.toJson(sn)))
    Ok(Json.toJson(Map("sets" -> jsList, "count" -> Json.toJson(setList.length))))
  }*/
}
