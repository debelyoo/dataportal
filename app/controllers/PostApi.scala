package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsObject, JsArray}
import controllers.util.{DateFormatHelper, Message, DataImporter}
import controllers.modelmanager.{VehicleManager, DataLogManager}
import java.util.UUID
import models.{Vehicle, Mission}
import scala.concurrent.duration.Duration
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global

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
      val vehicle = VehicleManager.getByName(vName).getOrElse(new Vehicle(vName))
      val departureTime = DateFormatHelper.unixTs2JavaDate(ts).get
      val mission = new Mission(departureTime, tz, vehicle)

      val reply = DataLogManager.insertionWorker ? Message.InsertMission(departureTime, tz, vName)

      reply.mapTo[JsObject].map { response => Ok(response)}
    }
  }

  def postData = Action(parse.json) { request =>
    // handle POST data as JSON
    val dataType = (request.body \ "datatype").as[String]
    val jsArray = (request.body \ "items").asInstanceOf[JsArray]
    val batchId = UUID.randomUUID().toString
    DataLogManager.insertionWorker ! Message.SetInsertionBatchJson(batchId, dataType, jsArray.value.length)
    jsArray.value.foreach(item => {
      dataType match {
        case DataImporter.Types.GPS => {
          val missionId = (item \ "mission_id").as[Long]
          val ts = (item \ "timestamp").as[Double]
          val date = DateFormatHelper.unixTs2JavaDate(ts).get
          val lat = (item \ "latitude").as[Double]
          val lon = (item \ "longitude").as[Double]
          val alt = (item \ "altitude").as[Double]
          val heading = (item \ "heading").asOpt[Double]
          DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, missionId, date, lat, lon, alt, heading)
        }
        case DataImporter.Types.TEMPERATURE => {
          val missionId = (item \ "mission_id").as[Long]
          val ts = (item \ "timestamp").as[Double]
          val date = DateFormatHelper.unixTs2JavaDate(ts).get
          val value = (item \ "value").as[Double]

          //DataLogManager.insertionWorker ! Message.InsertTemperatureLog(batchId, missionId, date, device.get, value)
        }
        case _  => println("Unknown data type ! ["+ dataType +"]")
      }
    })

    println(request.body)
    Ok
  }

}
