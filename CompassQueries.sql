//Will change limit.  10 just for testing
SELECT_SOME_T_MEMBER_IDS_RECENT_FACEBOOK_LOGINS
{
    select t_member.memberid from t_external_member join t_member on t_external_member.memberid = t_member.memberid
    where idtype='FB'
    and lastchange > '2015-01-01'
    limit 10;
}

SELECT_SOME_T_MEMBER_IDS_RECENT_FACEBOOK_LOGINS_PLACE_NOT_NULL
{
    select t_member.memberid, t_member.userid, t_member.locationid from t_external_member join t_member on t_external_member.memberid = t_member.memberid
    where idtype='FB'
    and t_member.locationid is not null
    and t_member.locationid<>0
    and lastchange > '2015-01-01'
    limit 50;
}

SELECT_INFO_BASED_ON_FACEBOOK_ID
{
    select t_member.memberid, t_member.userid, t_member.locationid from t_external_member join t_member on t_external_member.memberid = t_member.memberid
    where t_external_member.externalid = a_facebook_id
    and idtype='FB';
}

SELECT_LOCATIONID_FROM_RESERVATION
{
    select locationid from vw_tripconnect_bookings_latest
    where reservation_id = a_reservation_id;
}

SELECT_LOCATIONIDS_FROM_USERID
{
    select distinct location_id from t_booking_reservation
    where user_id = a_user_id;
}

SELECT_ALL_RESERVATIONS_USERID_LOCATION_ID
{
    select distinct user_id, location_id from t_booking_reservation order by user_id;
}

SELECT_MEMBER_ID_AND_LOCATION_FROM_USER_ID_T_MEMBER
{
    select memberid, locationid from t_member where userid = a_userid;
}
