package controllers

import play.api._
import play.api.mvc._
import controllers.util.{DateFormatHelper, DataImporter}
import play.api.libs.json.{JsValue, Json}
import com.google.gson.{JsonArray, JsonObject, JsonElement}
import scala.concurrent.ExecutionContext.Implicits.global
import controllers.modelmanager.DataLogManager
import models.{Vehicle, Mission, DeviceType}

object Application extends Controller with GetApi with PostApi with DeleteApi {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def importForm = Action {
    Ok(views.html.importSensorData())
  }

  def importResult(batchId: String = "") = Action {
    Ok(views.html.importResult(batchId))
  }

  def missionManager() = Action { implicit request =>
    Ok(views.html.missionManager())
  }

  def view = Action {
    Ok(views.html.view())
  }

  def contact = Action {
    Ok(views.html.contact())
  }

  def setDeviceType = Action {
    Ok(views.html.setDeviceType())
  }

  def addDeviceType = Action(parse.multipartFormData) { request =>
    val deviceTypeName = request.body.dataParts("deviceType").mkString(",")
    if (deviceTypeName != "") {
      if (DeviceType(deviceTypeName, "", DeviceType.PlotTypes.LINE).save()) {
        Redirect(routes.Application.setDeviceType())
      } else {
        Redirect(routes.Application.index).flashing(
          "error" -> "Empty device type !"
        )
      }
    } else {
      Redirect(routes.Application.index).flashing(
        "error" -> "Empty device type !"
      )
    }
  }

  /**
   * Called when mission manager form is submitted
   */
  def manageMission = Action(parse.multipartFormData) { request =>
    val action = request.body.dataParts("action").mkString(",").toLowerCase
    var lastMissionId: Long = 0
    val (status, msg) = if (action == "create") {
      Logger.info(request.body.dataParts.toString())
      val missionDate = request.body.dataParts("missionDate").mkString(",")
      val missionTime = request.body.dataParts("missionTime").mkString(",")
      val vehicleId = request.body.dataParts("missionVehicle").mkString(",").toLong
      val timezone = request.body.dataParts("missionTimezone").mkString(",")
      val depTime = DateFormatHelper.missionCreationDateFormatter.parse(missionDate + " " + missionTime)
      if (Mission.create(depTime, timezone, vehicleId)) {
        ("0", "mission has been created !")
      } else {
        ("1", "Mission creation failed !")
      }
    } else {
      val missionId = request.body.dataParts("missionId").mkString(",").toLong
      val secret = request.body.dataParts("secret").mkString(",")
      val secretVal = "1313"
      val mission = DataLogManager.getById[Mission](missionId).get
      if (secret != secretVal) {
        lastMissionId = missionId
        ("1", "Secret is wrong !")
      } else {
        if (Mission.delete(missionId)) {
          ("0", "mission ["+ DateFormatHelper.missionCreationDateFormatter.format(mission.departureTime) +" - "+ mission.vehicle.name +"] has been deleted !")
        } else {
          ("1", "Mission deletion failed !")
        }
      }
    }
    Redirect(routes.Application.missionManager()).flashing(
      "action" -> action,
      "lastMissionId" -> lastMissionId.toString,
      "status" -> status,
      "msg" -> msg
    )
  }

  /**
   * Called when import form is submitted
   */
  def importData = Action(parse.multipartFormData) { request =>
    val (addressFile, dataFile, dataType, missionId) = DataImporter.uploadFiles(request)
    if (dataFile.isDefined) {
      val batchId = DataImporter.importFromFile(dataType, addressFile, dataFile.get, missionId)
      Redirect(routes.Application.importResult(batchId))
    } else {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing Data file"
      )
    }
  }

  // Get the progress of an insertion batch
  def insertionProgress(batchId: String) = Action {
    val hintAndProgress = DataLogManager.insertionProgress(batchId)
    hintAndProgress.map { case (h, p) => Ok(Json.toJson(Map("progress" -> Json.toJson(p), "hint" -> Json.toJson(h))))}.getOrElse(NotFound)
  }

}