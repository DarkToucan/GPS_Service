package controller;
public class GpsModel implements java.io.Serializable {
	private static final long serialVersionUID = 1;
	public String gpsCords;
	public String userName;
	
	public GpsModel( String userName, String gpsCords){
		this.gpsCords = gpsCords;
		this.userName = userName;
	}

	public String getGpsCords() {
		return gpsCords;
	}

	public void setGpsCords(String gpsCords) {
		this.gpsCords = gpsCords;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}
}
