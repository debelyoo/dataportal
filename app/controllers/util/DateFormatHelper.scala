package controllers.util

import java.util.Date
import java.text.SimpleDateFormat

object DateFormatHelper {

  val dateTimeFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss")
  val postgresTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  val postgresTimestampWithMilliFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  val selectYearFormatter = new SimpleDateFormat("yyyy-MM-dd")

  /**
   * Convert a labview timestamp to Java Date (in time zone UTC+01:00)
   * @param labviewTs The timestamp in labview time reference (nb of seconds from 1.1.1904) UTC
   * @return The corresponding date in Java time reference
   */
  def labViewTsToJavaDate(labviewTs: Double): Date = {
    val labviewEpoch = dateTimeFormatter.parse("19040101-000000") // 1.1.1904
    val newTs = labviewEpoch.getTime() + math.round(labviewTs * 1000) + (3600 * 1000) // add one hour for the time zone (UTC+01:00)
    new Date(newTs)
  }
}
