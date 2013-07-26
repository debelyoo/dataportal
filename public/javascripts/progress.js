function getProgress(batchId, progressType, urlPrefix) {
    $.ajax({
        url: urlPrefix + "/batch/progress/"+ batchId
    }).done(function( jsonData ) {
        var prog = jsonData['progress'];
        var hint = jsonData['hint'];
        $("#progressPlaceholder").html(prog + "%");
        $(".progress .bar").css({'width': prog + "%"});
        $("#hintPlaceholder").html(hint);
        if (prog != 100) {
            // update progress every 1 sec
            setTimeout(function() {getProgress(batchId, progressType, urlPrefix)}, 1000)
        } else {
            $("#statusPlaceholder").html(progressType + " terminated !");
            $(".progress").removeClass('active');
        }
    });
}