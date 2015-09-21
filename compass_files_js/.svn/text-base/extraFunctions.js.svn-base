// takes in an integer and populates the "rating" field with that number of stars
function ratingStars(number) {
	if (number > 5) {
		number = 5;
	} else if (number <=0 ) {
		number = 0
	}
	var totalStars = Math.round(number);
	var star = '<div class="ratingbubble"></div>';
	var allStars = Array(totalStars+1).join(star);
	$("#rating").html(allStars);
}
