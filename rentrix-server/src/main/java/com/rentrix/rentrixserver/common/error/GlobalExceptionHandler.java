package com.rentrix.rentrixserver.common.error;

import com.rentrix.rentrixserver.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns every exception into {@code application/problem+json} with a stable {@code code}
 * (D-60). Spring MVC's own exceptions go through {@link ResponseEntityExceptionHandler} and
 * get a default code per status; validation errors become {@code 422 validation_failed}
 * with one entry per field.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
	
	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
		return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getErrors(), request.getRequestURI(),
			ex.getHeaders());
	}
	
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
		List<ApiProblem.FieldError> errors = ex.getConstraintViolations().stream()
			.map(v -> new ApiProblem.FieldError(lastNode(v.getPropertyPath().toString()), "invalid", v.getMessage()))
			.toList();
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation_failed", "The request has invalid fields.", errors,
			request.getRequestURI());
	}
	
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "malformed_request", "Parameter '" + ex.getName() + "' has an invalid value.",
			List.of(), request.getRequestURI());
	}
	
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return problem(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to do this.", List.of(),
			request.getRequestURI());
	}
	
	@ExceptionHandler({AuthenticationCredentialsNotFoundException.class, AuthenticationException.class})
	ResponseEntity<Object> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.", List.of(),
			request.getRequestURI());
	}
	
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong.", List.of(),
			request.getRequestURI());
	}
	
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation_failed", "The request has invalid fields.",
			fieldErrors(ex.getBindingResult()), path(request));
	}
	
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ApiProblem.FieldError> errors = ex.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new ApiProblem.FieldError(result.getMethodParameter().getParameterName(), "invalid",
					error.getDefaultMessage())))
			.toList();
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation_failed", "The request has invalid fields.", errors,
			path(request));
	}
	
	/** Every other Spring MVC exception: keep its status and detail, add our code and trace id. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		String detail = body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : null;
		if (statusCode.is5xxServerError()) {
			log.error("Request failed on {}", path(request), ex);
		}
		return problem(statusCode, ProblemCodes.forStatus(statusCode), detail, List.of(), path(request), headers);
	}
	
	private static List<ApiProblem.FieldError> fieldErrors(BindingResult result) {
		return result.getFieldErrors().stream()
			.map(e -> new ApiProblem.FieldError(e.getField(), code(e.getCode()), e.getDefaultMessage()))
			.toList();
	}
	
	/** Bean Validation constraint name to a snake_case code, e.g. NotBlank → not_blank. */
	private static String code(String constraint) {
		if (constraint == null) {
			return "invalid";
		}
		return constraint.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
	}
	
	private static String lastNode(String propertyPath) {
		int dot = propertyPath.lastIndexOf('.');
		return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
	}
	
	private static String path(WebRequest request) {
		if (request instanceof NativeWebRequest nwr && nwr.getNativeRequest() instanceof HttpServletRequest http) {
			return http.getRequestURI();
		}
		return null;
	}
	
	private static ResponseEntity<Object> problem(HttpStatusCode status, String code, String detail,
		List<ApiProblem.FieldError> errors, String instance) {
		return problem(status, code, detail, errors, instance, HttpHeaders.EMPTY);
	}
	
	private static ResponseEntity<Object> problem(HttpStatusCode status, String code, String detail,
		List<ApiProblem.FieldError> errors, String instance, HttpHeaders headers) {
		ApiProblem body = ApiProblem.of(status, code, detail, instance, errors, RequestIdFilter.currentRequestId());
		return ResponseEntity.status(status)
			.headers(headers)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(body);
	}
	
}
