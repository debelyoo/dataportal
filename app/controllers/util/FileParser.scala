package controllers.util

import java.io.File
import models.{DataLogManager, Device}
import controllers.database.InsertionWorker
import play.libs.Akka
import akka.actor.Props
import akka.pattern.ask
import DateFormatHelper._
import akka.util.Timeout
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Await
import java.util.UUID

// for the ExecutionContext

object FileParser {
  val TIMEOUT = 5 seconds
  //implicit val timeout = Timeout(TIMEOUT) // needed for `?` below


  /**
   * Parse the address file
   * @param file The name of the address file
   * @return A Map with the sensors defined in address file
   */
  def parseAddressFile(file: File): Option[Map[String, Device]] = {
    val sensors = scala.collection.mutable.Map[String, Device]() // address -> Sensor(name, address)
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
          sensors += Tuple2(addr, Device(name, addr, ""))
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
   * @param devices The sensors defined in the address file
   * @return The nb of inserted values
   */
  def parseDataFile(dataType: String, file: File, devices: Map[String, Device], missionId: Long): Option[String] = {
    try {
      val source = scala.io.Source.fromFile(file)
      val linesAsStr = source.mkString
      source.close()
      val lines = linesAsStr.split("\\r?\\n")
      val batchId = UUID.randomUUID().toString
      DataLogManager.insertionWorker ! Message.SetInsertionBatch(batchId, file.getName, dataType, lines, devices, missionId)
      Some(batchId)
    } catch {
      case ex: Exception => ex.printStackTrace; None
    }
  }
}
