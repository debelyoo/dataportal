package models.spatial

import controllers.util.{DateFormatHelper, JPAUtil}
import javax.persistence.{TemporalType, NoResultException}
import scala.reflect.{ClassTag, classTag}
import java.util.Date
import scala.collection.JavaConversions._

object DataLogManager {
  /**
   * Get a data log by Id (give log type in parameter using scala 2.10 ClassTag - https://wiki.scala-lang.org/display/SW/2.10+Reflection+and+Manifest+Migration+Notes)
   * @param sId The log id
   * @tparam T The type of data log to get
   * @return The log
   */
  def getById[T: ClassTag](sId: Long): Option[T] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      val q = em.createQuery("from "+ clazz.getName +" where id = "+sId)
      val log = q.getSingleResult.asInstanceOf[T]
      em.getTransaction().commit()
      em.close()
      Some(log)
    } catch {
      case nre: NoResultException => None
      case ex: Exception => ex.printStackTrace; None
    }
  }

  /**
   * Get the data logs of a type between a time interval
   * @param startTime The start time of the interval
   * @param endTime The end time of the interval
   * @tparam T The type of data log to get
   * @return A list of the logs in the specified time interval
   */
  def getByTimeInterval[T: ClassTag](startTime: Date, endTime: Date): List[T] = {
    val em = JPAUtil.createEntityManager()
    try {
      em.getTransaction().begin()
      val clazz = classTag[T].runtimeClass
      // where timestamp BETWEEN '2013-05-14 16:30:00'::timestamp AND '2013-05-14 16:33:25'::timestamp ;
      val queryStr = "from "+ clazz.getName +" " +
        "where timestamp between :start and :end"
      //println(queryStr)
      val q = em.createQuery(queryStr)
      q.setParameter("start", startTime, TemporalType.TIMESTAMP)
      q.setParameter("end", endTime, TemporalType.TIMESTAMP)
      //println(q.getResultList)
      val logs = q.getResultList.map(_.asInstanceOf[T]).toList
      em.getTransaction().commit()
      em.close()
      logs
    } catch {
      case nre: NoResultException => List()
      case ex: Exception => ex.printStackTrace; List()
    }
  }

  /**
   * Converts a Double value to Int
   * @param valueToTest The value to convert
   * @return an option with the Int value
   */
  def doubleToInt(valueToTest: Double): Option[Int] = {
    try {
      val intValue = valueToTest.toInt
      Some(intValue)
    } catch {
      case ex: Exception => None
    }
  }

}
