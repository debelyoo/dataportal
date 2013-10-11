package controllers.util

import java.util.{TimeZone, Calendar, Date}
import java.text.SimpleDateFormat

object DateFormatHelper {

  val dateTimeFormatter = new SimpleDateFormat("yyyyMMdd-HHmmss")
  val postgresTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  val postgresTimestampWithMilliFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  val selectYearFormatter = new SimpleDateFormat("yyyy-MM-dd")
  val selectTimeFormatter = new SimpleDateFormat("HH:mm:ss")
  //val ulmKmlTimestampFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss.SSS")

  /**
   * Convert a labview timestamp to Java Date (in time zone UTC+01:00)
   * @param labviewTs The timestamp in labview time reference (nb of seconds from 1.1.1904) UTC
   * @return The corresponding date in Java time reference
   */
  def labViewTs2JavaDate(labviewTs: Double): Date = {
    val labviewEpoch = dateTimeFormatter.parse("19040101-000000") // 1.1.1904
    val newTs = labviewEpoch.getTime() + math.round(labviewTs * 1000)
    new Date(newTs)
  }

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
