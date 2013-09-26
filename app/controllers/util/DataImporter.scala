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
    val GPS_ELEMO = "gps_elemo"
    val TEMPERATURE_ELEMO = "temperature_elemo"
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
      //println("Import Successful !")
      // delete the files from /tmp folder
      addressFile.delete()
      dataFile.delete()
      batchId.get
    } catch {
      case ae: AssertionError => ""
      case ex: Exception => ex.printStackTrace; ""
    }
  }

  /**
   * Get the files from the form data and save them in /tmp folder
   * @param request The HTTP request
   * @return The 2 uplaoded files and the type of the data to import
   */
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
