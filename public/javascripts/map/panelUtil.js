var config = new Config();
var controlPanel = new ControlPanel();
var graphPanel = new GraphPanel();
var mapLayerUtil = new MapLayerUtil();
var embeddedGraph; // reference to the graph in bottom part of interface
var zoomedGraph; // reference to the full screen graph (when zoomed)
var dataJsonUrl;
var currentMissions = new Array();
var dataGraphAvailable = false;

/*function refreshSensorField(date, startTime, endTime) {
	var dateNoDash = date.replace(/-/g, "");
	var stNoColumn = startTime.replace(/:/g, "");
	var etNoColumn = endTime.replace(/:/g, "");
	var startDate = dateNoDash+"-"+stNoColumn;
	var endDate = dateNoDash+"-"+etNoColumn;
	$.ajax({
		url: config.URL_PREFIX +"/api/sensors/from/"+ startDate +"/to/"+ endDate
	}).done(function( jsonData ) {
		createSensorSelect(jsonData);
	});
}*/

/**
 * Update the details info
 * @param containerElementId The id of the HTML element that contains the details
 * @param attr The data object {value: string, timestamp: string}
 * @param withDay Indicates if the day of the log has to be printed
 */
var updateInfoDiv = function(containerElementId, attr, withDay) {
	if (!mapLayerUtil.selected) {
		//console.log("updateInfoDiv()");
		var dateStr = attr.timestamp;
		var dateArr = dateStr.split(' ');
		//var coordinates = attr.coordinate_swiss.split(",");
		var containerElementId = mapLayerUtil.getInfoContainerId();
		if (withDay) {
			$('#'+ containerElementId +' > #dataTimePlaceholder').html(dateArr[1]+"  ("+ dateArr[0] +")");
		} else {
			$('#'+ containerElementId +' > #dataTimePlaceholder').html(dateArr[1]);
		}
		var valueHtml = "";
		if (mapLayerUtil.activeGraph != null && mapLayerUtil.activeGraph.sensorLogs.length == 1 && mapLayerUtil.activeGraph.withTooltip) {
			valueHtml = "<b>Value:</b> "+ Number(attr.value).toFixed(3) +"<br/>";
		}
		$('#'+ containerElementId +' > #dataValuePlaceholder').html(valueHtml);
		//$('#'+ containerElementId +' > #eastValuePlaceholder').html(coordinates[0].substr(0, 6));
		//$('#'+ containerElementId +' > #northValuePlaceholder').html(coordinates[1].substr(0, 6));
		$('#'+ containerElementId +' > #speedValuePlaceholder').html(Number(attr.speed).toFixed(2));
	}
}