package models

import javax.persistence._
import org.hibernate.annotations.GenericGenerator
import java.util.Date
import controllers.util.JPAUtil

@Entity
@Table(name = "sensorlog")
class SensorLog(m: Mission, d: Device, ts: Date, v: Double) {
  @Id
  @GeneratedValue(generator="increment")
  @GenericGenerator(name="increment", strategy = "increment")
  @Column(name = "id", unique = true, nullable = false)
  var id: Long = _

  @ManyToOne(cascade=Array(CascadeType.ALL))
  @JoinColumn(name="mission_id")
  var mission: Mission = m

  @ManyToOne(cascade=Array(CascadeType.ALL))
  @JoinColumn(name="device_id")
  var device: Device = d

  var timestamp: Date = ts

  var value: Double = v

  override def toString = "[SensorLog] id: " + id + ", mission: "+ mission.id +", device: "+ device.name +", TS: " + timestamp + " value: "+value

  def this() = this(null, null, null, 0.0) // default constructor - necessary to work with hibernate (otherwise not possible to do select queries)

  /**
   * Persist device type in DB (if it is not already in)
   * @param emOpt An optional EntityManger
   * @return true if success
   */
  def save(emOpt: Option[EntityManager] = None): Boolean = {
    println("[SensorLog] save() - "+ this.toString)
    val em = emOpt.getOrElse(JPAUtil.createEntityManager)
    try {
      if(emOpt.isEmpty) em.getTransaction.begin
      em.persist(this)
      if(emOpt.isEmpty) em.getTransaction.commit
      true
    } catch {
      case ex: Exception => {
        ex.printStackTrace()
        false
      }
    } finally {
      if (emOpt.isEmpty) em.close
    }
  }
}

object SensorLog {

}
