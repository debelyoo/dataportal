package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsObject, JsArray}
import controllers.util.{DateFormatHelper, Message, DataImporter}
import controllers.modelmanager.DataLogManager
import java.util.UUID
import models.{Device, Vehicle, Mission}
import scala.concurrent.duration.Duration
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger

// Necessary for `actor ? message`
import akka.pattern.ask

trait PostApi {
  this: Controller =>

  // Implicit argument required by `actor ?`
  implicit val timeout : Timeout = Timeout(Duration(5,"seconds"))

  def postMission = Action(parse.json) { request =>
    // handle POST data as JSON
    Async {
      val ts = (request.body \ "departure_time").as[Double]
      val tz = (request.body \ "timezone").as[String]
      val vName = (request.body \ "vehicle").as[String]
      val devices = (request.body \ "devices").as[JsArray]
      val departureTime = DateFormatHelper.unixTs2JavaDate(ts).get
      //val mission = new Mission(departureTime, tz, vehicle)
      val reply = DataLogManager.insertionWorker ? Message.InsertMission(departureTime, tz, vName, devices)

      reply.mapTo[JsObject].map(response => {
        Ok(response)
      })
    }
  }

  /*
   * handle payload of 500 kB max (enough to handle 1000 sensor logs in json object)
   */
  def postData = Action(parse.json(maxLength = 1024 * 500)) { request =>
    //println(request.body)
    // handle POST data as JSON
    val dataType = (request.body \ "datatype").as[String]
    val jsArray = (request.body \ "items").as[JsArray]
    val lastChunk = (request.body \"last_chunk").as[Boolean]
    //Logger.info("[PostApi] postData() - inc: " + (request.body \ "inc"))
    var missionId: Long = 0
    val batchId = UUID.randomUUID().toString
    DataLogManager.insertionWorker ! Message.SetInsertionBatchJson(batchId, dataType, jsArray.value.length)
    jsArray.value.foreach(item => {
      dataType match {
        case DataImporter.Types.GPS => {
          missionId = (item \ "mission_id").as[Long]
          val ts = (item \ "timestamp").as[Double]
          val date = DateFormatHelper.unixTs2JavaDate(ts).get
          val lat = (item \ "latitude").as[Double]
          val lon = (item \ "longitude").as[Double]
          val alt = (item \ "altitude").as[Double]
          val heading = (item \ "heading").asOpt[Double]
          DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, missionId, date, lat, lon, alt, heading)
        }
        case _ => {
          missionId = (item \ "mission_id").as[Long]
          val ts = (item \ "timestamp").as[Double]
          val date = DateFormatHelper.unixTs2JavaDate(ts).get
          val value = (item \ "value").as[Double]
          val deviceType = (item \ "sensor_type").as[String]
          val deviceAddress = (item \ "sensor_address").as[String]
          DataLogManager.insertionWorker ! Message.InsertSensorLog(batchId, missionId, date, value, deviceAddress)
        }
      }
    })
    if (dataType == DataImporter.Types.GPS && lastChunk) {
      // insert linestring object for mission
      DataLogManager.insertionWorker ! Message.InsertTrajectoryLinestring(missionId)
    }
    Ok
  }

}
