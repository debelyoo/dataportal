package models.internal

import com.google.gson.Gson
import controllers.util.json.JsonSerializable

/**
 * A case class to manipulate device logs that have one single Double value
 * @param id The id of the log (in database)
 * @param timestamp The timestamp of the log
 * @param value The value of the log (Double)
 */
case class OneDoubleValueLog(id: Long, timestamp: String, value: Double) extends JsonSerializable {

  /**
   * Format the device log as JSON
   * @return A JSOn representation of the log
   */
  @Override
  def toJson: String = {
    val gson = new Gson()
    gson.toJson(this)
  }

}
