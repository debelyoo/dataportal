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

  def contact = Action {
    Ok(views.html.contact())
  }

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

}