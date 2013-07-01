package controllers.util

import java.io.File
import play.api.mvc._
import play.api.libs.Files

object DataImporter {
  object Types {
    val TEMPERATURE = "temperature"
    val COMPASS = "compass"
    val WIND = "wind"
    val GPS = "gps"
    val RADIOMETER = "radiometer"
  }

  /**
   * Import measures from text file
   * @param dataType The type of data to import
   * @param addressFile The text file containing the addresses
   * @param dataFile The text file containing the data
   * @return The number of imported values
   */
  def importFromFile(dataType: String, addressFile: File, dataFile: File): String = {
    try {
      assert(dataFile.exists(), {println("File ["+ dataFile.getAbsolutePath +"] does not exist")})
      assert(addressFile.exists(), {println("File ["+ addressFile.getAbsolutePath +"] does not exist")})
      val sensors = FileParser.parseAddressFile(addressFile)
      assert(sensors.isDefined, {println("Wrong format in address file (2nb parameter)")})
      println("Importing... ")
      val batchId = FileParser.parseDataFile(dataType, dataFile, sensors.get)
      assert(batchId.isDefined, {println("Parsing of data file failed !")})
      println("Import Successful !")
      batchId.get
    } catch {
      case ae: AssertionError => ""
      case ex: Exception => ex.printStackTrace; ""
    }
  }

  def uploadFiles(request: Request[MultipartFormData[Files.TemporaryFile]]): (Option[File], Option[File], String) = {
    val addressFile = request.body.file("addressFile").map { file =>
      import java.io.File
      val filename = file.filename
      val contentType = file.contentType
      val destFile = new File("/tmp/"+filename)
      file.ref.moveTo(destFile, true)
      destFile
    }
    val dataFile = request.body.file("dataFile").map { file =>
      import java.io.File
      val filename = file.filename
      val contentType = file.contentType
      val destFile = new File("/tmp/"+filename)
      file.ref.moveTo(destFile, true)
      destFile
    }
    val dataType = request.body.dataParts("dataType").mkString(",").toLowerCase
    //println("--> "+dataType)
    (addressFile, dataFile, dataType)
  }
}
