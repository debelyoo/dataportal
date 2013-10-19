package controllers

import play.api._
import play.api.mvc._
import controllers.util.{DateFormatHelper, DataImporter}
import play.api.libs.json.{JsValue, Json}
import com.google.gson.{JsonArray, JsonObject, JsonElement}
import scala.concurrent.ExecutionContext.Implicits.global
import controllers.modelmanager.DataLogManager

object Application extends Controller with GetApi with PostApi {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def importForm = Action {
    Ok(views.html.importSensorData())
  }

  def importResult(batchId: String = "") = Action {
    Ok(views.html.importResult(batchId))
  }

  def spatializeResult(batchId: String = "") = Action {
    Ok(views.html.spatializeResult(batchId))
  }

  def spatializeForm = Action {
    Ok(views.html.spatializeSensorData())
  }

  def view = Action {
    Ok(views.html.view())
  }

  def contact = Action {
    Ok(views.html.contact())
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

  /*
  Called when spatialize form is submitted
   */
  def spatializeData = Action(parse.multipartFormData) { request =>
    val dataType = request.body.dataParts("dataType").mkString(",").toLowerCase
    val date = request.body.dataParts("date").mkString(",").toLowerCase
    //println("Spatialization process [START] ...")
    val res = "" //DataLogManager.spatialize(dataType, date)
    //println("Spatialization process [END]")
    Redirect(routes.Application.spatializeResult(res))
  }

  // Get the progress of a spatialization batch
  def spatializationProgress(batchId: String) = Action {
    val hintAndProgress = DataLogManager.spatializationProgress(batchId)
    hintAndProgress.map { case (h, p) => Ok(Json.toJson(Map("progress" -> Json.toJson(p), "hint" -> Json.toJson(h))))}.getOrElse(NotFound)
  }

}