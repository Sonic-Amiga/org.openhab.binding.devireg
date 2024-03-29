package org.openhab.binding.danfoss.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DanfossDiscoveryServlet extends HttpServlet {

    private static final long serialVersionUID = 4277870755120852304L;
    private final Logger logger = LoggerFactory.getLogger(DanfossDiscoveryServlet.class);
    private static final String RESOURCE_URL = "/" + DanfossBindingConstants.BINDING_ID;

    private HttpService httpService;

    public DanfossDiscoveryServlet(HttpService svc) {
        httpService = svc;
        try {
            httpService.registerServlet(RESOURCE_URL, this, null, httpService.createDefaultHttpContext());
        } catch (NamespaceException | ServletException e) {
            logger.error("Register servlet fails", e);
        }
    }

    public void dispose() {
        httpService.unregister(RESOURCE_URL);
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {

        if (req == null || resp == null) {
            return;
        }

        String uri = req.getRequestURI();

        if (uri == null) {
            // This doesn't make any sense but Eclipse simply forced me to write this
            logger.debug("GET: null URI");
            resp.sendRedirect(RESOURCE_URL + "/");
            return;
        }

        String filePath = uri.substring(RESOURCE_URL.length());
        logger.trace("GET {}", filePath);

        if (filePath.isEmpty()) {
            resp.sendRedirect(RESOURCE_URL + "/");
            return;
        } else if (filePath.equals("/")) {
            filePath = "/configreceiver.html";
        }

        InputStream in = getClass().getResourceAsStream("/ui" + filePath);

        if (in != null) {
            String type;

            // guessContentTypeFromName() doesn't know .js
            if (filePath.endsWith(".js")) {
                type = "application/javascript";
            } else {
                type = URLConnection.guessContentTypeFromName(filePath);
            }

            logger.trace("content-type {}", type);

            resp.addHeader("content-type", type);
            in.transferTo(resp.getOutputStream());
        } else {
            resp.sendError(404, "Not found");
        }
    }
}
