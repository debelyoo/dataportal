package models.internal

import controllers.util.json.JsonSerializable
import com.google.gson.Gson


case class SpeedLog(id: Long, timestamp: String, value: Double) extends JsonSerializable {

  @Override
  def toJson: String = {
    val gson = new Gson()
    gson.toJson(this)
  }
}
