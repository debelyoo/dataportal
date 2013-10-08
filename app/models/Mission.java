package models;

import com.google.gson.*;
import com.vividsolutions.jts.geom.LineString;
import controllers.util.DateFormatHelper;
import controllers.util.JPAUtil;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import scala.Option;

import javax.persistence.*;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

@Entity
@Table(name = "mission")
public class Mission implements JsonSerializable {


    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    private Date departureTime;

    @Column(name="trajectory")
    @Type(type="org.hibernate.spatial.GeometryType")
    private LineString trajectory;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="vehicle_id")
    private Vehicle vehicle;

    @ManyToMany(
            fetch=FetchType.EAGER,
            targetEntity=Device.class,
            cascade=CascadeType.ALL
    )
    @JoinTable(
            name="equipment",
            joinColumns=@JoinColumn(name="mission_id", referencedColumnName="id"),
            inverseJoinColumns=@JoinColumn(name="device_id", referencedColumnName="id")
    )
    private Collection<Device> devices = new HashSet<>();

    public Mission() {
    }

    public Long getId() {
        return id;
    }

    private void setId(Long id) {
        this.id = id;
    }

    public Date getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Date d) {
        this.departureTime = d;
    }

    public LineString getTrajectory() {
        return this.trajectory;
    }

    public void setTrajectory(LineString tra) {
        this.trajectory = tra;
    }

    public Vehicle getVehicle() {
        return this.vehicle;
    }

    public void setVehicle(Vehicle v) {
        this.vehicle = v;
    }

    public Collection<Device> getDevices() {
        return new HashSet<Device>(devices);
    }

    /*public void setDevices(Set<Device> devs) {
        this.devices = devs;
    }*/

    public void addDevice(Device dev) {
        if (!this.devices.contains(dev))
            this.devices.add(dev);
    }

    public String toString() {
        return id + " -> date: " + departureTime + ", vehicle: " + vehicle.getName();
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(Mission.class, new MissionSerializer()).create().toJson(this);
    }

    /**
     * Custom JSON Serializer for GPS log
     */
    public static class MissionSerializer implements JsonSerializer<Mission> {
        @Override
        public JsonElement serialize(Mission mission, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement missionJson = new JsonObject();
            missionJson.getAsJsonObject().addProperty("id", mission.getId());
            missionJson.getAsJsonObject().addProperty("date", DateFormatHelper.selectYearFormatter().format(mission.getDepartureTime()));
            missionJson.getAsJsonObject().addProperty("vehicle", mission.getVehicle().getName());
            return missionJson;
        }
    }


    /**
     * Save the Mission in Postgres database
     */
    public Boolean save(Option<EntityManager> emOpt) {
        EntityManager em; // = JPAUtil.createEntityManager();
        if (emOpt.isEmpty()) {
            em = JPAUtil.createEntityManager();
        } else {
            em = emOpt.get();
        }
        Boolean res = false;
        try {
            if(emOpt.isEmpty()) em.getTransaction().begin();
            em.persist(this);
            //persistDefensive(em);
            if(emOpt.isEmpty()) em.getTransaction().commit();
            res = true;
        } catch (Exception ex) {
            System.out.println("[WARNING][Mission.save()] "+ ex.getMessage());
        } finally {
            if(emOpt.isEmpty()) em.close();
        }
        return res;
    }

    /*private void persistDefensive(EntityManager em) {
        if (em.contains(this)) {
            em.merge(this);
        } else {
            em.persist(this);
        }
    }*/
}
