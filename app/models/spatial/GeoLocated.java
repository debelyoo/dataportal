package models.spatial;


import com.vividsolutions.jts.geom.Point;
import controllers.util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.Query;

public interface GeoLocated {

    /**
     * Update the geo position
     * @param pos The new geo position
     * @return true if success
     */
    /*Boolean updateGeoPos(Point pos) {
        //println("[Sensor] update() - "+ this.toString)
        EntityManager em = JPAUtil.createEntityManager();
        // UPDATE sensor SET datatype='temperature' WHERE id=16;
        try {
            em.getTransaction().begin();
            // geo_pos = ST_GeomFromText('POINT(-71.060316 48.432044)', 4326)
            String queryStr = "UPDATE "+ this.getClass().getName() +" SET geo_pos = ST_GeomFromText('POINT("+ pos.getX() +" "+ pos.getY() +")', 4326) WHERE id="+this.id;
            Query q = em.createQuery(queryStr);
            q.executeUpdate();
            em.getTransaction().commit();
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            em.close();
        }
    }*/
}
