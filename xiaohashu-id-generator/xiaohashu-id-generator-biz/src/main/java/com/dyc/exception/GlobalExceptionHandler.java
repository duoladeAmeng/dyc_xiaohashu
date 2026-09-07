package com.dyc.exception;

import com.dyc.framework.common.exception.BizException;
import com.dyc.framework.common.response.Response;
import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PARAM_NOT_VALID = "ID-GENERATOR-10001";
    private static final String GENERATOR_UNAVAILABLE = "ID-GENERATOR-20001";
    private static final String SYSTEM_ERROR = "ID-GENERATOR-99999";

    @ExceptionHandler({BizException.class})
    @ResponseBody
    public Response<Object> handleBizException(HttpServletRequest request, BizException e) {
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", request.getRequestURI(), e.getErrorCode(), e.getErrorMessage());
        return Response.fail(e);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseBody
    public Response<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors ->
                errors.forEach(error -> sb.append(error.getField())
                        .append(" ")
                        .append(error.getDefaultMessage())
                        .append(", 当前值: '")
                        .append(error.getRejectedValue())
                        .append("'; ")));
        log.warn("{} request parameter error: {}", request.getRequestURI(), sb);
        return Response.fail(PARAM_NOT_VALID, sb.toString());
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseBody
    public Response<Object> handleIllegalArgumentException(HttpServletRequest request, IllegalArgumentException e) {
        log.warn("{} request parameter error: {}", request.getRequestURI(), e.getMessage());
        return Response.fail(PARAM_NOT_VALID, e.getMessage());
    }

    @ExceptionHandler({IdGeneratorException.class})
    @ResponseBody
    public Response<Object> handleIdGeneratorException(HttpServletRequest request, IdGeneratorException e) {
        log.warn("{} id generator unavailable: {}", request.getRequestURI(), e.getMessage(), e);
        return Response.fail(GENERATOR_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler({Exception.class})
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error.", request.getRequestURI(), e);
        return Response.fail(SYSTEM_ERROR, "系统异常");
    }
}
