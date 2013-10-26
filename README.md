dataportal
==========

Play! backend (Scala) to store various sensors data (temperature, GPS, gyro, stream profiles) in spatial database.

The server is developed with the Play! framework (http://www.playframework.com/)

The data is stored in a PostGIS database, using Hibernate as ORM.

The frontend is composed of a bunch of dynamic pages.
They are built with HTML, JS, CSS to create various visualizations of the collected data.

Currently, the geo-referenced data is displayed over a Google map using <a href="http://openlayers.org/">OpenLayers</a>.
In parallel to this "map view" graphs with the sensor values are shown.

The data layer (trajectory on the map) is GeoJSON formatted. The data to feed the graphs are JSON formatted.

The graph of the sensor values is based on <a href="http://d3js.org/">D3.js</a>.

This project is developed under the <a href="http://en.wikipedia.org/wiki/GNU_General_Public_License">GNU General Public License</a> (GPL)
