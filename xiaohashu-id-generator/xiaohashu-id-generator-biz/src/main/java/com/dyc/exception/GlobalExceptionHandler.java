package com.dyc.exception;

import com.dyc.framework.common.response.Response;
import com.dyc.enums.ResponseCodeEnum;
import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;
import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获参数校验异常。
     *
     * @return 失败响应
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class })
    @ResponseBody
    public Response<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors -> errors.forEach(error ->
                sb.append(error.getField())
                        .append(" ")
                        .append(error.getDefaultMessage())
                        .append(", 当前值: '")
                        .append(error.getRejectedValue())
                        .append("'; ")
        ));
        String errorMessage = sb.toString();
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);
        return Response.fail(errorCode, errorMessage);
    }

    /**
     * 捕获请求体解析异常。
     *
     * @return 失败响应
     */
    @ExceptionHandler({ HttpMessageNotReadableException.class })
    @ResponseBody
    public Response<Object> handleHttpMessageNotReadableException(HttpServletRequest request, HttpMessageNotReadableException e) {
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(),
                ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), e.getMessage());
        return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID);
    }

    /**
     * 捕获参数异常。
     *
     * @return 失败响应
     */
    @ExceptionHandler({ IllegalArgumentException.class, NullPointerException.class })
    @ResponseBody
    public Response<Object> handleIllegalArgumentException(HttpServletRequest request, RuntimeException e) {
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(),
                ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), e.getMessage());
        return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), e.getMessage());
    }

    /**
     * 捕获发号器不可用异常。
     *
     * @return 失败响应
     */
    @ExceptionHandler({ GeneratorUnavailableException.class })
    @ResponseBody
    public Response<Object> handleGeneratorUnavailableException(HttpServletRequest request, GeneratorUnavailableException e) {
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(),
                ResponseCodeEnum.GENERATOR_UNAVAILABLE.getErrorCode(), e.getMessage());
        return Response.fail(ResponseCodeEnum.GENERATOR_UNAVAILABLE.getErrorCode(), e.getMessage());
    }

    /**
     * 捕获发号异常。
     *
     * @return 失败响应
     */
    @ExceptionHandler({ IdGeneratorException.class })
    @ResponseBody
    public Response<Object> handleIdGeneratorException(HttpServletRequest request, IdGeneratorException e) {
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(),
                ResponseCodeEnum.GENERATE_ID_FAIL.getErrorCode(), e.getMessage());
        return Response.fail(ResponseCodeEnum.GENERATE_ID_FAIL.getErrorCode(), e.getMessage());
    }

    /**
     * 捕获其他异常。
     *
     * @return 失败响应
     */
    @ExceptionHandler({ Exception.class })
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
    }
}
