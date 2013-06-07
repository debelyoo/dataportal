package controllers.util

import java.io.File
import models.Sensor
import controllers.database.InsertWorker
import play.libs.Akka
import akka.actor.Props
import akka.pattern.ask
import DateFormatHelper._
import akka.util.Timeout
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Await

// for the ExecutionContext

object FileParser {
  val insertWorker = Akka.system.actorOf(Props[InsertWorker], name = "insertWorker")
  val TIMEOUT = 5 seconds
  implicit val timeout = Timeout(TIMEOUT) // needed for `?` below


  /**
   * Parse the address file
   * @param file The name of the address file
   * @return A Map with the sensors defined in address file
   */
  def parseAddressFile(file: File): Option[Map[String, Sensor]] = {
    val sensors = scala.collection.mutable.Map[String, Sensor]() // address -> Sensor(name, address)
    try {
      val source = scala.io.Source.fromFile(file)
      val linesAsStr = source.mkString
      source.close()

      val lines = linesAsStr.split("\\r?\\n")
      for ((line, ind) <- lines.zipWithIndex) {
        val chunksOnLine = line.split("\\t")
        //println(chunksOnLine.toList)
        if (ind == 0 && chunksOnLine(0) != "Address") {
          throw new AssertionError
        } else if (chunksOnLine.nonEmpty && ind > 0) {
          //println(chunksOnLine(0)+ " - "+ chunksOnLine(1))
          val addr = chunksOnLine(0)
          val name = chunksOnLine(1)
          sensors += Tuple2(addr, Sensor(name, addr, ""))
        }
      }
      Some(sensors.toMap)
    } catch {
      case ae: AssertionError => None
      case ex: Exception => ex.printStackTrace(); None
    }
  }

  /**
   * Parse a data file
   * @param dataType The type of data to store
   * @param file The name of the data file
   * @param sensors The sensors defined in the address file
   * @return The nb of inserted values
   */
  def parseDataFile(dataType: String, file: File, sensors: Map[String, Sensor]): Option[Int] = {
    var nbInserted, nbFailed = 0
    def updateCounters(res: Option[Boolean]) {
      if (res.isDefined) {
        if (res.get) nbInserted += 1
      } else nbFailed += 1
    }
    try {
      val source = scala.io.Source.fromFile(file)
      val linesAsStr = source.mkString
      source.close()
      val lines = linesAsStr.split("\\r?\\n")
      for ((line, ind) <- lines.zipWithIndex) {
        val chunksOnLine = line.split("\\t")
        if (chunksOnLine.nonEmpty) {
          //println(chunksOnLine.toList)
          val sensorOpt = sensors.get(chunksOnLine(0))
          val sensor = Sensor(sensorOpt.get.name, sensorOpt.get.address, dataType)
          val f_sensorInsertion = insertWorker ? Message.InsertSensor(sensor)
          val res = Await.result(f_sensorInsertion, TIMEOUT).asInstanceOf[Boolean] // Blocking call - necessary to show on web page if errors occurred
          if (res) {
            val date = labViewTsToJavaDate(chunksOnLine(1).toDouble) // if TS comes from labview
            dataType match {
              case DataImporter.Types.COMPASS => {
                // address - timestamp - value
                val f_clInsertion = insertWorker ? Message.InsertCompassLog(date, sensor, chunksOnLine(2).toDouble)
                val clResOpt = Await.result(f_clInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                updateCounters(clResOpt)
              }
              case DataImporter.Types.TEMPERATURE => {
                // address - timestamp - value
                val f_tlInsertion = insertWorker ? Message.InsertTemperatureLog(date, sensor, chunksOnLine(2).toDouble)
                val tlResOpt = Await.result(f_tlInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                updateCounters(tlResOpt)
              }
              case DataImporter.Types.WIND => {
                // address - timestamp - value
                val f_wlInsertion = insertWorker ? Message.InsertWindLog(date, sensor, chunksOnLine(2).toDouble)
                val wlResOpt = Await.result(f_wlInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                updateCounters(wlResOpt)
              }
              case DataImporter.Types.GPS  => {
                if (chunksOnLine.length == 4) {
                  // address - timestamp - x value - y value
                  val f_glInsertion = insertWorker ? Message.InsertGpsLog(date, sensor, chunksOnLine(2).toDouble, chunksOnLine(3).toDouble)
                  val glResOpt = Await.result(f_glInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                  updateCounters(glResOpt)
                }
              }
              case DataImporter.Types.RADIOMETER  => {
                // address - timestamp - value
                val f_rlInsertion = insertWorker ? Message.InsertRadiometerLog(date, sensor, chunksOnLine(2).toDouble)
                val rlResOpt = Await.result(f_rlInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                updateCounters(rlResOpt)
              }
              case _  => println("Unknown data type !")
            }
          }

        }
      }
      if (nbFailed == 0) Some(nbInserted) else None
    } catch {
      case ae: AssertionError => None
      case ex: Exception => ex.printStackTrace; None
    }
  }
}
