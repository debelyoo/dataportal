package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, DataImporter, Message}
import controllers.database.BatchManager._
import models.spatial.{GpsLog, DataLogManager}
import models.Sensor

class InsertionBatchWorker extends Actor {
  //val TIMEOUT = 5 seconds

  def receive = {
    case Message.Work(batchId, dataType) => {
      var setNumberOpt: Option[Int] = None
      insertionBatches.get(batchId).map { case (lines, sensors) =>
        for ((line, ind) <- lines.zipWithIndex) {
          val chunksOnLine = line.split("\\t")
          if (chunksOnLine.nonEmpty) {
            //println(chunksOnLine.toList)
            val sensorOpt = sensors.get(chunksOnLine(0))
            val sensor = Sensor(sensorOpt.get.name, sensorOpt.get.address, dataType)
            DataLogManager.insertionWorker ! Message.InsertSensor(sensor)
            val date = DateFormatHelper.labViewTsToJavaDate(chunksOnLine(1).toDouble) // if TS comes from labview
            dataType match {
              case DataImporter.Types.COMPASS => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertCompassLog(batchId, date, sensor, chunksOnLine(2).toDouble)
              }
              case DataImporter.Types.TEMPERATURE => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertTemperatureLog(batchId, date, sensor, chunksOnLine(2).toDouble)
              }
              case DataImporter.Types.WIND => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertWindLog(batchId, date, sensor, chunksOnLine(2).toDouble)
              }
              case DataImporter.Types.GPS  => {
                if (chunksOnLine.length == 4) {
                  // address - timestamp - north value - east value
                  if (ind == 0) setNumberOpt = DataLogManager.getNextSetNumber(date)
                  //val setNumberOpt = DataLogManager.getNextSetNumber[GpsLog](date)
                  setNumberOpt.foreach(setNumber => {
                    DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, date, setNumber, sensor, chunksOnLine(2).toDouble, chunksOnLine(3).toDouble)
                  })
                } else {
                  DataLogManager.insertionWorker ! Message.SkipLog(batchId) // necessary to update batch progress correctly (when GPS error logs are skipped)
                }
              }
              case DataImporter.Types.RADIOMETER  => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertRadiometerLog(batchId, date, sensor, chunksOnLine(2).toDouble)
              }
              case _  => println("Unknown data type !")
            }
          }
        }
      }
    }
  }
}
