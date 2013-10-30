var config = new Config();
var controlPanel = new ControlPanel();
var graphPanel = new GraphPanel();
var mapLayerUtil = new MapLayerUtil();
var embeddedGraph; // reference to the graph in bottom part of interface
var zoomedGraph; // reference to the full screen graph (when zoomed)
var dataJsonUrl;
var currentMissions = new Array();
var dataGraphAvailable = false;