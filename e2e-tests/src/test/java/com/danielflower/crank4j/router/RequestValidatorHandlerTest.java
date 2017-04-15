package com.danielflower.crank4j.router;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;

public class RequestValidatorHandlerTest {
	RequestValidatorHandler handler = new RequestValidatorHandler();

	@Test
	public void hackyURLsAreRejected() throws IOException, ServletException {
		String badUrl = "/proplus/admin/login.php+-d+\\action=insert\\+-d+\\username=test\\+-d+\\password=test\\";
		HttpServletResponse response = createResponse();
		handler.handle("/blah", createRequest(badUrl), null, response);
		Assert.assertThat(response.getStatus(), is(400));
		Assert.assertThat(((Response) response).getReason(), startsWith("Invalid request. Error ID "));
	}

	private Request createRequest(String request) {
		return new Request(null, null) {
			@Override
			public String getRequestURI() {
				return request;
			}
		};
	}

	private Response createResponse() {
		return new Response(null, null) {
			private int __status;
			private String __message;
			@Override
			public void sendError(int code, String message) throws IOException {
				__status = code;
				__message = message;
			}

			@Override
			public int getStatus(){
				return __status;
			}

			@Override
			public String getReason()
			{
				return __message;
			}
		};
	}

}