package controllers.util

import java.util.{TimeZone, Calendar, Date}
import java.text.SimpleDateFormat

/**
 * A helper class to work with different date/time formats
 */
object DateFormatHelper {

  def dateTimeFormatter: SimpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss")
  val postgresTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  def postgresTimestampWithMilliFormatter: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  val selectYearFormatter = new SimpleDateFormat("yyyy-MM-dd")
  val selectTimeFormatter = new SimpleDateFormat("HH:mm:ss")
  val selectHourMinFormatter = new SimpleDateFormat("HH:mm")
  val missionCreationDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm")

  /**
   * Convert a labview timestamp to Java Date
   * @param labviewTs The timestamp in labview time reference (nb of seconds from 1.1.1904) UTC
   * @return The corresponding date in Java format
   */
  def labViewTs2JavaDate(labviewTs: String): Option[Date] = {
    try {
      val labviewEpoch = dateTimeFormatter.parse("19040101-000000") // 1.1.1904
      val newTs = labviewEpoch.getTime() + math.round(labviewTs.toDouble * 1000)
      Some(new Date(newTs))
    } catch {
      case ex: Exception => None
    }
  }

  /**
   * Convert a unix timestamp (in ms) to Java Date
   * @param unixTs The timestamp in unix time reference (ms from 1.1.1970)
   * @return The corresponding date in Java format
   */
  def unixTsMilli2JavaDate(unixTs: String): Option[Date] = {
    try {
      val cal = Calendar.getInstance()
      cal.setTimeInMillis(unixTs.toLong)
      Some(cal.getTime)
    } catch {
      case ex: Exception => ex.printStackTrace(); None
    }
  }

  /**
   * Convert a unix timestamp to Java Date
   * @param unixTs The unix timestamp to convert
   * @return A Java Date (as an Option)
   */
  def unixTs2JavaDate(unixTs: Double): Option[Date] = {
    try {
      val cal = Calendar.getInstance()
      val tsMilli = math.round(unixTs * 1000).toLong
      cal.setTimeInMillis(tsMilli)
      Some(cal.getTime)
    } catch {
      case ex: Exception => None
    }
  }

  /**
   * Convert a ULM timestamp (as it is formatted in gps files for ULM flights) to Java Date
   * @param ulmTs The ULM timestamp (as String)
   * @return The corresponding date, and timezone
   */
  def ulmTs2JavaDate(ulmTs: String): (Date, TimeZone) = {
    //val defaultTz = TimeZone.getDefault
    val tokens = ulmTs.split("[\\.Z\\r]");
    val gmtOffset = tokens(2).toInt
    val tz = TimeZone.getTimeZone("GMT+"+gmtOffset)
    //TimeZone.setDefault(tz)
    val fractionalSecs = tokens(1).toInt / 1000
    val dateStr = "%s.%03d+%s00".format(tokens(0), fractionalSecs, tokens(2))
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss.SSSZ")
    //formatter.setTimeZone(tz)
    val date = formatter.parse(dateStr)
    (date, tz)
  }
}
