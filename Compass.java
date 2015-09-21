package com.TripResearch.servlet;

import com.TripResearch.membership.IMember;
import com.TripResearch.membership.MemberFactory;
import com.TripResearch.modules.data.parameters.common.RequestMemberParameter;
import com.TripResearch.object.GlobalContext;
import com.TripResearch.servlet.BaseServlet;
import com.TripResearch.servlet.CustomServletRequest;
import com.TripResearch.servlet.ServletName;
import com.TripResearch.servlet.ServletUtils;
import com.TripResearch.servlet.VelocityServlet;
import com.TripResearch.servlet.membership.MemberPage;
import com.TripResearch.servlet.registration.RegistrationPage;
import com.TripResearch.util.Logging;
import com.TripResearch.util.UrlArg;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Comparator;

import com.TripResearch.object.social.FacebookAPIKey;
import com.TripResearch.object.facebook.LocatedFriendSet;
import com.TripResearch.service.client.ServiceClientFactory;
import com.TripResearch.facebook.IFacebookConnectSvc;
import com.TripResearch.facebook.FacebookConnectOptions;
import com.TripResearch.object.facebook.FacebookCredential;
import static com.TripResearch.object.partial.data.DataFactory.FRIENDS_VISITED_WITH_MEMBER_PAGED;
import com.TripResearch.queries.CompassQueries;
import java.sql.Connection;
import com.TripResearch.pooling.SinglePool;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.TripResearch.membership.FacebookMember;
import com.TripResearch.content.CContext;
import com.TripResearch.content.UserReviewComparator;

import com.TripResearch.object.PlaceTypes;
import com.TripResearch.booking.BookingClient;
import com.TripResearch.booking.Reservation;
import com.TripResearch.servlet.eatery.RestaurantPriceRange;
import com.TripResearch.servlet.accommodation.HotelPriceRange;
import com.tripadvisor.booking.adapterapi.interfaces.ReservationId;
import com.TripResearch.service.ServiceException;
import com.TripResearch.util.RandomFacebookIdGenerator;
import com.TripResearch.util.StringUtils;
import com.TripResearch.db.DBLocationElement;
import com.TripResearch.db.DBLocationElement.DistanceComparator;
import com.TripResearch.db.DBLocationStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.TripResearch.object.PlaceTypes;
import com.TripResearch.userreview.UserReview;
import com.TripResearch.userreview.UserReviewClient;

public class Compass extends VelocityServlet
{
    private static Gson gson = new Gson();
    public static int NYC = 60763;
    public static int ATL = 60898;
    public static int LA = 32655;
    public static int SF = 60713;
    public static int CHICAGO = 35805;
    public static int BOSTON = 60745;
    public static List<Integer> CITIES = Arrays.asList(new Integer[]{NYC, ATL, SF, CHICAGO, BOSTON});

    private class LocationObj
    {
        private int locationid = 0;
        private String name = null;
        private String parentName = null;
        private String postalCode = null;
        private boolean isAttraction = false;
        private boolean isNightlife = false;
        private boolean isShopping = false;
        private String placeType = null;
        private String description = null;
        private String address = null;
        private int popularity = 0;
        private String priceRating = null;
        private List<ReviewObj> reviewList = null;
        private float longitude = 0;
        private float latitude = 0;
        public LocationObj(DBLocationElement location)
        {
            postalCode = location.getPostalCode();
            parentName = location.getParentName();
            isAttraction = location.isAttractionCategory();
            isNightlife = location.isNightlifeCategory();
            isShopping = location.isShoppingCategory();
            name = location.getName();
            placeType = PlaceTypes.toString(location.getPlaceType());
            description = location.getDescription();
            address = location.getPlainTextAddress();
            popularity = location.getPopularity();
            locationid = location.getId();
            Logging.SERVLET.info("placeType~!", placeType, placeType.equals("Accommodation"));
            if(placeType.equals("Eatery"))
            {
                priceRating = RestaurantPriceRange.getPriceRating(location);
            }
            else if (placeType.equals("Accommodation"))
            {
                priceRating = HotelPriceRange.getPriceRating(location);
            }

            List<UserReview> userReviewList = UserReviewClient.getReviewsForLocation(location.getId(), false, true);
            reviewList = new ArrayList<>();
            Random rand = new Random();
            if(userReviewList.size() > 0)
            {
                int randReviewIndex = rand.nextInt(userReviewList.size());
                UserReview randomReview = userReviewList.get(randReviewIndex);
                reviewList.add(new ReviewObj(randomReview));
            }

            if (location.getGeoCenter() != null)
            {
                longitude = location.getGeoCenter().getLongitude();
                latitude = location.getGeoCenter().getLatitude();
            }
        }
    }
    private class ReviewObj
    {
        String text = null;
        String author = null;
        int rating = 3;

        public ReviewObj(UserReview review)
        {
            text = review.getTruncatedText(200);
            author = review.getUsername();
            rating = review.getRating();
        }
    }
    List<FacebookMember> friendList = null;
    List<FacebookMember> someMemberList = null;
    List<FacebookMember> reservationList = null;
    Map<String, Integer> map = null;
    public Compass()
    {
      super();
      try {
          friendList = getMemberListFromFacebookIds(RandomFacebookIdGenerator.getRandomFacebookUsers(5));
          // randomly fill them with missing data
          populateAgeAndGender(friendList);
          // randomly assign places they have been to
          populateLocations(friendList, getAllLocationsInCities(CITIES));
          someMemberList = getAllRecentFacebookUsers();
          populateAgeAndGender(someMemberList);
          populateLocations(someMemberList, getAllLocationsInCities(CITIES));

          reservationList = convertMapToMembersList(getAllCurrentReservationUsersAndLocations());
          populateAgeAndGender(reservationList);

          map = getDistinctHometownsOfMembers(reservationList);
      }
      catch(SQLException e)
      {
          Logging.SERVLET.info("error when trying to get reservationList", e);
      }
    }

    @Override
    public boolean generateVelocity(CustomServletRequest req,
            HttpServletResponse res) throws Exception
    {
        String action = getParamString(req, "action", "");

        if ("getSuggestions".equals(action))
        {
            // this is a Ajax call
            setVelocityTemplate("common/json.vm");
            setJsonContentType(res);

            // extract filters
            //String type = getParamString(req, "type", "attractions");

            int ageHigh = getParamInt(req, "ageHigh", 0);
            int ageLow = getParamInt(req, "ageLow", 0);
            Logging.SERVLET.info("AGERANGE:", ageLow, ageHigh);
            boolean friendOnly = getParamBool(req, "friendOnly", false);
            boolean reservationOnly = getParamBool(req, "reservationOnly", false);
            String types = getParamString(req,"types", "");
            List<String> locTypes = Arrays.asList(types.split(","));

            String hometown = getParamString(req,"hometown", "");
            int hometownId = 0;
            if(hometown != "") {
                hometownId = map.get(hometown);
            }
            List<FacebookMember> memberList;
            if(friendOnly)
            {
                memberList = friendList;
            }
            else if(reservationOnly)
            {
                memberList = reservationList;
            }
            else
            {
                // generate facebook users
                memberList = someMemberList;
            }

            memberList = filterByParams(memberList, ageLow, ageHigh, locTypes, hometownId);

            // collect all the places they have been to
            List<LocationObj> locationList = new ArrayList<>();
            for (FacebookMember member: memberList)
            {
                List<DBLocationElement> locations = member.getLocations();
                for (DBLocationElement location: locations)
                {
                    locationList.add(new LocationObj(location));
                }
            }
            addObject("json", gson.toJson(locationList));
        }
        else if ("getHometownList".equals(action))
        {
            setVelocityTemplate("common/json.vm");
            setJsonContentType(res);

            addObject("json", gson.toJson(map.keySet()));
        }
        else
        {
            // friendList = getMemberListFromFacebookIds(RandomFacebookIdGenerator.getRandomFacebookUsers(5));
            // populateAgeAndGender(friendList);
            // populateLocations(friendList, getAllLocationsInCities(CITIES));
            //
            // someMemberList = getAllRecentFacebookUsers();
            // populateAgeAndGender(someMemberList);
            // populateLocations(someMemberList, getAllLocationsInCities(CITIES));
            //
            // reservationList = convertMapToMembersList(getAllCurrentReservationUsersAndLocations());
            // populateAgeAndGender(reservationList);

            setVelocityTemplate("t4b/compass.vm");
            try
            {
                FacebookCredential credentials = FacebookAPIKey.summonCredentialsFromDeep(req);
                IFacebookConnectSvc fbServer = (IFacebookConnectSvc)ServiceClientFactory.getInstance().getClient("FacebookConnectService");
                LocatedFriendSet resultset = fbServer.getLocatedFriends(credentials, new FacebookConnectOptions());
                addObject("friends", resultset);
            }
            catch (Exception e)
            {
                Logging.TRIPCONNECT.info("Facebook retrival Failed");
            }
        }
        // addObject("cityFrequencies", getCityFrequencies(members));
        // addObject("cityFrequencies", getCityFrequencies(members));
        // addObject("locationFrequencies", getLocationFrequencies(members));

        return true;
    }


    @Override
    public String getServletInfo()
    {
        return ServletName.SERVLET_COMPASS;
    }

    @Override
    protected String checkForRedirect ( HttpServletRequest request, HttpServletResponse response )
    {
        IMember viewedMember = getMemberFromRequest ( request );
        if ( viewedMember == null )
        {
            String redirectURL = RegistrationPage.redirectURL(ServletUtils.getURL(request));
            Logging.SERVLET.info ( "Attempting to redirect out of member center for null member: ", redirectURL );
            return redirectURL;
        }
        else if ( !MemberPage.TRAVELMAP.isAccessible (viewedMember) )
        {
            return "/"; // Redirect to main profile page when not available.
        }
        GlobalContext.getInstance().getRequest().setAttribute ( RequestMemberParameter.REQUEST_MEMBER, viewedMember.getMemberID() );
        return null;
    }


    private IMember getMemberFromRequest ( HttpServletRequest req )
    {
        String uid = getParamString ( req, UrlArg.USER_ID, null );
        if ( uid == null )
        {
            // Change to public view only during read-only mode
            addObject ( "isViewingLoggedInMember", !BaseServlet.isWritesDisabled() );
            return MemberFactory.getLoggedInMember ();
        }
        try
        {
            IMember mem = MemberFactory.findMemberByID ( uid, true, true );
            addObject ( "isViewingLoggedInMember", uid.equals ( MemberFactory.getLoggedInMemberID () ) && !BaseServlet.isWritesDisabled() );
            return mem;
        }
        catch ( Exception e )
        {
            Logging.SERVLET.error ( "Unable to find member for UID: ", uid, e );
            return null;
        }
    }

    public static List<FacebookMember> filterByAge(List<FacebookMember> data, int age)
    {
        return data.stream().filter(m -> m.getAge() == age).collect(Collectors.toList());
    }

    public static List<FacebookMember> filterByAgeRange(List<FacebookMember> data, int ageLow, int ageHigh)
    {
        return data.stream().filter(m -> m.getAge() > ageLow && m.getAge() < ageHigh).collect(Collectors.toList());
    }

    /** Creates a new list where for each member, the location list only contains ones of the location types in locTypes
     *  Note that each String locType in locTypes is the same as PlaceTypes.toString()
     */
    public static List<FacebookMember> filterByLocType(List<FacebookMember> data, List<String> locTypes)
    {
        List<Integer> locTypeTags = locTypes.stream().map(l -> PlaceTypes.toTag(l)).collect(Collectors.toList());
        List<FacebookMember> filteredData = new ArrayList<FacebookMember>();
        for(FacebookMember member : data)
        {
            FacebookMember memberUpdated = new FacebookMember(member.getMemberId(), member.getUserId(),
                member.getAge(), member.getGender(), member.getHometown());
            List<DBLocationElement> visitedLocations = new ArrayList<>();
            for(DBLocationElement loc : member.getLocations())
            {
                if(locTypeTags.contains(loc.getPlaceType()))
                {
                    visitedLocations.add(loc);
                }

            }
            memberUpdated.setLocations(visitedLocations);
            filteredData.add(memberUpdated);
        }
        return filteredData;
    }

    //reviewList = UserReviewClient.getReviewsForLocation(location.getId(), true, true);

    /**
     *  If params are entered, filters by that param.
     */
    public static List<FacebookMember> filterByParams(List<FacebookMember> data, int ageLow, int ageHigh, List<String> locTypes, int hometown)
    {
        List<FacebookMember> filteredList = new ArrayList<FacebookMember>(data);
        if(ageLow != 0 && ageHigh != 0)
        {
            filteredList = filterByAgeRange(filteredList, ageLow, ageHigh);
        }
        boolean isTypeEmpty = locTypes.isEmpty()||(locTypes.size()==1&& locTypes.get(0).isEmpty());
        if(!isTypeEmpty)
        {
            filteredList = filterByLocType(filteredList, locTypes);
        }
        if(hometown != 0)
        {
            filteredList = filteredList.stream().filter(m -> m.getHometown() == hometown).collect(Collectors.toList());
        }
        return filteredList;
    }


    public static FacebookMember generateMemberFromFacebookId(String id) throws SQLException
    {
        FacebookMember member = null;
        ResultSet rs = null;
        try(Connection c = SinglePool.requireConnection("tripmonster");
            PreparedStatement stmt = c.prepareStatement(CompassQueries.SELECT_INFO_BASED_ON_FACEBOOK_ID);)
        {
            stmt.setString(CompassQueries.SELECT_INFO_BASED_ON_FACEBOOK_ID_FACEBOOK_ID, id);
            rs = stmt.executeQuery();
            rs.next();
            member = new FacebookMember(rs.getInt("memberid"), rs.getString("userid"), rs.getInt("locationid"));
        }
        catch(SQLException e)
        {
            throw new SQLException("Error getting member info from facebook id ", e);
        }
        return member;
    }

    public static List<FacebookMember> getMemberListFromFacebookIds(List<String> ids) throws SQLException
    {
        List<FacebookMember> memberList = new ArrayList<FacebookMember>();
        for (String id : ids)
        {
            memberList.add(generateMemberFromFacebookId(id));
        }
        return memberList;
    }


    public List<FacebookMember> getAllRecentFacebookUsers() throws SQLException
    {
        List<FacebookMember> members = new ArrayList<>();
        ResultSet rs = null;
        try(Connection c = SinglePool.requireConnection("tripmonster");
            PreparedStatement stmt = c.prepareStatement(CompassQueries.SELECT_SOME_T_MEMBER_IDS_RECENT_FACEBOOK_LOGINS_PLACE_NOT_NULL);)
        {
            rs = stmt.executeQuery();
            while(rs.next())
            {
                FacebookMember member = new FacebookMember(rs.getInt("memberid"), rs.getString("userid"), rs.getInt("locationid"));
                members.add(member);
            }
        }
        catch (SQLException e)
        {
            throw new SQLException("Error retrieving all recent facebook users", e);
        }
        return members;
    }

    public void populateAgeAndGender(List<FacebookMember> members )
    {
        for(FacebookMember member : members)
        {
             member.assignRandomAge();
             member.assignRandomGender();
        }
    }

    public void populateLocations(List<FacebookMember> members, List<DBLocationElement> randomLocations)
    {
        Random rand = new Random();
        int numPossibleLocations = randomLocations.size();

        for(FacebookMember member : members)
        {
            int randIndex = 0;
            DBLocationElement randomLoc = null;
            List<DBLocationElement> eachLocationList = new ArrayList<>();
            for(int i=0; i<10; i++)
            {
                randIndex = rand.nextInt(numPossibleLocations);
                randomLoc = randomLocations.get(randIndex);
                eachLocationList.add(randomLoc);
            }
            member.setLocations(eachLocationList);

        }
    }

    public List<DBLocationElement> getAllLocationsInCities(List<Integer> cities)
    {
        DBLocationStore dbstore = DBLocationStore.getInstance();
        List<DBLocationElement> randomLocations = new ArrayList<>();
        for (int locId : cities)
        {
            DBLocationElement loc = getLocationElement(locId);
            Iterable<DBLocationElement> subList = dbstore.immediateChildren(loc);
            for(DBLocationElement elm : subList)
            {
                randomLocations.add(elm);
            }
        }
        return randomLocations;
    }

    /** Maps the Location Element to its appearing frequency
     */
    public Map<DBLocationElement, Integer> getLocationFrequencies(List<FacebookMember> members)
    {
        Map<DBLocationElement, Integer> frequencyMap = new HashMap<>();
        //Iterate through each member, and then iterate through member's location List
        for (FacebookMember member : members)
        {
            for(DBLocationElement loc : member.getLocations())
            {
                if(!frequencyMap.containsKey(loc))
                {
                    frequencyMap.put(loc, 1);
                }
                else
                { //has location as a key already
                    int currentCount = frequencyMap.get(loc);
                    frequencyMap.put(loc, currentCount+1);
                }
            }
        }
        return frequencyMap;
    }


    /** Maps the Geo IDs to its appearing frequency (frequency counts for cities)
     */
    public Map<Integer, Integer> getCityFrequencies(List<FacebookMember> members)
    {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        //Iterate through each member, and then iterate through member's location List
        for (FacebookMember member : members)
        {
            for(DBLocationElement loc : member.getLocations())
            {
                int cityId = loc.getParentId();
                if (!frequencyMap.containsKey(cityId))
                {
                    frequencyMap.put(cityId, 1);
                }
                else
                {
                    int currentCount = frequencyMap.get(cityId);
                    frequencyMap.put(cityId, currentCount+1);
                }
            }

        }
        return frequencyMap;
    }

    public Map<String, List<Integer>> getAllCurrentReservationUsersAndLocations() throws SQLException
    {
        ResultSet rs = null;
        Map<String, List<Integer>> userToLocations = new HashMap<>();
        try(Connection c = SinglePool.getBookingSessionConnection();
            PreparedStatement stmt = c.prepareStatement(CompassQueries.SELECT_ALL_RESERVATIONS_USERID_LOCATION_ID);)
        {
            rs = stmt.executeQuery();
            while(rs.next())
            {
                String userId = rs.getString("user_id");
                int loc = rs.getInt("location_id");
                if(userToLocations.containsKey(userId))
                {
                    //If the map already has the key, get the current location list and append to it
                    List<Integer> locations = userToLocations.get(userId);
                    locations.add(loc);
                    userToLocations.put(userId, locations);
                } else
                {
                    List<Integer> locations = new ArrayList(Arrays.asList(new Integer[]{loc}));
                    userToLocations.put(userId, locations);
                }
            }
            return userToLocations;
        }
    }

    public static List<FacebookMember> convertMapToMembersList(Map<String, List<Integer>> userToLocs) throws SQLException
    {
        List<FacebookMember> members = new ArrayList<>();
        for(String user : userToLocs.keySet())
        {
            ResultSet rs = null;
            Random rand = new Random();
            int index = rand.nextInt(CITIES.size());
            int hometown = CITIES.get(index);
            int memId = 0;
            try(Connection c = SinglePool.requireConnection("tripmonster");
                PreparedStatement stmt = c.prepareStatement(CompassQueries.SELECT_MEMBER_ID_AND_LOCATION_FROM_USER_ID_T_MEMBER);)
            {
                stmt.setString(CompassQueries.SELECT_MEMBER_ID_AND_LOCATION_FROM_USER_ID_T_MEMBER_USERID, user);
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    memId = rs.getInt("memberid");
                    if(rs.getInt("locationid") != 0)
                    {
                        hometown = rs.getInt("locationid");
                    }
                }
            }
            FacebookMember member = new FacebookMember(memId, user, hometown);

            List<DBLocationElement> locElements = userToLocs.get(user).stream().map(l -> getLocationElement(l)).collect(Collectors.toList());
            member.setLocations(locElements);
            members.add(member);
        }
        return members;
    }

    /**returns a map of the location's name to its location id */
    public static Map<String, Integer> getDistinctHometownsOfMembers(List<FacebookMember> members)
    {
        Map<String, Integer> hometownMap = new HashMap<>();
        for(FacebookMember member : members)
        {
            int locationId = member.getHometown();
            DBLocationElement locElem = getLocationElement(locationId);
            hometownMap.put(locElem.getName(), locationId);
        }
        return hometownMap;
    }

}
