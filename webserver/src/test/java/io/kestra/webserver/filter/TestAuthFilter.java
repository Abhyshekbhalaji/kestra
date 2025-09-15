package io.kestra.webserver.filter;

import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import jakarta.inject.Inject;
import java.util.Base64;
import org.reactivestreams.Publisher;

@Filter("/**")
@Requires(env = Environment.TEST)
public class TestAuthFilter implements HttpClientFilter {
    public static boolean ENABLED = true;

    @Inject
    private BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private BasicAuthService basicAuthService;

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
        ClientFilterChain chain) {
        if (ENABLED) {
            //Basic auth may be removed from the database by jdbcTestUtils.drop(); / jdbcTestUtils.migrate();
            //We need it back to be able to run the tests and avoid NPE while checking the basic authorization
            if (basicAuthService.configuration() == null) {
                basicAuthService.save(basicAuthConfiguration);
            }
            //Add basic authorization header if no header are present in the query
            if (request.getHeaders().getAuthorization().isEmpty()) {
                String token = "Basic " + Base64.getEncoder().encodeToString(
                    (basicAuthConfiguration.getUsername() + ":" + basicAuthConfiguration.getPassword()).getBytes());
                request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
            }
        }
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() - 1;
    }
}
