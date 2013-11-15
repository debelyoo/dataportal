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
        val syncWithTrajectory = map.get("sync_with_trajectory").map(_.toBoolean).getOrElse(true) // default is true, graph is visible below the trajectory
        //Logger.info("sync: "+syncWithTrajectory)
        val deviceIdList = map.get("device_id").map {
          case "all" => Device.getForMission(missionId, map.get("data_type"), None).map(_.id)
          case _ => List(map.get("device_id").get.toLong)
        }.get
        val logMap = DataLogManager.getDataByMission(datatype, missionId, deviceIdList, startDate, endDate, maxNb, syncWithTrajectory)
        formatResponse(format, logMap)
      } catch {
        case ae: AssertionError => BadRequest
        case ex: Exception => ex.printStackTrace(); BadRequest
      }
  }

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
        val missionId = map.get("mission_id").map(_.toLong)
        val maxNb = map.get("max_nb").map(_.toInt)
        val resp = if (mode == GIS_LINESTRING) {
          Mission.getTrajectoryLinestring(missionId.get)
        } else {
          val trajectoryPoints = Mission.getTrajectoryPoints(missionId.get, None, None, maxNb)
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
    val poiList = Mission.getPointOfInterest(missionId.toLong)
    Ok(pointsAsGeoJson(poiList))
  }


  /**
   * Get details of a particular device (used mainly for tests)
   */
  def getDeviceById(dId: String) = Action {
    val deviceMap = Device.getById(List(dId.toLong), None)
    deviceMap.get(dId.toLong).map(s => Ok(Json.toJson(s.toJson))).getOrElse(NotFound) // return Not Found if no device with id 'dId' exists
  }

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
   * Get missions from DB
   * @return A list of missions (JSON)
   */
  def getMissions = Action {
    val missions = Mission.getAll()
    val jsList = Json.toJson(missions.map { case (mission) =>
      Json.toJson(Map("id" -> Json.toJson(mission.id),
        "date" -> Json.toJson(DateFormatHelper.selectYearFormatter.format(mission.departureTime)),
        "time" -> Json.toJson(DateFormatHelper.selectHourMinFormatter.format(mission.departureTime)),
        "vehicle" -> Json.toJson(mission.vehicle.name)))
    })
    Ok(jsList)
  }

  /**
   * Get missions for a specific date
   * @return A list of missions (JSON)
   */
  def getMissionsForDate(dateStr: String) = Action {
    val date = DateFormatHelper.selectYearFormatter.parse(dateStr)
    val missionList = Mission.getByDate(date)
    val jsList = Json.toJson(missionList.map(m => Json.parse(m.toJson)))
    Ok(jsList)
  }

  /**
   * Get raster data for a specific mission
   * @param idStr The id of the mission
   * @return The coordinate of the raster (JSON)
   */
  def getRasterDataForMission(idStr: String) = Action {
    val resp = Mission.getRasterData(idStr.toLong)
    Ok(resp)
  }

  /**
   * Get the maximum speed for a specific mission
   * @param idStr The id of the mission
   * @return The max speed (JSON)
   */
  def getMaxSpeedAndHeadingForMission(idStr: String) = Action {
    val resp = Mission.getMaxSpeedAndHeading(idStr.toLong)
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
