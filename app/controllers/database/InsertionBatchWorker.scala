package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, DataImporter, Message}
import controllers.database.BatchManager._
import models.spatial.DataLogManager
import models.Sensor

class InsertionBatchWorker extends Actor {
  //val TIMEOUT = 5 seconds

  def receive = {
    case Message.Work(batchId, dataType) => {
      insertionBatches.get(batchId).map { case (lines, sensors) =>
        for ((line, ind) <- lines.zipWithIndex) {
          val chunksOnLine = line.split("\\t")
          if (chunksOnLine.nonEmpty) {
            //println(chunksOnLine.toList)
            val sensorOpt = sensors.get(chunksOnLine(0))
            val sensor = Sensor(sensorOpt.get.name, sensorOpt.get.address, dataType)
            DataLogManager.insertionWorker ! Message.InsertSensor(sensor)
            //val res = Await.result(f_sensorInsertion, TIMEOUT).asInstanceOf[Boolean] // Blocking call - necessary to show on web page if errors occurred
            //if (res) {
              val date = DateFormatHelper.labViewTsToJavaDate(chunksOnLine(1).toDouble) // if TS comes from labview
              dataType match {
                case DataImporter.Types.COMPASS => {
                  // address - timestamp - value
                  DataLogManager.insertionWorker ! Message.InsertCompassLog(batchId, date, sensor, chunksOnLine(2).toDouble)
                  //val clResOpt = Await.result(f_clInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                  //updateCounters(clResOpt)
                }
                case DataImporter.Types.TEMPERATURE => {
                  // address - timestamp - value
                  DataLogManager.insertionWorker ! Message.InsertTemperatureLog(batchId, date, sensor, chunksOnLine(2).toDouble)
                  //val tlResOpt = Await.result(f_tlInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                  //updateCounters(tlResOpt)
                }
                case DataImporter.Types.WIND => {
                  // address - timestamp - value
                  DataLogManager.insertionWorker ! Message.InsertWindLog(batchId, date, sensor, chunksOnLine(2).toDouble)
                  //val wlResOpt = Await.result(f_wlInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                  //updateCounters(wlResOpt)
                }
                case DataImporter.Types.GPS  => {
                  if (chunksOnLine.length == 4) {
                    // address - timestamp - north value - east value
                    DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, date, sensor, chunksOnLine(2).toDouble, chunksOnLine(3).toDouble)
                    //val glResOpt = Await.result(f_glInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                    //updateCounters(glResOpt)
                  }
                }
                case DataImporter.Types.RADIOMETER  => {
                  // address - timestamp - value
                  DataLogManager.insertionWorker ! Message.InsertRadiometerLog(batchId, date, sensor, chunksOnLine(2).toDouble)
                  //val rlResOpt = Await.result(f_rlInsertion, TIMEOUT).asInstanceOf[Option[Boolean]] // Blocking call - necessary to show on web page if errors occurred
                  //updateCounters(rlResOpt)
                }
                case _  => println("Unknown data type !")
              }
            //}
          }
        }
      }
    }
  }
}
