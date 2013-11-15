package models.internal

import com.google.gson.Gson
import controllers.util.json.JsonSerializable

case class OneDoubleValueLog(id: Long, timestamp: String, value: Double) extends JsonSerializable {

  @Override
  def toJson: String = {
    val gson = new Gson()
    gson.toJson(this)
  }

}
