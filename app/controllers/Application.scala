package controllers

import play.api._
import play.api.mvc._
import controllers.util.{DateFormatHelper, DataImporter}
import models.spatial.{TemperatureLog, CompassLog, DataLogManager, GpsLog}
import play.api.libs.json.{JsValue, Json}
import com.google.gson.{JsonArray, JsonObject, JsonElement}
import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller with GetApi {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def importForm = Action {
    Ok(views.html.importSensorData())
  }

  def importResult(success: String = "false", nbImportedData: String = "0") = Action {
    Ok(views.html.importResult(success.toBoolean, nbImportedData.toInt))
  }

  def spatializeResult(batchId: String = "") = Action {
    Ok(views.html.spatializeResult(batchId))
  }

  def spatializeForm = Action {
    Ok(views.html.spatializeSensorData())
  }

  def contact = Action {
    Ok(views.html.contact())
  }

  /*
  Called when import form is submitted
   */
  def importData = Action(parse.multipartFormData) { request =>
    val (addressFile, dataFile, dataType) = DataImporter.uploadFiles(request)
    if (addressFile.isDefined && dataFile.isDefined) {
      val count = DataImporter.importFromFile(dataType, addressFile.get, dataFile.get)
      if (count.isDefined) {
        Redirect(routes.Application.importResult("true", count.get.toString))
        //Ok("File imported successfully ["+ dataFile.get.getAbsolutePath +"]")
      } else {
        Redirect(routes.Application.importResult("false", "0"))
      }
    } else {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing file"
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
    val res = DataLogManager.spatialize(dataType, date)
    //println("Spatialization process [END]")
    Redirect(routes.Application.spatializeResult(res.toString))
  }

  def spatializationProgress(batchId: String) = Action {
    val prog = DataLogManager.spatializationProgress(batchId)
    prog.map(p => Ok(Json.toJson(Map("progress" -> Json.toJson(p))))).getOrElse(NotFound)
    /*val f_prog = DataLogManager.spatializationProgress(batchId)
    Async {
      f_prog.map(p => {
        if (p.isLeft) {
          Ok(Json.toJson(Map("progress" -> Json.toJson(p.left.get))))
        } else {
          if (p.right.get == "timeout") {
            RequestTimeout
          } else {
            NotFound
          }
        }
      })
    }*/
  }

}