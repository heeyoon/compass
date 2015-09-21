var elevator;
var map;
var geoJSON;
var request;
var gettingData = false;
var openWeatherMapKey = "deb1e772542b9775d0f381f3fc2b1509";
var marker = false;
var latLng = false;
var locationDataSelected = false;
var weatherListener1;
var weatherListener2;

function initialize() {

  elevator = new google.maps.ElevationService();
  var mapOptions = {
      center: new google.maps.LatLng(42.3598, -7.0921),
      zoom: 3,
      mapTypeId: google.maps.MapTypeId.ROADMAP,
		  minZoom: 3,
		  maxZoom:20,
		  mapTypeControl: false,
		  streetViewControl: false,
		  zoomControl: false,
		  panControl: false,
styles: [

  {
    "featureType": "landscape.natural",
    "elementType": "geometry.fill",
    "stylers": [
      { "color": "#f5f5f2" },
      { "visibility": "on" }
    ]
  },{
    "featureType": "poi",
    "stylers": [
      { "visibility": "off" }
    ]
  },{
        "featureType": "poi.park",
        "elementType": "geometry.fill",
        "stylers": [
            {
                "visibility": "on"
            },
            {
                "color": "#9BE4C4"
            }
        ]
    },{
    "featureType": "transit",
    "stylers": [
      { "visibility": "off" }
    ]
  },{
    "featureType": "landscape.man_made",
    "elementType": "geometry.fill",
    "stylers": [
      { "color": "#ffffff" },
      { "visibility": "on" }
    ]
  },{
    "featureType": "road.arterial",
    "stylers": [
      { "visibility": "simplified" },
      { "color": "#fee379" }
    ]
  },{
    "featureType": "road.highway",
    "elementType": "labels.icon",
    "stylers": [
      { "visibility": "off" }
    ]
  },{
    "featureType": "landscape",
    "stylers": [
      { "color": "#F7F8FF" }
    ]
  },{
    "featureType": "road",
    "stylers": [
      { "color": "#ffffff" }
    ]
  },{
    "featureType": "water",
    "stylers": [
      { "color": "#A9D5F1" }
    ]
  },{
    "featureType": "landscape",
    "stylers": [
      { "visibility": "off" }
    ]
  },
],

		};
  map = new google.maps.Map(document.getElementById('map'),
      mapOptions);



  weatherListener1 = google.maps.event.addListener(map, 'idle', checkIfDataRequested);
// Sets up and populates the info window with details
  weatherListener2 = map.data.addListener('click', function(event) {
     if (locationDataSelected) {
      locationDataInfoBox();
      $("#weather-picture").html("<img src=" + event.feature.getProperty("icon") + " class='weather-picture'>");
      $(".weather-city").html(event.feature.getProperty("city"));
      $("#weather-temp").html(event.feature.getProperty("temperature") + "&deg;C");
      $("#weather-name").html(event.feature.getProperty("weather"));
      var weather_coordinates = event.feature.getProperty("coordinates");
      map.panTo(new google.maps.LatLng(weather_coordinates[1], weather_coordinates[0]));
     }
   });



  // This event listener will call addMarker() when the map is clicked.
  // google.maps.event.addListener(map, 'click', function(event) {
  //   addMarker(event.latLng);
  //   latLng = event.latLng;
  // });

  // Adds a marker at the center of the map.

  // Load initial suggestions
  loadSuggestions();
  initializeJRange();
  initializeTypeAhead();
}



// Add a marker to the map and push to the array.
var markers = [];
function clearMarkers(){
  markers.forEach(function (marker) {
    marker.setMap(null);
  })
  markers = [];
}
function addMarker(location) {
  var pinIcon = new google.maps.MarkerImage(
    "css2/compass_files/img/marker.png",
    null, /* size is determined at runtime */
    null, /* origin is 0,0 */
    null, /* anchor is bottom center of the scaled image */
    new google.maps.Size(39,40)
);

  $.extend( location, {
    position: {
      lng: location.longitude,
      lat: location.latitude
    },
    map: map,
    icon: pinIcon
  } );
  var marker = new google.maps.Marker(location);
  markers.push(marker);
  google.maps.event.addListener(marker,'click',function() {
    showMarker(marker);
  })
}

function showMarker(marker){
  $('#title').text(marker.name);
  $('#address').text(marker.address);
  $('#description').text(marker.description);
  $('#reviewer-name').text("No one");
  $('#review-content').text("anything yet");
  if (marker.reviewList.length){
    var review = marker.reviewList[Math.floor(Math.random() * marker.reviewList.length)];
    $('#reviewer-name').text(review.author);
    $('#review-content').text(review.text);
    ratingStars(review.rating);
  }
  $("#ib").attr('href', '/'+marker.locationid);
  console.log(marker);
  if (marker.placeType === 'Accommodation'){
    $('#instantbook').show();
    $('#instantview').hide();
  } else {
    $('#instantbook').hide();
    $('#instantview').show();
  }
}

google.maps.event.addDomListener(window, 'load', initialize);
function loadSuggestions() {

 if (!locationDataSelected) {
  console.log("loading markers1");
  var params = {
    action: 'getSuggestions',
    friendOnly : $('.fbFriends').hasClass("shaded"),
    reservationOnly : $('.reservations').hasClass("shaded"),
    types: $('.type-input').val().replace(" ",""),
    hometown: $('.hometown-input').val()
  }
  var ageRange = $('.slider-input').val().split(',');
  params.ageLow = ageRange[0];
  params.ageHigh = ageRange[1];
  $.get('/Compass',params)
  .fail(function (e) {
    alert("Get Suggestion Failed");
  })
  .done(function (data) {
    clearMarkers();
    data.forEach(
      function (location) {
        if (location.longitude != 0 && location.latitude !=0) {
          addMarker(location);
        }
        drawIcons(geoJSON);
    });
    var markerCluster = new MarkerClusterer(map, markers, {gridSize: 50, maxZoom: 10});
  })
 }
}


 var checkIfDataRequested = function() {
  if (locationDataSelected){
    // Stop extra requests being sent
    while (gettingData === true) {
      request.abort();
      gettingData = false;
    }
    getCoords();
  }
};
  // Get the coordinates from the Map bounds
var getCoords = function() {
    var bounds = map.getBounds();
    var NE = bounds.getNorthEast();
    var SW = bounds.getSouthWest();
    getWeather(NE.lat(), NE.lng(), SW.lat(), SW.lng());
};
  // Make the weather request
var getWeather = function(northLat, eastLng, southLat, westLng) {
    gettingData = true;
    var requestString = "http://api.openweathermap.org/data/2.5/box/city?bbox="
                        + westLng + "," + northLat + "," //left top
                        + eastLng + "," + southLat + "," //right bottom
                        + map.getZoom()
                        + "&cluster=yes&format=json"
                        + "&APPID=" + openWeatherMapKey;
    request = new XMLHttpRequest();
    request.onload = proccessResults;
    request.open("get", requestString, true);
    request.send();
};
  // Take the JSON results and proccess them
var proccessResults = function() {
    var results = JSON.parse(this.responseText);
    if (results.list.length > 0) {
        resetData();
        for (var i = 0; i < results.list.length; i++) {
          geoJSON.features.push(jsonToGeoJson(results.list[i]));
        }
        drawIcons(geoJSON);
    }
};
  var infowindow = new google.maps.InfoWindow();
  // For each result that comes back, convert the data to geoJSON
  var jsonToGeoJson = function (weatherItem) {
    var feature = {
      type: "Feature",
      properties: {
        city: weatherItem.name,
        weather: weatherItem.weather[0].main,
        temperature: weatherItem.main.temp,
        min: weatherItem.main.temp_min,
        max: weatherItem.main.temp_max,
        humidity: weatherItem.main.humidity,
        pressure: weatherItem.main.pressure,
        windSpeed: weatherItem.wind.speed,
        windDegrees: weatherItem.wind.deg,
        windGust: weatherItem.wind.gust,
        icon: "http://openweathermap.org/img/w/"
              + weatherItem.weather[0].icon  + ".png",
        coordinates: [weatherItem.coord.lon, weatherItem.coord.lat]
      },
      geometry: {
        type: "Point",
        coordinates: [weatherItem.coord.lon, weatherItem.coord.lat]
      }
    };
    // Set the custom marker icon
    map.data.setStyle(function(feature) {
      return {
        icon: {
          url: feature.getProperty('icon'),
          anchor: new google.maps.Point(25, 25)
        }
      };
    });
    // returns object
    return feature;
  };
  // Add the markers to the map
  var drawIcons = function (weather) {
     map.data.addGeoJson(geoJSON);
     // Set the flag to finished
     gettingData = false;
  };
  // Clear data layer and geoJSON
  var resetData = function () {
    geoJSON = {
      type: "FeatureCollection",
      features: []
    };
    map.data.forEach(function(feature) {
      map.data.remove(feature);
    });
  };



function initializeJRange(){
  $('.slider-input').jRange({
      from: 20,
      to: 50,
      step: 1,
      scale: [20,30,40,50],
      format: '%s',
      width: 100,
      showLabels: false,
      isRange : true,
      onstatechange: function (value) {
      loadSuggestions();
      }
  });
}

function toggleFbFriends(){
  var isSelected = $('.fbFriends').hasClass("shaded");
  if (isSelected) {
    $('.fbFriends').removeClass("shaded");
  } else {
    if (locationDataSelected) {
        locationData();
    }
    $('.fbFriends').addClass("shaded");
  }
  loadSuggestions();
}

function toggleReservations(){
  var isSelected = $('.reservations').hasClass("shaded");
  if (isSelected) {
    $('.reservations').removeClass("shaded");
  } else {
    $('.reservations').addClass("shaded");
  }
  loadSuggestions();
}

function initializeTypeAhead(){
  $('.type-input,.hometown-input').change(function () {
    loadSuggestions();
  })
}


function locationData(){
//called when button is toggled, displays weather and clears all other views off, resets them when this is turned off
	if (!locationDataSelected) {
		locationDataSelected = true;
		$('#locationData').addClass("shaded");
		if ($('.fbFriends').hasClass("shaded")) {
			toggleFbFriends();
		}
		clearMarkers();
		checkIfDataRequested();
		locationDisplayText();
		locationDataFormatBox(false);
	}
	else {
		locationDataSelected = false;
		$('#locationData').removeClass("shaded");
		resetData();
                locationDataFormatBox(true);
		resetInfoBox();
	        toggleFbFriends();
	}
}

function resetInfoBox(){
//resets contents of infobox because locationData replaces that format with something else, called on exit of locationData
//  newCode = '<div id="title">Title of Selected Location</div><div id="rating"><div class="ratingbubble"></div><div class="ratingbubble"></div><div class="ratingbubble"></div><div class="ratingbubble"></div> </div><br><div id="description">View a description of the location you are checking reviews from here.</div><div id="recommender"><span id="name">Recommender</span> say'+"'"+'s ...</div><div id="review">View a review here by a marker you select!</div><a id="ib" href="https://www.tripadvisor.com/HotelBookingRoomSelectionHtml?a=2&ik=d7c60d1b133246f79775ace1763eaae5&d=113317&ci_day=22&ep=437&co_day=23&r=1&ci_month=9&co_month=9&co_year=2015&src_0=63261472&tp=Hotels_MainList&ci_year=2015&cpn=IB_SabreHospitalitySolu_1Fv9i6k"><div id="instantbook">Book Now &nbsp;></div></a>';

  newCode = '<div id="title">Title</div><div id="address">Address</div><div id="rating"><div class="ratingbubble"></div><div class="ratingbubble"></div><div class="ratingbubble"></div><div class="ratingbubble"></div></div><br><div id="description">Description</div><div id="recommender"><span id="reviewer-name">Name</span> say'+"'"+'s ...</div><div id="review-content">Review</div><a id="ib" href="https://www.tripadvisor.com/HotelBookingRoomSelectionHtml?a=2&ik=d7c60d1b133246f79775ace1763eaae5&d=113317&ci_day=22&ep=437&co_day=23&r=1&ci_month=9&co_month=9&co_year=2015&src_0=63261472&tp=Hotels_MainList&ci_year=2015&cpn=IB_SabreHospitalitySolu_1Fv9i6k"><div id="instantbook">Book Now &nbsp;></div></a>'

  $("#infobox").html(newCode);
}

function locationDataFormatBox(original) {
  var width1 = "20%";
  var height1 = "400px";
  if (original) {
      width1 = "350px";
      height1 = "500px";
  }
  $("#infobox").animate({
	width: width1, height: height1}, 300);
}

function locationDataInfoBox(){
//resets it for locationData
  newCode = '<div id="weather-picture">pic</div><br>Weather at<div class="weather-city">Selected Marker</div><br><div id="weather-temp">Temperature</div><div id="weather-name">Sunny</div>';
  $("#infobox").html(newCode);
}

function locationDisplayText(){
  $("#infobox").html("<h4>Selecting an icon on the map will display more information here</h4>");
}

// Sets the map on all markers in the array.
function setAllMap(map) {
  for (var i = 0; i < markers.length; i++) {
    markers[i].setMap(map);
  }
}

// Shows any markers currently in the array.
function showMarkers() {
  setAllMap(map);
}
