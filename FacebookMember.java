package com.TripResearch.membership;

import java.util.Random;
import java.lang.Math;
import java.util.List;
import java.util.ArrayList;
import com.TripResearch.db.DBLocationElement;

public class FacebookMember {
    public enum Gender {
		    MALE, FEMALE;

        public String toString() {
            switch(this) {
                case MALE: return "M";
                case FEMALE: return "F";
                default: throw new IllegalArgumentException();
            }
        }
    }

	private int memberId;
  private String userId;
	private int age;
	private Gender gender;
	private int hometown;
  private List<DBLocationElement> locationsTraveled;

  public FacebookMember(int memId, String username, int locationId)
  {
      memberId = memId;
      userId = username;
      hometown = locationId;
  }

  public FacebookMember(int memId, String username, int numAge, Gender genderVal, int locationId)
  {
      memberId = memId;
      userId = username;
      age = numAge;
      gender = genderVal;
      hometown = locationId;
  }

	/**
	 * setter methods
	 */
	public void setMemberId(int memId)
	{
		  memberId = memId;
	}

  public void setUserId(String userid)
  {
      userId = userid;
  }

	public void setAge(int numAge)
	{
		  age = numAge;
	}

	public void setGender(Gender genderVal)
	{
		  gender = genderVal;
	}

	public void setHometown(int hometownId)
	{
		  hometown = hometownId;
	}

  public void setLocations(List<DBLocationElement> locationids)
  {
      locationsTraveled = locationids;
  }

	/**
	 * getter methods
	 */
	public int getMemberId()
	{
		  return memberId;
	}

  public String getUserId()
  {
      return userId;
  }

	public int getAge()
	{
		  return age;
	}

	public Gender getGender()
	{
		  return gender;
	}

	public int getHometown()
	{
		  return hometown;
	}

  public List<DBLocationElement> getLocations()
  {
      return locationsTraveled;
  }

  public void assignRandomAge()
  {
      int randomAge = 0;
      double proportion = Math.random();
      if (proportion < .30 )
      {
          randomAge = randInt(20, 25);
      }
      else if (proportion < .50)
      {
          randomAge = randInt(26, 30);
      }
      else if (proportion < .8)
      {
          randomAge = randInt(31, 40);
      }
      else {
          randomAge = randInt(41, 50);
      }
      this.setAge(randomAge);
  }

  public void assignRandomGender()
  {
      double proportion = Math.random();
      if (proportion < .5)
      {
          this.setGender(Gender.MALE);
      }
      else {
          this.setGender(Gender.FEMALE);
      }
  }

  private int randInt(int low, int high)
  {
       Random rand = new Random();
       int randNum = rand.nextInt((high - low) + 1) + low;
       return randNum;
  }


	@Override
	public String toString() {
		return "[memberId: " + memberId + ", userid: " + userId + ", age: " + age +
				", gender: " + gender + ", hometown: " + hometown + ", locations: " + locationsTraveled + "]";
	}

}
