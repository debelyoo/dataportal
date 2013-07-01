function getProgress(batchId, progressType, urlPrefix) {
    $.ajax({
        url: urlPrefix + "/batch/progress/"+ batchId
    }).done(function( jsonData ) {
        var prog = jsonData['progress'];
        $("#progressPlaceholder").html(prog + "%");
        if (prog != 100) {
            setTimeout(function() {getProgress(batchId, progressType, urlPrefix)}, 1000)
        } else {
            $("#statusPlaceholder").html(progressType + " terminated !");
        }
    });
}