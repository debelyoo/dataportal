package controllers

import controllers.util.json.JsonSerializable
import controllers.util.{ResponseFormatter, DateFormatHelper}
import models.spatial._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import scala.reflect.{ClassTag, classTag}
import models.Sensor

trait GetApi extends ResponseFormatter {
  this: Controller =>

  val JSON_FORMAT = "json"
  val KML_FORMAT = "xml"
  val GML_FORMAT = "gml"

  /*
  Get logs by time interval
   */
  def getGpsLogByTimeInterval(startTime: String, endTime: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[GpsLog](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
  }

  def getCompassLogByTimeInterval(startTime: String, endTime: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[CompassLog](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
  }

  def getTemperatureLogByTimeIntervalAndSensor(format: String, startTime: String, endTime: String, sensorId: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeIntervalAndSensor[TemperatureLog](startDate, endDate, sensorId)
    format match {
      case JSON_FORMAT => Ok(logsAsJson(logList))
      case KML_FORMAT => Ok(logsAsKml(logList))
      case GML_FORMAT => Ok(logsAsGml(logList))
      case _ => Ok(logsAsJson(logList))
    }
  }

  def getRadiometerLogByTimeInterval(startTime: String, endTime: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[RadiometerLog](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
  }

  def getWindLogByTimeInterval(startTime: String, endTime: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[WindLog](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
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
    val sensorOpt = Sensor.getById(sId.toLong)
    sensorOpt.map(s => Ok(Json.toJson(s.toJson))).getOrElse(NotFound) // return Not Found if no sensor with id 'sId' exists
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
