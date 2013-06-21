package controllers

import play.api._
import play.api.mvc._
import controllers.util.{DateFormatHelper, DataImporter}
import models.Sensor
import models.spatial.{TemperatureLog, CompassLog, DataLogManager, GpsLog}
import play.api.libs.json.{JsValue, Json}
import com.google.gson.{JsonArray, JsonObject, JsonElement}
import controllers.util.json.JsonSerializable
import scala.reflect.ClassTag

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

  def spatializeResult(nbSuccess: String = "0", nbFailures: String = "0") = Action {
    Ok(views.html.spatializeResult(nbSuccess.toInt, nbFailures.toInt))
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
    println("Spatialization process [START] ...")
    val (successes, failures) = DataLogManager.spatialize(dataType, date)
    println("Spatialization process [END]")
    Redirect(routes.Application.spatializeResult(successes.toString, failures.toString))
  }

}