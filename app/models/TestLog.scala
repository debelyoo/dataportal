package models

import javax.persistence.{Id, Table, Entity}
import controllers.util.JPAUtil

@Entity
@Table(name = "testlog")
class TestLog(@Id col1: String, col2: String, col3: String) {
  override def toString = "col1: "+col1+", col2: "+col2+", col3: "+col3

  def save {
    println("[TestLog] save() - "+ this.toString)
    val em = JPAUtil.createEntityManager()
    em.getTransaction().begin()
    em.persist(this)
    em.getTransaction().commit()
    em.close()
  }
}
