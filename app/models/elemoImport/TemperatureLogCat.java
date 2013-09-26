package models.elemoImport;

import controllers.util.JPAUtil;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "temperaturelogcat")
public class TemperatureLogCat {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    private Long id;

    private Date timestamp;

    private Double value;

    @Column(name="device_id")
    private int deviceId;

    @Column(name="outing_id")
    private int outingId;

    public TemperatureLogCat() {
    }

    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int did) {
        this.deviceId = did;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date ts) {
        this.timestamp = ts;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        this.value = v;
    }

    public int getOutingId() {
        return this.outingId;
    }

    public void setOutingId(int oid) {
        this.outingId = oid;
    }

    @Override
    public String toString() {
        return "[TemperatureLogCat] id: "+ this.id +", device id: "+this.deviceId +", TS: "+this.timestamp +", " +
                "value: "+this.value +", outing id: "+outingId;
    }



    /**
     * Save the TemperatureLogCat in Postgres database
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
            // no need to rollback, hibernate does it automatically in case of error
            System.out.println("[WARNING] "+ ex.getMessage());
        } finally {
            em.close();
        }
        return res;
    }

}