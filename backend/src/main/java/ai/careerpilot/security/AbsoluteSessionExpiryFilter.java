package ai.careerpilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AbsoluteSessionExpiryFilter extends OncePerRequestFilter {
    private final long absoluteLifetimeMillis;

    public AbsoluteSessionExpiryFilter(@Value("${app.session.absolute-timeout-ms:43200000}") long absoluteLifetimeMillis) {
        this.absoluteLifetimeMillis = absoluteLifetimeMillis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object authenticatedAt = session.getAttribute("AUTHENTICATED_AT");
            if (authenticatedAt instanceof Long createdAt
                    && System.currentTimeMillis() - createdAt >= absoluteLifetimeMillis) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
