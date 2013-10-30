/**
 * The control panel (right)
 */
function ControlPanel() {
    this.dateList = {}; // map containing the mission dates
};

/**
 * The initialize function (called when map view loads)
 */
ControlPanel.prototype.initialize = function() {
    var self = this;
    this.createLayerTreeControls();

    $.ajax({
      url: config.URL_PREFIX +"/api/missiondates"
    }).done(function( jsonData ) {
        for (var i=0;i<jsonData.length;i++){
            self.addMissionDate(jsonData[i]);
        }
        self.createCalendar();
    });
};

/**
 * Create the controls to hide/show the various layers
 */
ControlPanel.prototype.createLayerTreeControls = function() {
    //	Defines the tree layer. First the node ...
    var layerRoot = new Ext.tree.TreeNode({
        text: "Layers",
        expanded: true,
    });

    //	... then add a child (Overlay) ...
    layerRoot.appendChild(new GeoExt.tree.LayerContainer({
        text: "Overlays",
        map: mapLayerUtil.mapPanel,
        expanded: true,
        singleClickExpand: true,
        loader: {
            filter: function(record) {
                return record.get("layer").isBaseLayer== true
            }
        }
    }));

    //	... a child containing the trajectories ...
    layerRoot.appendChild(new GeoExt.tree.LayerContainer({
        text: "Trajectories",
        map: mapLayerUtil.mapPanel,
        expanded: true,
        singleClickExpand: true,
        loader:{
            filter: function(record){
                res=record.get("layer").CLASS_NAME == "OpenLayers.Layer.Vector" && record.get("layer").isBaseLayer == false;
                return res
            }
        }
    }));

    //	... and an other child containing the layers
    layerRoot.appendChild(new GeoExt.tree.LayerContainer({
        text: "Layers",
        map: mapLayerUtil.mapPanel,
        expanded: false,
        singleClickExpand: true,
        loader:{
            filter: function(record){
                res = record.get("layer").CLASS_NAME == "OpenLayers.Layer.WMS" && record.get("layer").isBaseLayer == false;
                return res

            }
        }

    }));

    // display the tree in the appropriate DOM (layerTree defined in map.html)
    var tree = new Ext.tree.TreePanel({
        renderTo: "layerTree",
        frame: false,
        border: false,
        root: layerRoot,
        rootVisible: false
    });


    // legend panel
    legendPanel = new GeoExt.LegendPanel({
        border: false,
        layerStore: mapLayerUtil.mapPanel.layers,
        renderTo: "legendPanel",
        filter: function(record){
            return record.get("layer").CLASS_NAME == "OpenLayers.Layer.Vector"
        }
    });
};

ControlPanel.prototype.onLemanBtnClicked = function() {
    var ptLeman = new OpenLayers.LonLat(6.59,46.39).transform(mapLayerUtil.epsg4326,mapLayerUtil.epsg900913);
    zoomLeman=10;
    mapLayerUtil.mapPanel.map.setCenter(ptLeman,zoomLeman);
};

ControlPanel.prototype.onBaikalBtnClicked = function() {
    var ptBaikal= new OpenLayers.LonLat(106.7,52.10).transform(mapLayerUtil.epsg4326,mapLayerUtil.epsg900913);
    zoomBaikal=10;
    mapLayerUtil.mapPanel.map.setCenter(ptBaikal,zoomBaikal);
};

ControlPanel.prototype.onAddLayersClicked = function() {
    this.getMissionsForDates($('#calendar').DatePickerGetDate('ymd'));
};

ControlPanel.prototype.onResetClicked = function() {
    graphPanel.closePanel();
    currentMissions = new Array(); // reset array
    dataGraphAvailable = false;
    //$('#dataSelectPanel').hide();
    $('#calendar').DatePickerClear();
    mapLayerUtil.removeLayers()
    $('#speedVectorPlaceholder').hide();
    $('#dataSelectPanel').hide();
    $('#deviceSelectRow').hide();
    $('#dataGraphPlaceholder').hide();
};

ControlPanel.prototype.createCalendar = function() {
    var self = this;
    // draw calendar
    $( "#calendar" ).DatePicker({
        flat: true,
        current: "2013-07-10",
        date: "2013-07-10",
        format: "Y-m-d",
        mode:"multiple",
        onRender: function(date){
            var res = self.checkSpecialDate(date);
            return res;
        }
    });
};

/**
 * Check if calendar date is a special date (defined in dateList)
 * @param date The current date to check
 */
ControlPanel.prototype.checkSpecialDate = function(date) {
    var res = { disabled: true };
    //console.log(dateList);
    for (var d in this.dateList) {
        var specialDate = new Date(d);
        // For some reason that eludes me, need the day before...
        var tmp = new Date(
            specialDate.getFullYear(),
            specialDate.getMonth(),
            specialDate.getDate() - 1,
            specialDate.getHours(),
            specialDate.getMinutes(),
            specialDate.getSeconds(),
            specialDate.getMilliseconds()
        )
        //console.log(date.toISOString().substring(0,10) + " - " +tmp.toISOString().substring(0,10));
        if (date.toISOString().substring(0,10)==tmp.toISOString().substring(0,10)){
            res = {
                disabled: false,
                className: this.dateList[d]
            }
        }
    };
    return res;
};

/**
 * Add a mission date in dateList with the proper class name (for calendar coloration)
 * @param mission The mission to consider
 */
ControlPanel.prototype.addMissionDate = function(mission) {
    var className;
    if (isUlmMission(mission)) {
        className = config.ULM_DATE_CLASSNAME;
    } else if (isCatamaranMission(mission)) {
        className = config.CAT_DATE_CLASSNAME;
    }
    var nDate = new Date(mission.departuretime.substring(0,10));
    if (!this.dateList.hasOwnProperty(nDate)) {
        this.dateList[nDate] = className;
    } else if (this.dateList[nDate] != className) {
        // flight & cruise exist for this date
        this.dateList[nDate] = config.ULM_CAT_DATE_CLASSNAME;
    }
};

/**
 * Get the missions for specific dates
 * @param dateArr The list of date
 */
ControlPanel.prototype.getMissionsForDates = function(dateArr) {
    //var self = this;
    //nbSelectedDates = dateArr.length;
    if (dateArr.length > 0) {
        $('#loadingGifPlaceholder').show();
    }
    //nbMissionListReceived = 0;
    for (var i=0; i < dateArr.length; i++) {
        $.ajax({
            url: config.URL_PREFIX +"/api/missions/fordate/"+dateArr[i]
        }).done(function( missions ) {
            //nbMissionListReceived++;
            //var mode = $("#selectMode").val();
            for (var j=0;j<missions.length;j++){
                currentMissions.push(missions[j]);
                // add path for each mission
                mapLayerUtil.addLayers(missions[j]);
            }
        });
    }
};

ControlPanel.prototype.togglePanel = function() {
    //var slideDistance = 430;
    //console.log($("#graphPanel").position().left);
    if (!$("#controlPanelHideBtnPlaceholder").hasClass('rotated180')) {
        $("#controlPanelHideBtnPlaceholder").addClass('rotated180');
        $("#controlPanelHideBtnPlaceholder").animate({left: '+=95%'}, 1000)
        $("#controlPanel").animate({left: '+=100%'}, 1000)
    } else {
        $("#controlPanelHideBtnPlaceholder").removeClass('rotated180');
        $("#controlPanelHideBtnPlaceholder").animate({left: '-=95%'}, 1000)
        $("#controlPanel").animate({left: '-=100%'}, 1000)
    }
};