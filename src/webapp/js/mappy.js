// Start Map Setup Section of script
let bufferSize = 5; // How many messages we keep on screen.
let bufferPosition = 0; // Keeps track of where we are in the circular buffer.
let messageBuffer = Array(bufferSize); // Circular buffer to limit messages on screen to messageBufferSize.
let points = {};  // Object to track markers and popups by id

var map = new ol.Map({
    target: "map",
    layers: [
        new ol.layer.Tile({
            source: new ol.source.OSM()
        })
    ],
    view: new ol.View({
        center: ol.proj.fromLonLat([-124.157666036, 40.787330184]), // Center around Eureka, CA
        zoom: 9
    })
});

function addMarker(lon, lat) {
    var layer = new ol.layer.Vector({
        source: new ol.source.Vector({
            features: [
                new ol.Feature({
                    geometry: new ol.geom.Point(ol.proj.fromLonLat([lon, lat]))
                })
            ]
        })
    });
    map.addLayer(layer);
    return layer;
}

function injectPopup(newNode, referenceNode) {
    referenceNode.parentNode.insertBefore(newNode, referenceNode.nextSibling);
}

function createPopupElement(idStr, content) {
    let popId = "popup"+idStr;
    let popCloseId = "popup-closer"+idStr;
    let popupInnerHtml = "<a href=\"#\" id=\""+popCloseId+"\" class=\"ol-popup-closer\"></a><div id=\"popup-content\">"+content+"</div>";
    let popupDiv = document.createElement("div");
    popupDiv.setAttribute("id", popId);
    popupDiv.setAttribute("class", "ol-popup");
    popupDiv.innerHTML = popupInnerHtml.trim();
    return popupDiv;
}

function createPopup(idStr, lon, lat, message) {
     let popupDiv = createPopupElement(idStr, message);
     let closer = popupDiv.querySelector("#popup-closer" + idStr);

     var overlay = new ol.Overlay({
         element: popupDiv,
         autoPan: true,
         autoPanAnimation: {
             duration: 250
         }
     });
     overlay.setPosition(ol.proj.fromLonLat([lon, lat]));
     map.addOverlay(overlay);

     closer.onclick = function() {
         overlay.setVisible(false);
         //overlay.setPosition(undefined);
         closer.blur();
         return false;
     };

     return overlay;
}
// End Map Setup Section of script

// Start socket Setup Section of script
const messageWindow = document.getElementById("messages");  // Socket message window
const sendButton = document.getElementById("send");         // Btn used to ping socket server
const socket = new WebSocket("ws://127.0.0.1:8080/socket"); // The actual web-socket to the socket server
// socket.binaryType = "arraybuffer";                       // Used if we were sending/receiving binary data

// function fired upon web-socket connection Event
socket.onopen = function (event) {
    addMessageToWindow("Connected to socket server.");
};

// function fired upon web-socket receive Event
socket.onmessage = function (event) {
    // Example of getting binary data (like an image) from socket.
    //if (event.data instanceof ArrayBuffer) {let url = URL.createObjectURL(new Blob([event.data]));}
    //console.log(event.data);
    addMessageToWindow(`Got Response: ${event.data}`);
    try {
        // Expect json with { id: <id>, address: <addr>, coordinates: <coordinates> }
        let message = JSON.parse(event.data);
        if("coordinates" in message) {  // Basic check for coordinates.
            if( message.id in points) { // We've already got this popup and marker point, only need to show it
                points[message.id].marker.setVisible(true);
                points[message.id].popup.setVisible(true);
            } else {
                points[message.id] = {
                    "marker": addMarker(message.coordinates[0], message.coordinates[1]),
                    "popup": createPopup(message.id, message.coordinates[0], message.coordinates[1], message.address),
                    "bufferPosition": bufferPosition
                };
                // Track messages displayed
                if(messageBuffer[bufferPosition] != null) { // if the current circular buffer position holds data, clean-up!
                    if(messageBuffer[bufferPosition] in points) {
                        //console.log("Removing: " + messageBuffer[bufferPosition]);
                        map.removeOverlay(points[messageBuffer[bufferPosition]].popup); // Remove popup from map
                        map.removeLayer(points[messageBuffer[bufferPosition]].marker);  // Remove marker from map
                        delete points[messageBuffer[bufferPosition]];                   // Remove old id from points
                    }
                }
                messageBuffer[bufferPosition] = message.id;
                bufferPosition = ++bufferPosition % bufferSize;
            }
        }
    } catch(e){
        console.log(e);
    }
};

// Registering onclick event for ping/echo
sendButton.onclick = function (event) {
    sendMessage("ping");
};

// function to transmit to socket server
function sendMessage(message) {
    socket.send(message);
    addMessageToWindow("Sent: " + message);
}

// function to prepend a message to messageWindow div
function addMessageToWindow(message) {
    messageWindow.innerHTML = `<div>${message}</div>` + messageWindow.innerHTML;
}
