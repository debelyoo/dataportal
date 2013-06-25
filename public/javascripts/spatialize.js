function getProgress(batchId) {
    $.ajax({
        url: "/playproxy/spatialize/progress/"+ batchId
    }).done(function( jsonData ) {
        var prog = jsonData['progress'];
        $("#progressPlaceholder").html(prog + "%");
        if (prog != 100) {
            setTimeout(function() {getProgress(batchId)}, 1000)
        } else {
            $("#statusPlaceholder").html("Spatialization terminated !");
        }
    });
}