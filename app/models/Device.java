package models;

import com.google.gson.*;
import controllers.modelmanager.DeviceManager;
import controllers.util.JPAUtil;
import controllers.util.json.JsonSerializable;
import org.hibernate.annotations.GenericGenerator;
import scala.None;
import scala.Option;

import javax.persistence.*;
import java.util.Collection;
import java.util.HashSet;


@Entity
@Table(name = "device")
public class Device implements JsonSerializable {

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    private String name;
    private String address;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="devicetype_id")
    private DeviceType devicetype;

    @ManyToMany(
            cascade = CascadeType.ALL,
            mappedBy = "devices",
            targetEntity = Mission.class
    )
    private Collection<Mission> missions = new HashSet<>();

    // constructor to create virtual device (fake id)
    public Device(Long id, String n, String a, DeviceType d) {
        this.id = id;
        this.name = n;
        this.address = a;
        this.devicetype = d;
    }

    // constructor to create real device, id will be set when inserted
    public Device(String n, String a, DeviceType d) {
        this.name = n;
        this.address = a;
        this.devicetype = d;
    }

    public Device(){} // default constructor

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public DeviceType getDeviceType() {
        return devicetype;
    }

    public Collection<Mission> getMissions() {
        return new HashSet<Mission>(missions);
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "device")
    protected Collection<SensorLog> sensorLogs;


    public String toString() {
        return id + " -> name: " + this.name + ", address: " + this.address +", devicetype: "+this.devicetype.name();
    }

    @Override
    public String toJson() {
        return new GsonBuilder().registerTypeAdapter(Device.class, new DeviceSerializer()).create().toJson(this);
    }

    /**
     * Custom JSON Serializer for Device
     */
    public static class DeviceSerializer implements JsonSerializer<Device> {
        @Override
        public JsonElement serialize(Device device, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonElement missionJson = new JsonObject();
            missionJson.getAsJsonObject().addProperty("id", device.getId());
            missionJson.getAsJsonObject().addProperty("name", device.getName());
            missionJson.getAsJsonObject().addProperty("address", device.getAddress());
            missionJson.getAsJsonObject().addProperty("devicetype", device.getDeviceType().name());
            return missionJson;
        }
    }

    /**
     * Save the Device in Postgres database
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
            Option<Device> sensorInDb = DeviceManager.getByNameAndAddress(this.name, this.address, emOpt);
            if (sensorInDb.isEmpty()) {
                em.persist(this);
                res = true;
            } else {
                res = true;
            }
            if(emOpt.isEmpty()) em.getTransaction().commit();
        } catch (Exception ex) {
            System.out.println("[WARNING][Device.save] "+ ex.getMessage());
        } finally {
            if(emOpt.isEmpty()) em.close();
        }
        return res;
    }
}
