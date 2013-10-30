import controllers.modelmanager.DataLogManager
import java.util.Date
import models.{SensorLog, Mission}
import models.spatial.TrajectoryPoint
import org.specs2.mutable._
import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.test._
import play.api.test.FakeApplication
import play.api.test.Helpers._

class ModelSpec extends Specification {
  "TrajectoryPoint" should {
    running(FakeApplication()) {
      "be retrieved by id" in {
        val gpsLog = DataLogManager.getById[TrajectoryPoint](1)
        gpsLog must beSome
      }
      "be serializable to Geo JSON" in {
        val gpsLog = DataLogManager.getById[TrajectoryPoint](1)
        val jsStr = gpsLog.get.toGeoJson
        val res = Json.parse(jsStr)
        res must haveClass[JsObject]
      }
    }
  }
  "Sensor log" should {
    running(FakeApplication()) {
      val sensorLog = DataLogManager.getById[SensorLog](1)
      "be serializable to JSON" in {
        val jsStr = sensorLog.get.toJson
        val res = Json.parse(jsStr)
        res must haveClass[JsObject]
      }
      "have a 'value' field" in {
        val jsObj = Json.parse(sensorLog.get.toJson)
        val value = jsObj \ "value"
        value must haveClass[JsNumber]
      }
      "have a 'mission_id' field" in {
        val jsObj = Json.parse(sensorLog.get.toJson)
        val fieldVal = jsObj \ "mission_id"
        fieldVal must haveClass[JsNumber]
      }
      "have a 'device_id' field" in {
        val jsObj = Json.parse(sensorLog.get.toJson)
        val fieldVal = jsObj \ "device_id"
        fieldVal must haveClass[JsNumber]
      }
    }
  }
  "Mission" should {
    running(FakeApplication()) {
      val mission = DataLogManager.getById[Mission](8)
      "be serializable to JSON" in {
        val jsStr = mission.get.toJson
        val res = Json.parse(jsStr)
        res must haveClass[JsObject]
      }
      "have an 'id' field" in {
        val jsObj = Json.parse(mission.get.toJson)
        val idValue = jsObj \ "id"
        idValue must haveClass[JsNumber]
      }
    }
  }
}
