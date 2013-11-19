package controllers.database

import akka.actor.Actor
import controllers.util.{DateFormatHelper, DataImporter, Message}
import controllers.database.BatchManager._
import java.util.{Calendar, Date}
import controllers.modelmanager.DataLogManager
import models.{Mission, Device}

class InsertionBatchWorker extends Actor {
  //val TIMEOUT = 5 seconds

  def parseDate(dateStr: String): Date = {
    val date = if (dateStr.contains(".")) {
      // if TS comes from labview
      DateFormatHelper.labViewTs2JavaDate(dateStr)
    } else {
      // timestamp with new Qt interface is already a unix TS in ms
      DateFormatHelper.unixTsMilli2JavaDate(dateStr)
    }
    assert(date.isDefined, {println("Date is not parsable")})
    date.get
  }

  def receive = {
    case Message.Work(batchId, dataType, missionId) => {
      //var setNumberOpt: Option[Int] = None
      insertionBatches.get(batchId).map { case (lines, devices) =>
        val trajectoryPoints = if (dataType == DataImporter.Types.COMPASS) {
          Mission.getTrajectoryPoints(missionId, None, None, None) // for inserting compass value
        } else List()
        for ((line, ind) <- lines.zipWithIndex) {
          val chunksOnLine = if(dataType != DataImporter.Types.ULM_TRAJECTORY)
            line.split("\\t")
          else
            line.split(",")
          if (chunksOnLine.nonEmpty) {
            //println(chunksOnLine.toList)
            dataType match {
              case DataImporter.Types.COMPASS => {
                if (chunksOnLine(0) == "41") {
                  // address - timestamp - value
                  val date = parseDate(chunksOnLine(1))
                  DataLogManager.insertionWorker ! Message.InsertCompassLog(batchId, trajectoryPoints, date, chunksOnLine(2).toDouble)
                } else {
                  DataLogManager.insertionWorker ! Message.SkipLog(batchId) // necessary to update batch progress correctly (when compass error logs are skipped)
                }
              }
              case DataImporter.Types.GPS  => {
                if (chunksOnLine(0) == "48") {
                  val altitudeValue = if (chunksOnLine.length == 5) chunksOnLine(4).toDouble else 0.0
                  //println("--> handle GPS log ! "+ chunksOnLine(0))
                  // address - timestamp - latitude - longitude - elevation
                  val date = parseDate(chunksOnLine(1))
                  DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, missionId, date,
                    chunksOnLine(2).toDouble, chunksOnLine(3).toDouble, altitudeValue, None)
                  if (ind == lines.length-1) {
                    DataLogManager.insertionWorker ! Message.InsertTrajectoryLinestring(missionId)
                  }
                } else {
                  DataLogManager.insertionWorker ! Message.SkipLog(batchId) // necessary to update batch progress correctly (when GPS error logs are skipped)
                }
              }
              case DataImporter.Types.POINT_OF_INTEREST => {
                // timestamp - latitude - longitude - elevation
                val date = parseDate(chunksOnLine(0))
                DataLogManager.insertionWorker ! Message.InsertPointOfInterest(batchId, missionId, date,
                  chunksOnLine(1).toDouble, chunksOnLine(2).toDouble, chunksOnLine(3).toDouble)
              }
              case DataImporter.Types.ULM_TRAJECTORY => {
                // latitude - longitude - elevation - ts
                val (tsDate, timeZone) = DateFormatHelper.ulmTs2JavaDate(chunksOnLine(3))
                DataLogManager.insertionWorker ! Message.InsertGpsLog(batchId, missionId, tsDate,
                  chunksOnLine(0).toDouble, chunksOnLine(1).toDouble, chunksOnLine(2).toDouble, None)
                if (ind == lines.length-1) {
                  DataLogManager.insertionWorker ! Message.InsertTrajectoryLinestring(missionId)
                }
              }
              case _  => {
                val deviceOpt = devices.get(chunksOnLine(0))
                DataLogManager.insertionWorker ! Message.InsertDevice(deviceOpt.get, missionId)
                val date = parseDate(chunksOnLine(1))
                DataLogManager.insertionWorker ! Message.InsertDeviceLog(batchId, missionId, date, chunksOnLine(2).toDouble, chunksOnLine(0))
              }
            }
          }
        }
      }
    }
  }
}
