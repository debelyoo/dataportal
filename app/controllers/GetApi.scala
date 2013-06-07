package controllers

import controllers.util.json.JsonSerializable
import controllers.util.DateFormatHelper
import models.spatial._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import scala.reflect.{ClassTag, classTag}
import models.Sensor

trait GetApi {
  this: Controller =>

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

  def getTemperatureLogByTimeInterval(startTime: String, endTime: String) = Action {
    val startDate = DateFormatHelper.dateTimeFormatter.parse(startTime)
    val endDate = DateFormatHelper.dateTimeFormatter.parse(endTime)
    val logList = DataLogManager.getByTimeInterval[TemperatureLog](startDate, endDate)
    val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
    // build return JSON obj with array and count
    Ok(Json.toJson(Map("logs" -> jsList, "count" -> Json.toJson(logList.length))))
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
}
