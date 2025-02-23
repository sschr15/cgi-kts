package com.sschr15.scripting.api

public sealed class HttpStatusCode(public val number: Int, public val message: String) {
    public class Success internal constructor(code: Int, message: String) : HttpStatusCode(code, message) {
        public companion object {
            public val Ok: Success = Success(200, "OK")
            public val Created: Success = Success(201, "Created")
            public val Accepted: Success = Success(202, "Accepted")
            public val NonAuthoritativeInformation: Success = Success(203, "Non-Authoritative Information")
            public val NoContent: Success = Success(204, "No Content")
            public val ResetContent: Success = Success(205, "Reset Content")
            public val PartialContent: Success = Success(206, "Partial Content")
            public val MultiStatus: Success = Success(207, "Multi-Status")
            public val AlreadyReported: Success = Success(208, "Already Reported")
            public val IMUsed: Success = Success(226, "IM Used")
        }
    }

    public class Redirect internal constructor(code: Int, message: String) : HttpStatusCode(code, message) {
        public companion object {
            public val MultipleChoices: Redirect = Redirect(300, "Multiple Choices")
            public val MovedPermanently: Redirect = Redirect(301, "Moved Permanently")
            public val Found: Redirect = Redirect(302, "Found")
            public val SeeOther: Redirect = Redirect(303, "See Other")
            public val NotModified: Redirect = Redirect(304, "Not Modified")
            public val UseProxy: Redirect = Redirect(305, "Use Proxy")
            // 306 Switch Proxy is no longer used
            public val TemporaryRedirect: Redirect = Redirect(307, "Temporary Redirect")
            public val PermanentRedirect: Redirect = Redirect(308, "Permanent Redirect")
        }
    }

    public class ClientError internal constructor(code: Int, message: String) : HttpStatusCode(code, message) {
        public companion object {
            public val BadRequest: ClientError = ClientError(400, "Bad Request")
            public val Unauthorized: ClientError = ClientError(401, "Unauthorized")
            public val PaymentRequired: ClientError = ClientError(402, "Payment Required")
            public val Forbidden: ClientError = ClientError(403, "Forbidden")
            public val NotFound: ClientError = ClientError(404, "Not Found")
            public val MethodNotAllowed: ClientError = ClientError(405, "Method Not Allowed")
            public val NotAcceptable: ClientError = ClientError(406, "Not Acceptable")
            public val ProxyAuthenticationRequired: ClientError = ClientError(407, "Proxy Authentication Required")
            public val RequestTimeout: ClientError = ClientError(408, "Request Timeout")
            public val Conflict: ClientError = ClientError(409, "Conflict")
            public val Gone: ClientError = ClientError(410, "Gone")
            public val LengthRequired: ClientError = ClientError(411, "Length Required")
            public val PreconditionFailed: ClientError = ClientError(412, "Precondition Failed")
            public val PayloadTooLarge: ClientError = ClientError(413, "Payload Too Large")
            public val URITooLong: ClientError = ClientError(414, "URI Too Long")
            public val UnsupportedMediaType: ClientError = ClientError(415, "Unsupported Media Type")
            public val RangeNotSatisfiable: ClientError = ClientError(416, "Range Not Satisfiable")
            public val ExpectationFailed: ClientError = ClientError(417, "Expectation Failed")
            public val ImATeapot: ClientError = ClientError(418, "I'm a teapot")
            public val MisdirectedRequest: ClientError = ClientError(421, "Misdirected Request")
            public val UnprocessableContent: ClientError = ClientError(422, "Unprocessable Content")
            public val Locked: ClientError = ClientError(423, "Locked")
            public val FailedDependency: ClientError = ClientError(424, "Failed Dependency")
            public val TooEarly: ClientError = ClientError(425, "Too Early")
            public val UpgradeRequired: ClientError = ClientError(426, "Upgrade Required")
            public val PreconditionRequired: ClientError = ClientError(428, "Precondition Required")
            public val TooManyRequests: ClientError = ClientError(429, "Too Many Requests")
            public val RequestHeaderFieldsTooLarge: ClientError = ClientError(431, "Request Header Fields Too Large")
            public val UnavailableForLegalReasons: ClientError = ClientError(451, "Unavailable For Legal Reasons")
        }
    }

    public class ServerError internal constructor(code: Int, message: String) : HttpStatusCode(code, message) {
        public companion object {
            public val InternalServerError: ServerError = ServerError(500, "Internal Server Error")
            public val NotImplemented: ServerError = ServerError(501, "Not Implemented")
            public val BadGateway: ServerError = ServerError(502, "Bad Gateway")
            public val ServiceUnavailable: ServerError = ServerError(503, "Service Unavailable")
            public val GatewayTimeout: ServerError = ServerError(504, "Gateway Timeout")
            public val HTTPVersionNotSupported: ServerError = ServerError(505, "HTTP Version Not Supported")
            public val VariantAlsoNegotiates: ServerError = ServerError(506, "Variant Also Negotiates")
            public val InsufficientStorage: ServerError = ServerError(507, "Insufficient Storage")
            public val LoopDetected: ServerError = ServerError(508, "Loop Detected")
            public val NotExtended: ServerError = ServerError(510, "Not Extended")
            public val NetworkAuthenticationRequired: ServerError = ServerError(511, "Network Authentication Required")
        }
    }

    public class Unknown(code: Int, message: String) : HttpStatusCode(code, message)

    override fun toString(): String {
        return "$number $message"
    }
}

public fun HttpStatusCode(code: Int): HttpStatusCode = when (code) {
    200 -> HttpStatusCode.Success.Ok
    201 -> HttpStatusCode.Success.Created
    202 -> HttpStatusCode.Success.Accepted
    203 -> HttpStatusCode.Success.NonAuthoritativeInformation
    204 -> HttpStatusCode.Success.NoContent
    205 -> HttpStatusCode.Success.ResetContent
    206 -> HttpStatusCode.Success.PartialContent
    207 -> HttpStatusCode.Success.MultiStatus
    208 -> HttpStatusCode.Success.AlreadyReported
    226 -> HttpStatusCode.Success.IMUsed
    300 -> HttpStatusCode.Redirect.MultipleChoices
    301 -> HttpStatusCode.Redirect.MovedPermanently
    302 -> HttpStatusCode.Redirect.Found
    303 -> HttpStatusCode.Redirect.SeeOther
    304 -> HttpStatusCode.Redirect.NotModified
    305 -> HttpStatusCode.Redirect.UseProxy
    307 -> HttpStatusCode.Redirect.TemporaryRedirect
    308 -> HttpStatusCode.Redirect.PermanentRedirect
    400 -> HttpStatusCode.ClientError.BadRequest
    401 -> HttpStatusCode.ClientError.Unauthorized
    402 -> HttpStatusCode.ClientError.PaymentRequired
    403 -> HttpStatusCode.ClientError.Forbidden
    404 -> HttpStatusCode.ClientError.NotFound
    405 -> HttpStatusCode.ClientError.MethodNotAllowed
    406 -> HttpStatusCode.ClientError.NotAcceptable
    407 -> HttpStatusCode.ClientError.ProxyAuthenticationRequired
    408 -> HttpStatusCode.ClientError.RequestTimeout
    409 -> HttpStatusCode.ClientError.Conflict
    410 -> HttpStatusCode.ClientError.Gone
    411 -> HttpStatusCode.ClientError.LengthRequired
    412 -> HttpStatusCode.ClientError.PreconditionFailed
    413 -> HttpStatusCode.ClientError.PayloadTooLarge
    414 -> HttpStatusCode.ClientError.URITooLong
    415 -> HttpStatusCode.ClientError.UnsupportedMediaType
    416 -> HttpStatusCode.ClientError.RangeNotSatisfiable
    417 -> HttpStatusCode.ClientError.ExpectationFailed
    418 -> HttpStatusCode.ClientError.ImATeapot
    421 -> HttpStatusCode.ClientError.MisdirectedRequest
    422 -> HttpStatusCode.ClientError.UnprocessableContent
    423 -> HttpStatusCode.ClientError.Locked
    424 -> HttpStatusCode.ClientError.FailedDependency
    425 -> HttpStatusCode.ClientError.TooEarly
    426 -> HttpStatusCode.ClientError.UpgradeRequired
    428 -> HttpStatusCode.ClientError.PreconditionRequired
    429 -> HttpStatusCode.ClientError.TooManyRequests
    431 -> HttpStatusCode.ClientError.RequestHeaderFieldsTooLarge
    451 -> HttpStatusCode.ClientError.UnavailableForLegalReasons
    500 -> HttpStatusCode.ServerError.InternalServerError
    501 -> HttpStatusCode.ServerError.NotImplemented
    502 -> HttpStatusCode.ServerError.BadGateway
    503 -> HttpStatusCode.ServerError.ServiceUnavailable
    504 -> HttpStatusCode.ServerError.GatewayTimeout
    505 -> HttpStatusCode.ServerError.HTTPVersionNotSupported
    506 -> HttpStatusCode.ServerError.VariantAlsoNegotiates
    507 -> HttpStatusCode.ServerError.InsufficientStorage
    508 -> HttpStatusCode.ServerError.LoopDetected
    510 -> HttpStatusCode.ServerError.NotExtended
    511 -> HttpStatusCode.ServerError.NetworkAuthenticationRequired

    in 100..199 -> HttpStatusCode.Unknown(code, "Informational")
    in 200..299 -> HttpStatusCode.Success(code, "Other Success")
    in 300..399 -> HttpStatusCode.Redirect(code, "Other Redirect")
    in 400..499 -> HttpStatusCode.ClientError(code, "Other Client Error")
    in 500..599 -> HttpStatusCode.ServerError(code, "Other Server Error")
    else -> HttpStatusCode.Unknown(code, "Unknown")
}
