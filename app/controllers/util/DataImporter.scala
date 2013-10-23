package controllers.util

import java.io.File
import play.api.mvc._
import play.api.libs.Files
import models.Device

object DataImporter {
  object Types {
    val ALTITUDE = "altitude"
    val SPEED = "speed"
    val TEMPERATURE = "temperature"
    val COMPASS = "compass"
    val WIND = "wind"
    val GPS = "gps"
    val RADIOMETER = "radiometer"
    val POINT_OF_INTEREST = "point_of_interest"
    val ULM_TRAJECTORY = "ulm_trajectory"
  }

  /**
   * Import measures from text file
   * @param dataType The type of data to import
   * @param addressFile The text file containing the addresses
   * @param dataFile The text file containing the data
   * @param missionId The id of the mission
   * @return The number of imported values
   */
  def importFromFile(dataType: String, addressFile: Option[File], dataFile: File, missionId: Long): String = {
    try {
      assert(dataFile.exists(), {println("File ["+ dataFile.getAbsolutePath +"] does not exist")})
      val sensors = if (addressFile.isDefined) {
        assert(addressFile.get.exists(), {println("File ["+ addressFile.get.getAbsolutePath +"] does not exist")})
        FileParser.parseAddressFile(addressFile.get, )
      } else Some(Map[String, Device]())
      assert(sensors.isDefined, {println("Wrong format in address file (2nb parameter)")})
      println("Importing... ")
      val batchId = FileParser.parseDataFile(dataType, dataFile, sensors.get, missionId)
      assert(batchId.isDefined, {println("Parsing of data file failed !")})
      //println("Import Successful !")
      // delete the files from /tmp folder
      if (addressFile.isDefined)
        addressFile.get.delete()
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
  def uploadFiles(request: Request[MultipartFormData[Files.TemporaryFile]]): (Option[File], Option[File], String, Long) = {
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
    val missionId = request.body.dataParts("missionId").mkString(",").toLong
    //println("--> "+dataType)
    (addressFile, dataFile, dataType, missionId)
  }
}
