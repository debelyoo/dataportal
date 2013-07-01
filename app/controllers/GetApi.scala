package controllers

import controllers.util.json.JsonSerializable
import controllers.util._
import models.spatial._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import javax.persistence.EntityManager
import models.mapping.{MapGpsRadiometer, MapGpsCompass, MapGpsTemperature, MapGpsWind}
import java.util.Date

//import scala.reflect.{ClassTag, classTag}
import models.Sensor

trait GetApi extends ResponseFormatter {
  this: Controller =>

  /*
  Get logs by time interval
   */
  def getGpsLogByTimeInterval(format: String, startTime: String, endTime: String, sensorId: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[GpsLog](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
  }

  /*def getCompassLogByTimeInterval(format: String, startTime: String, endTime: String, sensorId: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[CompassLog](startDate, endDate)
    formatResponse(format, logList)
  }

  def getTemperatureLogByTimeIntervalAndSensor(format: String, startTime: String, endTime: String, sensorIdStr: String) = Action {
    try {
      val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
      val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
      val sensorId = sensorIdStr.toLong
      val logMap = DataLogManager.getByTimeIntervalAndSensorWithJoin[TemperatureLog, MapGpsTemperature](startDate, endDate, List(sensorId))
      formatResponse(format, logMap)
    } catch {
      case ex: Exception => BadRequest
    }
  }

  def getRadiometerLogByTimeInterval(format: String, startTime: String, endTime: String, sensorId: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[RadiometerLog](startDate, endDate)
    formatResponse(format, logList)
  }

  def getWindLogByTimeInterval(format: String, startTime: String, endTime: String, sensorId: String) = Action {
    val start = new Date
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    //val logList = DataLogManager.getByTimeInterval[WindLog](startDate, endDate)
    val logList = DataLogManager.getByTimeIntervalWithJoin[WindLog, MapGpsWind](startDate, endDate) // Request with JOIN seems to be faster
    val diff = (new Date).getTime - start.getTime
    //println("Time: "+ diff +"ms")
    formatResponse(format, logList)
  } */

  /**
   * Handle data request with query params  (ex: /api/data?data_type=temperature&from_date=20130613-150000&to_date=20130613-170000&sensor_id=4&geo_only=true)
   * @return
   */
  def getData = Action {
    implicit request =>
      println("[GetApi] getData()")
      try {
        val map = request.queryString.map { case (k,v) => k -> v.mkString }
        //println(map)
        assert(map.contains("data_type"), {println("Missing data_type parameter")})
        assert(map.contains("from_date"), {println("Missing from_date parameter")})
        assert(map.contains("to_date"), {println("Missing to_date parameter")})
        val format = map.get("format").getOrElse(JSON_FORMAT) // default format is Json
        val startDate = DateFormatHelper.dateTimeFormatter.parse(map.get("from_date").get)
        val endDate = DateFormatHelper.dateTimeFormatter.parse(map.get("to_date").get)
        val geoOnly = map.get("geo_only").getOrElse("true").toBoolean
        val sensorIdList = map.get("sensor_id").map(_.toLong).toList
        val logMap = map.get("data_type").get match {
          case DataImporter.Types.TEMPERATURE => {
            //println("GET data - "+DataImporter.Types.TEMPERATURE)
            if (geoOnly) {
              DataLogManager.getByTimeIntervalAndSensorWithJoin[TemperatureLog, MapGpsTemperature](startDate, endDate, sensorIdList) // only geo-referenced points (Request with JOIN seems to be faster)
            } else {
              DataLogManager.getByTimeIntervalAndSensor[TemperatureLog](startDate, endDate, sensorIdList) // all data points
            }
          }
          case DataImporter.Types.WIND => {
            if (geoOnly) {
              DataLogManager.getByTimeIntervalAndSensorWithJoin[WindLog, MapGpsWind](startDate, endDate, sensorIdList)
            } else {
              DataLogManager.getByTimeIntervalAndSensor[WindLog](startDate, endDate, sensorIdList)
            }
          }
          case DataImporter.Types.COMPASS => {
            if (geoOnly) {
              DataLogManager.getByTimeIntervalAndSensorWithJoin[CompassLog, MapGpsCompass](startDate, endDate, sensorIdList)
            } else {
              DataLogManager.getByTimeIntervalAndSensor[CompassLog](startDate, endDate, sensorIdList)
            }
          }
          case DataImporter.Types.RADIOMETER => {
            if (geoOnly) {
              DataLogManager.getByTimeIntervalAndSensorWithJoin[RadiometerLog, MapGpsRadiometer](startDate, endDate, sensorIdList)
            } else {
              DataLogManager.getByTimeIntervalAndSensor[RadiometerLog](startDate, endDate, sensorIdList)
            }
          }
          case _ => {
            println("GET data - Unknown data type")
            Map[String, List[WebSerializable]]()
          }
        }
        formatResponse(format, logMap)
      } catch {
        case ae: AssertionError => BadRequest
        case ex: Exception => BadRequest
      }
  }

  /* Does not work - because of mix between Scala and Java ? */
  /*def getSensorLogByTimeInterval[T <: JsonSerializable with ClassTag](startTime: String, endTime: String): JsValue = {
  //def getSensorLogByTimeInterval[T: ClassTag](startTime: String, endTime: String): JsValue = {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[T](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    //val jsList = Json.toJson("test")
    // build return JSON obj with array and count
    Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length)))
  }*/

  /*
  Get details of a particular log (used mainly for tests)
   */
  def getSensorById(sId: String) = Action {
    val sensorMap = Sensor.getById(List(sId.toLong), None)
    sensorMap.get(sId.toLong).map(s => Ok(Json.toJson(s.toJson))).getOrElse(NotFound) // return Not Found if no sensor with id 'sId' exists
  }

  def getSensorByDatetime(startTime: String, endTime: String) = Action {
    //println("Start: "+startTime +", End: "+endTime)
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val sensorList = Sensor.getByDatetime(startDate, endDate)
    val jsList = Json.toJson(sensorList.map(s => Json.parse(s.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("sensors" -> jsList, "count" -> Json.toJson(sensorList.length))))
  }

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

  def getLogDates = Action {
    val dateList = DataLogManager.getDates
    val jsList = Json.toJson(dateList.map(date => Json.toJson(date)))
    Ok(Json.toJson(Map("dates" -> jsList, "count" -> Json.toJson(dateList.length))))
  }

  def getLogTimesForDate(dateStr: String) = Action {
    val date = DateFormatHelper.selectYearFormatter.parse(dateStr)
    val (firstTime, lastTime) = DataLogManager.getTimesForDate(date)
    Ok(Json.toJson(Map("first_time" -> Json.toJson(firstTime), "last_time" -> Json.toJson(lastTime))))
  }
}
