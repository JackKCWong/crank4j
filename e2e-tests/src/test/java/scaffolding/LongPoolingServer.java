package scaffolding;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

public class LongPoolingServer {
	public static void main(String[] args) throws Exception {
		Server server = new Server();
		ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());
		connector.setPort(8080);
		server.addConnector(connector);
		server.setHandler(new AbstractHandler() {
			@Override
			public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
				System.out.println("request coming in...");
				for (; ; ) {
					System.out.println("Sending");
					httpServletResponse.getWriter().append(new Date().toString()).flush();
				}
			}
		});

		server.start();
	}
}
