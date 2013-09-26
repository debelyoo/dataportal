package models.elemoImport;

import com.vividsolutions.jts.geom.Point;
import controllers.util.JPAUtil;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "trajectorypoint")
public class TrajectoryPoint {


    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    private Date timestamp;

    @Column(name="coordinate")
    @Type(type="org.hibernate.spatial.GeometryType")
    private Point coordinate;

    @Column(name="outing_id")
    private int outingId;

    public TrajectoryPoint() {
    }

    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public Point getCoordinate() {
        return this.coordinate;
    }

    public void setCoordinate(Point pos) {
        this.coordinate = pos;
    }

    public int getOutingId() {
        return this.outingId;
    }

    public void setOutingId(int oid) {
        this.outingId = oid;
    }

    public String toString() {
        return id + " -> ts: " + timestamp + ", point: " + coordinate.toString();
    }


    /**
     * Save the GpsLog in Postgres database
     */
    public Boolean save() {
        EntityManager em = JPAUtil.createEntityManager();
        Boolean res = false;
        try {
            em.getTransaction().begin();
            em.persist(this);
            em.getTransaction().commit();
            res = true;
        } catch (Exception ex) {
            System.out.println("[WARNING] "+ ex.getMessage());
        } finally {
            em.close();
        }
        return res;
    }

}
