package controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

/**
 * Servlet implementation class Service
 */
@WebServlet("/Service")
public class Controller extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private String sqlUser = "";
	private String sqlPassword = "";
	Connection conn;
	private final byte[] keyValue = "".getBytes();

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public Controller() {
		super();
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		System.out.println("in services get");
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			System.out.println("in services Post");
			// open mysql connection
			openConnection();

			if (request.getParameter("user") != null && request.getParameter("data") != null) {
				newData(request);
			} else if (request.getParameter("getGPS") != null && request.getParameter("name") != null) {
				getGPSdata(request, response);
			} else if (request.getParameter("getHome") != null) {
				getHomeLocation(request, response);
			} else if (request.getParameter("getNotifications") != null) {
				getNotificationsData(request, response);
			}
			close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Requires: request, response
	// Returns: gets last 10 notification encrypts and returns them
	private void getNotificationsData(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String name = sanatiseInput(request.getParameter("getNotifications"), 200);
		PreparedStatement createStatement = null;
		PreparedStatement userStatement = null;
		ResultSet rs = null;
		// fetch gps from database
		try {
			createStatement = conn.prepareStatement(
					"select * from gpsapp.notifications ORDER BY notifcations.`TimeStamp` DESC LIMIT 10;");
			createStatement.setString(1, name);
			userStatement = conn.prepareStatement("select 1 from gpsapp.registeredgpsusers where registeredgpsusers = ?;");
			userStatement.setString(1, name);
			
			rs = userStatement.executeQuery();
			String res = "";
			while (rs.next()) {
				if(!rs.getString(1).equals("1"))return; //invalid user break

			}
			rs.close();
			rs = null;
			rs = createStatement.executeQuery();
			while (rs.next()) {
				String input = rs.getString("TimeStamp");
				res = res + rs.getString("TimeStamp")+","+ rs.getString("Device")+":";
			}
			response.getOutputStream().println(encrypt(res.substring(0, res.length() - 1))); //remove last comma

		} catch (SQLException e) {
			System.out.println(e.getClass().getName() + " " + e.getMessage());
		} finally {
			try {
				rs.close();
			} catch (Exception e) {
				/* Ignored */}
			try {
				createStatement.close();
			} catch (Exception e) {
				/* Ignored */}
		}
	}
	// Requires: request, response
	// Returns: gets homelocation, encrypts and returns it
	private void getHomeLocation(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String name = sanatiseInput(request.getParameter("getHome"), 200);
		PreparedStatement createStatement = null;
		ResultSet rs = null;
		// fetch gps from database
		try {
			createStatement = conn.prepareStatement(
					"select Homelocation from gpsapp.registeredgpsusers where registeredgpsusers.Username = ?;");
			createStatement.setString(1, name);
			rs = createStatement.executeQuery();
			String res = "";
			while (rs.next()) {
				String input = rs.getString("gpscords");
				String gps = decrypt(input);
				res = res + gps;

			}
			response.getOutputStream().println(encrypt(res));

		} catch (SQLException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		} finally {
			try {
				rs.close();
			} catch (Exception e) {
				/* Ignored */}
			try {
				createStatement.close();
			} catch (Exception e) {
				/* Ignored */}
		}
	}
	// Requires: request, response
	// Returns: gets gps data and returns it encrypted
	private void getGPSdata(HttpServletRequest request, HttpServletResponse response) throws IOException {
		System.out.println("in get GPS");

		String user = sanatiseInput((String) request.getParameter("getGPS"), 5000);
		String name = sanatiseInput((String) request.getParameter("name"), 5000);

		PreparedStatement createStatement = null;
		ResultSet rs = null;
		// fetch gps from database
		try {
			createStatement = conn.prepareStatement(
					"select * from `gpsapp`.`gpsdata` where user = ? and name = ? ORDER BY stamp DESC LIMIT 1");
			createStatement.setString(1, user);
			createStatement.setString(2, name);
			rs = createStatement.executeQuery();
			String res = "";
			while (rs.next()) {
				String input = rs.getString("gpscords");
				String gps = decrypt(input);
				res = res + gps;

			}
			response.getOutputStream().println(encrypt(res));

		} catch (SQLException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		} finally {
			try {
				rs.close();
			} catch (Exception e) {
				/* Ignored */}
			try {
				createStatement.close();
			} catch (Exception e) {
				/* Ignored */}
		}
	}
	
	// Requires: request, response
	// Returns: inserts new gps data into db
	private void newData(HttpServletRequest request) {
		System.out.println("in new GPS");
		PreparedStatement createUserStatement = null;

		// get data
		String user = request.getParameter("user");
		String name = request.getParameter("name");
		String data = request.getParameter("data").replaceAll(" ", "+");
		GpsModel mod = new GpsModel(user, data);

		// generate statement & execute
		try {
			createUserStatement = conn.prepareStatement(
					"INSERT INTO `gpsapp`.`gpsdata` (`user`,`name`,`stamp`,`gpscords`) VALUES (?,?,NOW(),?);");
			createUserStatement.setString(1, mod.userName);
			createUserStatement.setString(2, name);
			createUserStatement.setString(3, mod.getGpsCords());
			createUserStatement.executeUpdate();

		} catch (SQLException ex) {
			try {
				createUserStatement.close();
			} catch (Exception e1) {
				ex.printStackTrace();
				/* ignored */
			} finally {
				try {
					createUserStatement.close();
				} catch (Exception ex2) {
					ex2.printStackTrace();
				}
			}
		}
	}

	// Requires: Takes a string and a input limit
	// Returns: a cleaned string with max length
	private String sanatiseInput(String l, int inputLimit) {
		l = Jsoup.clean(l, Whitelist.none());
		return l;
	}

	private void openConnection() {
		try {
			// Set up connection
			Class.forName("com.mysql.jdbc.Driver");
			conn = DriverManager.getConnection("jdbc:Mysql://localhost:3306", sqlUser, sqlPassword);
		} catch (Exception e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
	}

	// Requires:
	// Returns: (Effect) closes the connection such to not waist resources on an
	// open connection.
	private void close() {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				/* ignored */
			}
		}
	}

	// Requires:
	// Returns: generates the AES key
	private Key generateKey() {
		Key key = new SecretKeySpec(keyValue, "AES");
		return key;
	}

	// Requires: A plaintext string
	// Returns: A encryoted cipher text version of string
	public String encrypt(String plainText) {
		try {
			Cipher AesCipher = Cipher.getInstance("AES");
			AesCipher.init(Cipher.ENCRYPT_MODE, generateKey());

			return Base64.getEncoder().withoutPadding().encodeToString((AesCipher.doFinal(plainText.getBytes())));
		} catch (Exception e) {

		}
		return null;
	}

	// Requires: A cipher text string
	// Returns: The decrypted plain text of text string
	public String decrypt(String cipherText) {
		try {
			Cipher AesCipher;
			AesCipher = Cipher.getInstance("AES");
			AesCipher.init(Cipher.DECRYPT_MODE, generateKey());
			return new String(AesCipher.doFinal(Base64.getDecoder().decode(cipherText.getBytes())));
		} catch (Exception e) {

		}
		return null;
	}

}
