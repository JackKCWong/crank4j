package scaffolding;


import com.danielflower.crank4j.connector.ClientFactory;
import com.danielflower.crank4j.utils.CancelledException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;

public class LongPoolingClient {
	public static void main(String[] args) throws Exception {
		HttpClient client = ClientFactory.startedHttpClient();
		client.start();
		Request requestToTarget = client.newRequest("https://localhost:9443").method("GET");
		requestToTarget.send(result -> {
			if (result.isSucceeded()) {
				System.out.println("succeed");
				try {
					main(null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				if (!(result.getFailure() instanceof CancelledException)) {
					System.out.println("Failed for " + result.getResponse());
					System.out.println("Failed for " + result.getFailure());
					requestToTarget.abort(new CancelledException("Socket to Router closed"));
				}
			}
		});
	}
}
