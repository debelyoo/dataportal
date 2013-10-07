import controllers.modelmanager.DataLogManager
import java.util.Date
import models.spatial.{GpsLog}
import org.specs2.mutable._
import play.api.libs.json.{JsObject, Json}
import play.api.test._
import play.api.test.Helpers._

class ModelSpec extends Specification {
  "GPS log" should {
    "be retrieved by id" in {
      running(FakeApplication()) {
        val gpsLog = DataLogManager.getById[GpsLog](1)
        gpsLog must beSome
      }
    }
    "be serializable to JSON" in {
      running(FakeApplication()) {
        val gpsLog = DataLogManager.getById[GpsLog](1)
        val jsStr = gpsLog.get.toJson
        val res = Json.parse(jsStr)
        //val res = Json.parse("{\"test\":\"value\"}")
        res must haveClass[JsObject]
      }
    }
    "be serializable to Geo JSON" in {
      running(FakeApplication()) {
        val gpsLog = DataLogManager.getById[GpsLog](1)
        val jsStr = gpsLog.get.toGeoJson
        val res = Json.parse(jsStr)
        res must haveClass[JsObject]
      }
    }
  }
}
