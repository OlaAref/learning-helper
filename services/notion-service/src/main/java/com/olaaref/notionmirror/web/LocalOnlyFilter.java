package com.olaaref.notionmirror.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects requests whose Host or Origin is not localhost (DNS-rebinding / cross-site protection). */
@Component
public class LocalOnlyFilter extends OncePerRequestFilter {
    private static final Set<String> OK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!hostOk(req.getHeader("Host")) || !originOk(req.getHeader("Origin"))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Local access only");
            return;
        }
        chain.doFilter(req, res);
    }

    private static boolean hostOk(String host) {
        if (host == null) return false;
        int i = host.lastIndexOf(':');
        String name = (i > 0 && !host.endsWith("]")) ? host.substring(0, i) : host;
        return OK.contains(name);
    }

    private static boolean originOk(String origin) {
        if (origin == null) return true;
        try {
            String h = URI.create(origin).getHost();
            return h != null && OK.contains(h);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
