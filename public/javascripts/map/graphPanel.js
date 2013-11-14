/**
 * The graph panel (bottom)
 */
function GraphPanel() {};

/**
 * Create the field (drop down list) to select the trajectory for which we want the devices graph
 * This function is called as a called from mapLayerUtil.js when all layer features are loaded
 */
GraphPanel.prototype.createPathSelectForData = function() {
    //console.log("createPathSelectForData()", currentMissions.length);
    var options = "";
    //var options = "<option value=0>Select a trajectory</option>"; // default value
    for (var i = 0; i < currentMissions.length; i++) {
        var mission = currentMissions[i];
        var missionName = mission.date + " "+ mission.time +" - " + mission.vehicle
        dataGraphAvailable = true;
        options += "<option value="+ mission.id +">"+ missionName +"</option>";
    }

    var pathSelect = "<select id=\"pathSelect\" class=\"form-control\">" + options + "</select>";
    $('#trajectorySelectPlaceholder').html(pathSelect);
    $('#pathSelect').change(function() {
        var missionId = $("#pathSelect").val();
        if (missionId > 0) {
            // get sensors for this trajectory
            graphPanel.getDevicesForMission(getCurrentMissionById(missionId));
            graphPanel.getMissionMaximumSpeed(missionId); // every time the selected trajectory changes -> get max speed and heading
        } else {
            $('#deviceSelectRow').hide();
        }
    });
    if (dataGraphAvailable) {
        graphPanel.getDevicesForMission(currentMissions[0]);
        graphPanel.getMissionMaximumSpeed(currentMissions[0].id);
        $('#dataSelectPanel').show();
        graphPanel.togglePanel();
    }
};

GraphPanel.prototype.getDevicesForMission = function(mission) {
    var self = this;
    $.ajax({
        url: config.URL_PREFIX +"/api/devices/formission/"+mission.id
    }).done(function( jsonData ) {
        self.createDeviceSelectForData(jsonData, mission);
    });
};

/**
 * Create the select field for the devices associated with a trajectory (or mission)
 * @param jsonData A JSON object containing the device list
 * @param mission The selected mission
 */
GraphPanel.prototype.createDeviceSelectForData = function(jsonData, mission) {
    var self = this;
    var devices = jsonData['devices'];
    //console.log("createDeviceSelectForData()", devices);
    var options;
    if (isCatamaranMission(mission)) {
        options = "<option value=\"speed--0\">Speed</option>";
    } else {
        options = "<option value=\"altitude--0\">Altitude</option>";
    }

    devices.map(function(d) {
        var deviceName, deviceId;
        if (d['id'] != 0) {
            deviceName = d['name'];
            deviceId = d['id'];
        } else {
            deviceName = "All "+d['name'];
            deviceId = "all";
        }

        var datatype = d['device_type'];
        // add in select values, only data that can be plotted as 'line'
        if (d['plot_type'] == "line") {
            // id is datatype--ID (ex: temperature--3)
            options += "<option value=\""+ datatype +"--"+ deviceId +"\">"+ deviceName +"</option>"
        }
    });
    var pathSelect = "<select id=\"deviceSelect\" class=\"form-control\">" + options + "</select>";
    $('#deviceSelectPlaceholder').html(pathSelect);
    $('#deviceSelect').change(function() {
        //console.log("load data for device: "+did+" and mission: "+missionId);
        var deviceTypeAndId = self.getDatatypeAndDeviceId();
        self.getDeviceData(deviceTypeAndId[0], mission.id, deviceTypeAndId[1]);
    });
    $('#deviceSelectRow').show();
    /// show graph with altitude or speed (at load)
    var deviceTypeAndId = this.getDatatypeAndDeviceId();
    this.getDeviceData(deviceTypeAndId[0], $("#pathSelect").val(), deviceTypeAndId[1]);
};

GraphPanel.prototype.getDatatypeAndDeviceId = function() {
    var deviceId = $("#deviceSelect").val();
    var deviceParts = deviceId.split('--');
    //var datatype = deviceParts[0];
    //var did = deviceParts[1];
    return deviceParts;
};

/**
 * Get the data for the selected device
 * @param datatype The type of data requested
 * @param missionId The id of the mission
 * @param deviceId The id of the device
 */
GraphPanel.prototype.getDeviceData = function(datatype, missionId, deviceId) {
    //console.log("[panelUtil.js] getDeviceData()");
    dataJsonUrl = config.URL_PREFIX +"/api/data?data_type="+ datatype +"&mission_id="+missionId+"&device_id="+deviceId;
    var url = dataJsonUrl + "&max_nb="+config.MAX_NB_DATA_POINTS_ON_MAP
    console.log(url);
    var graphHeight = $('#graphPanel').height();
    var graphWidth = $('#dataGraphPlaceholder').width();
    embeddedGraph = mapLayerUtil.activeGraph
    if(!embeddedGraph) {
        //console.log("create embedded graph !");
        embeddedGraph = new GraphD3();
        embeddedGraph.containerElementId = 'dataGraphPlaceholder';
        embeddedGraph.svgElementId = 'svgElement1';
        embeddedGraph.linkWithGeoData = true;
        embeddedGraph.heightContainer = graphHeight;
        embeddedGraph.widthContainer = graphWidth;
        embeddedGraph.initialize(); // call initialize explicitly
        mapLayerUtil.activeGraph = embeddedGraph;
    }
    embeddedGraph.refreshSensorGraph(url, false);
    $("#graphPanelZoomBtnPlaceholder").attr("onclick", "graphPanel.zoomGraph('"+ datatype +"','"+ missionId +"','"+ deviceId +"')");
    $('#dataGraphPlaceholder').show();
};

/**
 * Get maximum speed (if available) and heading (if avaialable) of a mission
 * @param missionId The id of the mission
 */
GraphPanel.prototype.getMissionMaximumSpeed = function(missionId) {
    $.ajax({
        url: config.URL_PREFIX +"/api/maxspeed/formission/"+ missionId
    }).done(function( jsonData ) {
        mapLayerUtil.maximumSpeed = jsonData.max_speed;
        mapLayerUtil.headingAvailable = jsonData.heading_available;
    });
};

/**
 * Create the zoomed graph and show it
 * @param datatype The type of data displayed
 * @param mid The mission id
 * @param sid The device id
 */
GraphPanel.prototype.zoomGraph = function(datatype, mid, sid) {
    // disable zoom if multiple sets are selected, because zoomed graph is buggy
    if ($("#setSelect").val() == "all") {
        alert("Zoom is not available on multiple sets. Please select only one set.");
    } else {
        $("#graphZoomPanel").show();
        //console.log()
        if(zoomedGraph == undefined) {
            zoomedGraph = new GraphD3();
            zoomedGraph.containerElementId = 'graphZoomPlaceholder';
            zoomedGraph.svgElementId = 'svgElement2';
            zoomedGraph.margin = {top: 50, right: 150, bottom: 50, left: 100};
            zoomedGraph.widthContainer = $("#graphZoomPlaceholder").width();
            zoomedGraph.heightContainer = $("#graphZoomPlaceholder").height();
            zoomedGraph.datatype = datatype;
            zoomedGraph.sensorId = sid;
            zoomedGraph.missionId = mid;
            zoomedGraph.zoomable = true;
            zoomedGraph.ticksIntervalX = 5;
            zoomedGraph.nbTicksX = 10;
            zoomedGraph.nbTicksY = 14;
            zoomedGraph.linkWithGeoData = false;
            zoomedGraph.withTooltip = true;
            zoomedGraph.initialize();
        } else {
            zoomedGraph.datatype = datatype;
            zoomedGraph.sensorId = sid;
            zoomedGraph.missionId = mid;
        }
        var dataJsonUrlZoomedGraph = dataJsonUrl + "&max_nb="+config.MAX_NB_DATA_POINTS_SINGLE_GRAPH+"&sync_with_trajectory=false"
        zoomedGraph.refreshSensorGraph(dataJsonUrlZoomedGraph);
        mapLayerUtil.activeGraph = zoomedGraph;
    }
};

GraphPanel.prototype.closeZoomGraph = function() {
    zoomedGraph.resetZoomBounds();
    mapLayerUtil.activeGraph = embeddedGraph;
    $("#graphZoomResetZoomBtn").hide();
    $('#graphZoomPanel').hide();
};

GraphPanel.prototype.togglePanel = function() {
    //console.log("toggleGraphPanel()");
    if (dataGraphAvailable) {
        var maxTop = $("#mapPanel").height() - 50;
        //if ($("#graphPanelContainer").position().top < maxTop) {
        if (!$("#graphPanelHideBtnPlaceholder").hasClass('rotated270')) {
            $("#graphPanelHideBtnPlaceholder").removeClass('rotated90');
            $("#graphPanelHideBtnPlaceholder").addClass('rotated270');
            $("#graphPanelContainer").animate({top: '+=25%'}, 1000)
        } else {
            $("#graphPanelHideBtnPlaceholder").removeClass('rotated270');
            $("#graphPanelHideBtnPlaceholder").addClass('rotated90');
            $("#graphPanelContainer").animate({top: '-=25%'}, 1000)
        }
    }
};

GraphPanel.prototype.closePanel = function() {
    // if graph panel is visible (open), close it (slide down)
    if (!$("#graphPanelHideBtnPlaceholder").hasClass('rotated270')) {
        this.togglePanel();
    }
};

/**
 * Update the details info
 * @param containerElementId The id of the HTML element that contains the details
 * @param attr The data object {value: string, timestamp: string}
 * @param withDay Indicates if the day of the log has to be printed
 */
GraphPanel.prototype.updateInfoDiv = function(containerElementId, attr, withDay) {
    if (!mapLayerUtil.selected) {
        //console.log("updateInfoDiv()");
        var dateStr = attr.timestamp;
        var dateArr = dateStr.split(' ');
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
        $('#'+ containerElementId +' > #speedValuePlaceholder').html(Number(attr.speed).toFixed(2));
    }
};
