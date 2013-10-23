package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, DataImporter, Message}
import controllers.database.BatchManager._
import java.util.{Calendar, Date}
import controllers.modelmanager.DataLogManager
import models.Device

class InsertionBatchWorker extends Actor {
  //val TIMEOUT = 5 seconds

  def receive = {
    case Message.Work(batchId, dataType, missionId) => {
      //var setNumberOpt: Option[Int] = None
      insertionBatches.get(batchId).map { case (lines, devices) =>
        val trajectoryPoints = if (dataType == DataImporter.Types.COMPASS) {
          DataLogManager.getTrajectoryPoints(missionId, None, None, None) // for inserting compass value
        } else List()
        var inc = 0
        for ((line, ind) <- lines.zipWithIndex) {
          val chunksOnLine = if(dataType != DataImporter.Types.ULM_TRAJECTORY)
            line.split("\\t")
          else
            line.split(",")
          if (chunksOnLine.nonEmpty) {
            //println(chunksOnLine.toList)
            inc += 1
            val deviceOpt = devices.get(chunksOnLine(0))
            val device = if (deviceOpt.isDefined) {
              val dev = new Device(deviceOpt.get.getName, deviceOpt.get.getAddress, dataType)
              if (dataType != DataImporter.Types.COMPASS) {
                // persist device in DB. Not for compass because compass value is added to trajectory points
                DataLogManager.insertionWorker ! Message.InsertDevice(dev)
              }
              Some(dev)
            } else None
            val date = if (chunksOnLine(1).contains(".") && dataType != DataImporter.Types.ULM_TRAJECTORY) {
              // if TS comes from labview
              DateFormatHelper.labViewTs2JavaDate(chunksOnLine(1))
            } else {
              // timestamp with new Qt interface is already a unix TS in ms
              DateFormatHelper.unixTsMilli2JavaDate(chunksOnLine(1))
            }
            assert(date.isDefined || dataType == DataImporter.Types.ULM_TRAJECTORY, {println("Date is not parsable")})

            dataType match {
              case DataImporter.Types.COMPASS => {
                if (chunksOnLine(0) == "41") {
                  // address - timestamp - value
                  DataLogManager.insertionWorker ! Message.InsertCompassLog(batchId, trajectoryPoints, date.get, chunksOnLine(2).toDouble)
                } else {
                  DataLogManager.insertionWorker ! Message.SkipLog(batchId) // necessary to update batch progress correctly (when GPS error logs are skipped)
                }
              }
              case DataImporter.Types.TEMPERATURE => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertTemperatureLog(batchId, missionId, date.get, device.get, chunksOnLine(2).toDouble)
              }
              case DataImporter.Types.WIND => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertWindLog(batchId, missionId, date.get, device.get, chunksOnLine(2).toDouble)
              }
              case DataImporter.Types.RADIOMETER  => {
                // address - timestamp - value
                DataLogManager.insertionWorker ! Message.InsertRadiometerLog(batchId, missionId, date.get, device.get, chunksOnLine(2).toDouble)
              }
              case DataImporter.Types.GPS  => {
                if (chunksOnLine(0) == "48") {
                  val altitudeValue = if (chunksOnLine.length == 5) chunksOnLine(4).toDouble else 0.0
                  //println("--> handle GPS log ! "+ chunksOnLine(0))
                  // address - timestamp - latitude - longitude - elevation
                  //if (setNumberOpt.isEmpty) setNumberOpt = DataLogManager.getNextSetNumber(date.get)
                  //val setNumberOpt = DataLogManager.getNextSetNumber[GpsLog](date)
                  DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, missionId, date.get,
                    chunksOnLine(2).toDouble, chunksOnLine(3).toDouble, altitudeValue, None)
                } else {
                  DataLogManager.insertionWorker ! Message.SkipLog(batchId) // necessary to update batch progress correctly (when GPS error logs are skipped)
                }
              }
              case DataImporter.Types.POINT_OF_INTEREST => {
                // timestamp - latitude - longitude - elevation
                DataLogManager.insertionWorker ! Message.InsertPointOfInterest(batchId, missionId, date.get,
                  chunksOnLine(2).toDouble, chunksOnLine(3).toDouble, chunksOnLine(4).toDouble)
              }
              case DataImporter.Types.ULM_TRAJECTORY => {
                // latitude - longitude - elevation - ts
                val (tsDate, timeZone) = DateFormatHelper.ulmTs2JavaDate(chunksOnLine(3))
                DataLogManager.insertionWorker ! Message.InsertUlmTrajectory(batchId, missionId, tsDate,
                  chunksOnLine(0).toDouble, chunksOnLine(1).toDouble, chunksOnLine(2).toDouble)
              }
              case _  => println("Unknown data type ! ["+ dataType +"]")
            }
          }
        }
      }
    }
  }
}
