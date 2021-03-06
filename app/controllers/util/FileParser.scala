package controllers.util

import java.io.File
import java.util.UUID
import controllers.modelmanager.DataLogManager
import models.{DeviceType, Device}

object FileParser {


  /**
   * Parse the address file
   * @param file The name of the address file
   * @param deviceTypeName The name of the device type
   * @return A Map with the devices defined in address file
   */
  def parseAddressFile(file: File, deviceTypeName: String): Option[Map[String, Device]] = {
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
          val deviceType = DeviceType.getByName(deviceTypeName)
          assert(deviceType.isDefined, {println("[parseAddressFile()] device type does not exist")})
          val addr = chunksOnLine(0)
          val name = chunksOnLine(1)
          sensors += Tuple2(addr, new Device(name, addr, deviceType.get))
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
   * @param devices The devices defined in the address file
   * @return The nb of inserted values
   */
  def parseDataFile(dataType: String, file: File, devices: Map[String, Device], missionId: Long): Option[String] = {
    try {
      val source = scala.io.Source.fromFile(file)
      val linesAsStr = source.mkString
      source.close()
      val lines = if (dataType != DataImporter.Types.ULM_TRAJECTORY)
        linesAsStr.split("\\r?\\n")
      else
        linesAsStr.split("\\n")
      //Logger.info("lines: "+lines.length)
      val batchId = UUID.randomUUID().toString
      DataLogManager.insertionWorker ! Message.SetInsertionBatch(batchId, file.getName, dataType, lines, devices, missionId)
      Some(batchId)
    } catch {
      case ex: Exception => ex.printStackTrace; None
    }
  }
}
