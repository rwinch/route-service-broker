package org.springframework.cloud.sample.routeservice.config;

import org.springframework.core.io.buffer.DataBuffer;
        import org.springframework.core.io.buffer.DataBufferFactory;
        import org.springframework.http.HttpMethod;
        import org.springframework.http.HttpStatus;
        import org.springframework.http.MediaType;
        import org.springframework.http.server.reactive.ServerHttpResponse;
        import org.springframework.security.web.server.csrf.CsrfToken;
        import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
        import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
        import org.springframework.util.MultiValueMap;
        import org.springframework.web.server.ServerWebExchange;
        import org.springframework.web.server.WebFilter;
        import org.springframework.web.server.WebFilterChain;
        import reactor.core.publisher.Mono;

        import java.nio.charset.Charset;

import static org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory.X_CF_FORWARDED_URL;

/**
 * Generates a default log in page used for authenticating users.
 *
 * @author Rob Winch
 * @since 5.0
 */
public class TestClass implements WebFilter {
    private ServerWebExchangeMatcher matcher = exchange -> {

        String forwardedUrl = exchange.getRequest().getHeaders().getFirst(X_CF_FORWARDED_URL);
        if (forwardedUrl != null && forwardedUrl.endsWith("/login")) {
            return ServerWebExchangeMatcher.MatchResult.match();
        }
        return ServerWebExchangeMatcher.MatchResult.notMatch();
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return this.matcher.matches(exchange)
                .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
                .switchIfEmpty(chain.filter(exchange).then(Mono.empty()))
                .flatMap(matchResult -> render(exchange));
    }

    private Mono<Void> render(ServerWebExchange exchange) {
        ServerHttpResponse result = exchange.getResponse();
        result.setStatusCode(HttpStatus.OK);
        result.getHeaders().setContentType(MediaType.TEXT_HTML);
        return result.writeWith(createBuffer(exchange));
    }

    private Mono<DataBuffer> createBuffer(ServerWebExchange exchange) {
        MultiValueMap<String, String> queryParams = exchange.getRequest()
                .getQueryParams();
        Mono<CsrfToken> token = exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
        return token
                .map(TestClass::csrfToken)
                .defaultIfEmpty("")
                .map(csrfTokenHtmlInput -> {
                    boolean isError = queryParams.containsKey("error");
                    boolean isLogoutSuccess = queryParams.containsKey("logout");
                    byte[] bytes = createPage(isError, isLogoutSuccess, csrfTokenHtmlInput);
                    DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                    return bufferFactory.wrap(bytes);
                });
    }

    private static byte[] createPage(boolean isError, boolean isLogoutSuccess, String csrfTokenHtmlInput) {
        String page =  "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "  <head>\n"
                + "    <meta charset=\"utf-8\">\n"
                + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, shrink-to-fit=no\">\n"
                + "    <meta name=\"description\" content=\"\">\n"
                + "    <meta name=\"author\" content=\"\">\n"
                + "    <title>Please sign in</title>\n"
                + "    <link href=\"https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta/css/bootstrap.min.css\" rel=\"stylesheet\" integrity=\"sha384-/Y6pD6FV/Vv2HJnA6t+vslU6fwYXjCFtcEpHbNJ0lyAFsXTsjBbfaDjzALeQsN6M\" crossorigin=\"anonymous\">\n"
                + "    <link href=\"http://getbootstrap.com/docs/4.0/examples/signin/signin.css\" rel=\"stylesheet\" crossorigin=\"anonymous\"/>\n"
                + "  </head>\n"
                + "  <body>\n"
                + "     <div class=\"container\">\n"
                + "      <form class=\"form-signin\" method=\"post\" action=\"/login\">\n"
                + "        <h2 class=\"form-signin-heading\">Please sign in</h2>\n"
                + createError(isError)
                + createLogoutSuccess(isLogoutSuccess)
                + "        <p>\n"
                + "          <label for=\"username\" class=\"sr-only\">Username</label>\n"
                + "          <input type=\"text\" id=\"username\" name=\"username\" class=\"form-control\" placeholder=\"Username\" required autofocus>\n"
                + "        </p>\n"
                + "        <p>\n"
                + "          <label for=\"password\" class=\"sr-only\">Password</label>\n"
                + "          <input type=\"password\" id=\"password\" name=\"password\" class=\"form-control\" placeholder=\"Password\" required>\n"
                + "        </p>\n"
                + csrfTokenHtmlInput
                + "        <button class=\"btn btn-lg btn-primary btn-block\" type=\"submit\">Sign in</button>\n"
                + "      </form>\n"
                + "    </div>\n"
                + "  </body>\n"
                + "</html>";

        return page.getBytes(Charset.defaultCharset());
    }

    private static String csrfToken(CsrfToken token) {
        return "          <input type=\"hidden\" name=\"" + token.getParameterName() + "\" value=\"" + token.getToken() + "\">\n";
    }

    private static String createError(boolean isError) {
        return isError ? "<div class=\"alert alert-danger\" role=\"alert\">Invalid credentials</div>" : "";
    }

    private static String createLogoutSuccess(boolean isLogoutSuccess) {
        return isLogoutSuccess ? "<div class=\"alert alert-success\" role=\"alert\">You have been signed out</div>" : "";
    }
}

