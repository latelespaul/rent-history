package com.rentrix.rentrixserver.common.error;

import com.rentrix.rentrixserver.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/** Writes an {@link ApiProblem} straight to the servlet response (used outside Spring MVC, e.g. security). */
@Component
@RequiredArgsConstructor
public class ProblemWriter {
	
	private final ObjectMapper objectMapper;
	
	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
		String detail) throws IOException {
		ApiProblem problem = ApiProblem.of(status, code, detail, request.getRequestURI(), List.of(),
			RequestIdFilter.currentRequestId());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), problem);
	}
	
}
