package controllers

import play.api._
import play.api.mvc._
import controllers.util.{DateFormatHelper, DataImporter}
import play.api.libs.json.{JsValue, Json}
import com.google.gson.{JsonArray, JsonObject, JsonElement}
import scala.concurrent.ExecutionContext.Implicits.global
import controllers.modelmanager.DataLogManager
import models.DeviceType

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

  def missionManager = Action {
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

  /*
  Called when import form is submitted
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