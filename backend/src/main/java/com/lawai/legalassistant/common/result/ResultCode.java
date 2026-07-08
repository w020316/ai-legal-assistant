package com.lawai.legalassistant.common.result;

/**
 * 业务错误码常量
 * 命名规则：1xxx 客户端错误，2xxx 服务端/AI 错误，9xxx 未知
 */
public final class ResultCode {

    public static final int SUCCESS = 0;

    // 客户端错误
    public static final int PARAM_ERROR = 1001;
    public static final int UNAUTHORIZED = 1002;
    public static final int FORBIDDEN = 1003;
    public static final int NOT_FOUND = 1004;
    public static final int CONFLICT = 1005;
    public static final int TOO_MANY_REQUESTS = 1006;

    // 服务端 / AI 错误
    public static final int AI_SERVICE_ERROR = 2001;
    public static final int RAG_RETRIEVE_ERROR = 2002;
    public static final int FILE_TOO_LARGE = 2003;

    // 未知
    public static final int UNKNOWN = 9999;

    private ResultCode() {
    }
}
