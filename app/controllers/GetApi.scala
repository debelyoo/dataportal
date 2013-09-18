package controllers

import controllers.util._
import models.spatial._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.Logger
import com.vividsolutions.jts.geom.Point

//import scala.reflect.{ClassTag, classTag}
import models.Sensor

trait GetApi extends ResponseFormatter {
  this: Controller =>

  /**
   * Get logs by time interval
   * @param format The
   * @param startTime
   * @param endTime
   * @param sensorId
   */
  /*def getGpsLogByTimeInterval(format: String, startTime: String, endTime: String, sensorId: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[GpsLog](startDate, endDate, false)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
  }*/

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
        Logger.info("[GetApi] getData() - "+ map.get("data_type").get + " <"+ format +">")
        //println(map)
        assert(map.contains("data_type"), {println("Missing data_type parameter")})
        assert(map.contains("sensor_id"), {println("Missing sensor_id parameter")})
        assert(map.contains("from_date"), {println("Missing from_date parameter")})
        assert(map.contains("to_date"), {println("Missing to_date parameter")})
        val startDate = DateFormatHelper.dateTimeFormatter.parse(map.get("from_date").get)
        val endDate = DateFormatHelper.dateTimeFormatter.parse(map.get("to_date").get)
        val geoOnly = map.get("geo_only").getOrElse("true").toBoolean
        val coordinateFormat = map.get("coordinate_format").getOrElse("gps")
        //val sensorIdList = map.get("sensor_id").map(_.toLong).toList // TODO handle multi Ids (by parsing string)
        val sensorIdList = map.get("sensor_id").map {
          case "all" => Sensor.getByDatetime(startDate, endDate, map.get("data_type")).map(_.id)
          case _ => List(map.get("sensor_id").get.toLong)
        }.get
        val maxNb = map.get("max_nb").map(_.toInt)
        val logMap = map.get("data_type").get match {
          case DataImporter.Types.TEMPERATURE => {
            //println("GET data - "+DataImporter.Types.TEMPERATURE)
            DataLogManager.getByTimeIntervalAndSensor[TemperatureLog](startDate, endDate, geoOnly, sensorIdList, maxNb)
          }
          case DataImporter.Types.WIND => {
            DataLogManager.getByTimeIntervalAndSensor[WindLog](startDate, endDate, geoOnly, sensorIdList, maxNb)
          }
          case DataImporter.Types.COMPASS => {
            DataLogManager.getByTimeIntervalAndSensor[CompassLog](startDate, endDate, geoOnly, sensorIdList, maxNb)
          }
          case DataImporter.Types.RADIOMETER => {
            DataLogManager.getByTimeIntervalAndSensor[RadiometerLog](startDate, endDate, geoOnly, sensorIdList, maxNb)
          }
          case DataImporter.Types.GPS => {
            val gpsLogsNative = DataLogManager.getByTimeInterval[GpsLog](startDate, endDate, false, maxNb)
            val gpsLogs = if (coordinateFormat == "swiss") {
              gpsLogsNative.map(gl => {
                val arr = ApproxSwissProj.WGS84toLV03(gl.getGeoPos.getY, gl.getGeoPos.getX, 0L).toList // get east, north, height
                val geom = CoordinateHelper.wktToGeometry("POINT("+ arr(0) +" "+ arr(1) +")")
                gl.setGeoPos(geom.asInstanceOf[Point])
                gl
              })
            } else gpsLogsNative
            Map("gps sensor" -> gpsLogs)
          }
          case _ => {
            println("GET data - Unknown data type")
            Map[String, List[WebSerializable]]()
          }
        }
        formatResponse(format, logMap)
      } catch {
        case ae: AssertionError => BadRequest
        case ex: Exception => ex.printStackTrace(); BadRequest
      }
  }

  // TODO - test only
  def getGpsData = Action {
    implicit request =>
      try {
        val map = request.queryString.map { case (k,v) => k -> v.mkString }
        val format = map.get("format").getOrElse(GEOJSON_FORMAT) // default format is Geo json
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
  }



  /**
   * Get details of a particular sensor (used mainly for tests)
   */
  def getSensorById(sId: String) = Action {
    val sensorMap = Sensor.getById(List(sId.toLong), None)
    sensorMap.get(sId.toLong).map(s => Ok(Json.toJson(s.toJson))).getOrElse(NotFound) // return Not Found if no sensor with id 'sId' exists
  }

  /**
   * Get the active sensors in a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @param dataType The type of the sensor to get
   * @return A list of sensors (JSON)
   */
  def getSensorByDatetime(startTime: String, endTime: String, dataType: Option[String] = None) = Action {
    //println("Start: "+startTime +", End: "+endTime)
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val sensorList = Sensor.getByDatetime(startDate, endDate, dataType)
    val jsList = Json.toJson(sensorList.map(s => Json.parse(s.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("sensors" -> jsList, "count" -> Json.toJson(sensorList.length))))
  }

  /// Getters for log details (used mainly for tests)
  def getGpsLogById(gId: String) = Action {
    val logOpt = DataLogManager.getById[GpsLog](gId.toLong)
    logOpt.map(gl => Ok(gl.toString)).getOrElse(NotFound) // return Not Found if no log with id 'gId' exists
  }
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
  def getLogDates = Action {
    val dateList = DataLogManager.getDates
    val jsList = Json.toJson(dateList.map(date => Json.toJson(date)))
    Ok(Json.toJson(Map("dates" -> jsList, "count" -> Json.toJson(dateList.length))))
  }

  /**
   * Get the first and last log time for a specific date and set
   * @param dateStr The date to look for
   * @return The first and last log time (JSON)
   */
  def getLogTimesForDateAndSet(dateStr: String, setNumber: String) = Action {
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
  }
}
