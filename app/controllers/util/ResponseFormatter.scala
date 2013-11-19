package controllers.util

import play.api.libs.json.{Json, JsValue}
import controllers.util.json.{GeoJsonSerializable, JsonSerializable}
import scala.xml.{Node, NodeSeq}
import scala.xml.parsing.NoBindingFactoryAdapter
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.dom.DOMSource
import play.api.mvc.Results.Ok
import com.google.gson.{Gson, JsonArray, JsonObject, JsonElement}
import models.Device

trait ResponseFormatter {

  /**
   * data JSON format
   */
  val JSON_FORMAT = "json"
  /**
   * trajectory GeoJSON format
   */
  val GEOJSON_FORMAT = "geojson"
  /**
   * trajectory display mode: Linestring
   */
  val GIS_LINESTRING = "linestring"
  /**
   * trajectory display mode: points
   */
  val GIS_POINTS = "points"

  /**
   * Format the logs list in the right format, depending on what has been requested in the REST query
   * @param format The desired format
   * @param logMap The list of logs (by device)
   *
   * @return A HTTP response
   */
  def formatResponse(format: String, logMap: Map[Device, List[JsonSerializable]]) = {
    format match {
      case _ => Ok(logsAsJson(logMap)) // currently only one output format is used (JSON)
    }
  }

  /**
   * Format the list of logs as JSON array
   * @param logMap A map with the logs for each device
   * @return A JSON object with the sensor logs
   */
  private def logsAsJson(logMap: Map[Device, List[JsonSerializable]]): JsValue = {
    val jsObjectList = logMap.map { case (device, logList) => {
      val jsList = Json.toJson(logList.map(log => Json.parse(log.toJson)))
      val sensorAndValues = Json.toJson(Map(
        "sensor" -> Json.toJson(device.name),
        "unit" -> Json.toJson(device.deviceType.unit),
        "values" -> jsList,
        "count" -> Json.toJson(logList.length)))
      sensorAndValues
    }}
    // build return JSON obj with array
    Json.toJson(Map("logs" -> Json.toJson(jsObjectList)))
  }


  /**
   * Create a GeoJson document with a list of TrajectoryPoint
   * @param points The list of TrajectoryPoints
   * @return A GeoJson Document
   */
  def pointsAsGeoJson(points: List[GeoJsonSerializable]): JsValue = {
    val gson: Gson = new Gson
    val featureArray = new JsonArray
    points.foreach(pt => {
      val featureObj = gson.fromJson(pt.toGeoJson, classOf[JsonObject])
      featureArray.add(featureObj)
    })
    //Logger.warn("Geo Json: "+ points.size +" items")

    val dataJson = (new JsonObject).asInstanceOf[JsonElement]
    dataJson.getAsJsonObject.addProperty("type", "FeatureCollection")
    dataJson.getAsJsonObject.add("features", featureArray)
    Json.toJson(Json.parse(dataJson.toString))
  }
}
