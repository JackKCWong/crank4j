package scaffolding;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class BadRequestRunner {
	private static String LOCAL = "https://localhost:9443";

	static {
		//for localhost testing only
		javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
				new javax.net.ssl.HostnameVerifier(){

					public boolean verify(String hostname,
					                      javax.net.ssl.SSLSession sslSession) {
						if (hostname.equals("localhost")) {
							return true;
						}
						return false;
					}
				});
	}


	public static void main(String[] args) throws Exception {
		SSLUtilities.trustAllHostnames();
		SSLUtilities.trustAllHttpsCertificates();

//		String sampleRequest = "/forum_arc.asp?n=/../../../../../../../../etc/passwd|36|80040e14|[Microsoft]";
//		int responseCode = newRequest(HK_SIT + sampleRequest);
//		System.out.println(responseCode + " " + sampleRequest);
		makeBatchRequests();

	}

	private static void makeBatchRequests() throws IOException {
		List<String> lines = Files.readLines(new File(BadRequestRunner.class.getResource("/bad_request_test_file.txt").getFile()), Charsets.UTF_8);
		lines.forEach(line -> {
			if (line.startsWith("#") || line.trim().isEmpty()) {
				return;
			}
			String request = line.split(",")[1].replaceAll("\"", "");

			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						int responseCode = newRequest(LOCAL + request);
						System.out.println(responseCode + " " + request);
					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
				}
			}).start();
		});
	}

	public static int newRequest(String request) throws Exception{
		SSLUtilities.trustAllHostnames();
		SSLUtilities.trustAllHttpsCertificates();
		URL url = new URL(request);
		HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
		return con.getResponseCode();
	}
}
